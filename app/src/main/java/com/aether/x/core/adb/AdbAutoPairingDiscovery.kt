package com.aether.x.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AdbAutoPairingDiscovery {

    private const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp"
    private const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp"

    data class DiscoveredService(val host: String, val port: Int)

    suspend fun discoverPairingService(
        context: Context,
        timeoutMs: Long = 120_000,
    ): DiscoveredService = discoverService(context, SERVICE_TYPE_PAIRING, timeoutMs)

    suspend fun discoverConnectService(
        context: Context,
        timeoutMs: Long = 15_000,
    ): DiscoveredService = discoverService(context, SERVICE_TYPE_CONNECT, timeoutMs)

    private suspend fun discoverService(
        context: Context,
        serviceType: String,
        timeoutMs: Long,
    ): DiscoveredService {
        val appContext = context.applicationContext
        val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("aetherx-adb-discovery")?.apply {
            setReferenceCounted(true)
        }

        return try {
            multicastLock?.acquire()
            withTimeout(timeoutMs) {
                resolveFirstMatch(nsdManager, serviceType)
            }
        } finally {
            runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        }
    }

    /**
     * FIX BUG: gagal pairing di Android 13 ("Searching for Pairing…"
     * macet sampai timeout padahal dialog pairing Android sudah terbuka
     * dan wireless debugging aktif).
     *
     * Akar masalah SEBELUMNYA: [NsdManager.ResolveListener.onResolveFailed]
     * dibiarkan kosong total (tidak melakukan apa pun). Di Android 13,
     * `NsdManager.resolveService` jauh lebih sering gagal di percobaan
     * pertama dibanding Android 11/12 — errorCode transient seperti
     * `FAILURE_ALREADY_ACTIVE` (isu yang cukup dikenal di NsdManager sejak
     * Android 12+, resolve sebelumnya untuk service lain kadang masih
     * dianggap "aktif" oleh sistem walau sebenarnya sudah
     * selesai/timeout) sangat umum terjadi. Tanpa retry, satu-satunya
     * jalan keluar sebelumnya adalah menunggu event
     * [NsdManager.DiscoveryListener.onServiceFound] BARU untuk instance
     * service yang sama — yang seringkali TIDAK PERNAH dikirim ulang oleh
     * sistem dalam sesi discovery yang sama, sehingga seluruh proses
     * pairing macet diam sampai timeout total (120 detik) habis, walau
     * service pairing-nya sendiri sebenarnya sudah "ditemukan" sejak awal.
     *
     * Sekarang: [onResolveFailed] retry resolve untuk service YANG SAMA
     * beberapa kali dengan jeda singkat (menangani errorCode transient di
     * atas), dan kalau tetap gagal setelah itu, biarkan discovery lanjut
     * mendengarkan (TIDAK menggagalkan seluruh continuation) supaya masih
     * ada kesempatan kandidat lain sebelum timeout total di
     * [discoverService] tercapai.
     */
    private suspend fun resolveFirstMatch(
        nsdManager: NsdManager,
        serviceType: String,
    ): DiscoveredService = suspendCancellableCoroutine { continuation ->

        val resolvedOnce = CompletableDeferred<Unit>()
        val maxResolveRetriesPerService = 3
        val resolveRetryDelayMs = 300L
        val resolveAttempts = mutableMapOf<String, Int>()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        lateinit var discoveryListener: NsdManager.DiscoveryListener
        lateinit var resolveListener: NsdManager.ResolveListener

        fun safeStop() {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }

        fun serviceKey(serviceInfo: NsdServiceInfo): String =
            "${serviceInfo.serviceName}|${serviceInfo.serviceType}"

        fun tryResolve(serviceInfo: NsdServiceInfo) {
            if (resolvedOnce.isCompleted) return
            runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
        }

        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                if (resolvedOnce.isCompleted || serviceInfo == null) return

                val key = serviceKey(serviceInfo)
                val attempts = (resolveAttempts[key] ?: 0) + 1
                resolveAttempts[key] = attempts

                if (attempts >= maxResolveRetriesPerService) {
                    // Sudah dicoba beberapa kali khusus untuk service ini
                    // dan tetap gagal — JANGAN gagalkan seluruh discovery
                    // (errorCode transient di banyak ROM Android 13 tidak
                    // selalu berarti service benar-benar tidak valid),
                    // cukup berhenti mencoba service ini dan biarkan
                    // listener menunggu kandidat lain sampai timeout total
                    // di [discoverService] tercapai.
                    return
                }

                // Retry dengan jeda singkat — errorCode transient seperti
                // FAILURE_ALREADY_ACTIVE di Android 13 biasanya hilang
                // sendiri sesaat kemudian.
                mainHandler.postDelayed({ tryResolve(serviceInfo) }, resolveRetryDelayMs)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (resolvedOnce.isCompleted) return
                val host = serviceInfo.host?.hostAddress
                val port = serviceInfo.port
                if (host.isNullOrBlank() || port <= 0) return
                resolvedOnce.complete(Unit)
                safeStop()
                if (continuation.isActive) {
                    continuation.resume(DiscoveredService(host = host, port = port))
                }
            }
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                tryResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("NSD startDiscoveryFailed($serviceType, errorCode=$errorCode)"),
                    )
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        continuation.invokeOnCancellation {
            mainHandler.removeCallbacksAndMessages(null)
            safeStop()
        }

        runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
