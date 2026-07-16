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

/**
 * FITUR BARU — Auto-Pairing (lihat perintah rework: "jadikan sistem
 * pairing AetherX seperti [referensi] ... tinggal klik Start lalu ada
 * notifikasi mengambang Searching for Pairing ... dan tidak perlu isi
 * alamat ip dll secara manual").
 *
 * Android Wireless debugging (Android 11+) mem-broadcast dua service mDNS
 * lewat NSD (Network Service Discovery) SETIAP KALI layar "Wireless
 * debugging" dibuka:
 *
 *   - "_adb-tls-pairing._tcp"  → service pairing (dialog "Pasangkan
 *     perangkat dengan kode pairing"), HANYA muncul selama dialog itu
 *     terbuka.
 *   - "_adb-tls-connect._tcp"  → service shell/koneksi biasa, muncul
 *     selama Wireless debugging aktif (tidak perlu dialog kode terbuka).
 *
 * Ini PERSIS mekanisme yang dipakai Shizuku Manager & App Manager untuk
 * auto-detect host+port tanpa pengguna mengetik apa pun secara manual —
 * device Android sendiri yang mem-broadcast alamat IP + port lewat
 * mDNS/Bonjour di jaringan Wi-Fi lokal, [NsdManager] tinggal
 * mendengarkannya.
 *
 * SENGAJA sebuah object stateless berisi dua fungsi suspend independen
 * (bukan menyimpan listener sebagai state kelas) supaya masing-masing
 * pemanggilan discover punya siklus hidup sendiri yang bersih (register →
 * dapat hasil pertama yang valid → langsung unregister), tidak ada
 * listener yang "nyangkut" di background lebih lama dari yang dibutuhkan.
 */
object AdbAutoPairingDiscovery {

    private const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp"
    private const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp"

    data class DiscoveredService(val host: String, val port: Int)

    /**
     * Menunggu service PAIRING (`_adb-tls-pairing._tcp`) muncul di jaringan
     * lokal — ini yang membuat kartu bisa langsung menampilkan "Searching
     * for Pairing…" begitu tombol Start ditekan, lalu otomatis lanjut ke
     * dialog kode 6-digit begitu Android menyalakan dialog pairing-nya
     * sendiri (Opsi Developer > Wireless debugging > "Pasangkan perangkat
     * dengan kode pairing").
     *
     * [timeoutMs] default 120 detik — cukup lama untuk pengguna berpindah
     * ke Pengaturan dan membuka dialog pairing, tapi tidak selamanya
     * (supaya listener NSD tidak menyala tanpa batas kalau pengguna
     * berubah pikiran).
     */
    suspend fun discoverPairingService(
        context: Context,
        timeoutMs: Long = 120_000,
    ): DiscoveredService = discoverService(context, SERVICE_TYPE_PAIRING, timeoutMs)

    /**
     * Menunggu service KONEKSI (`_adb-tls-connect._tcp`) — dipakai saat
     * auto-reconnect (sudah pernah pairing sebelumnya, tinggal butuh
     * host:port terbaru karena port bisa berubah tiap Wireless debugging
     * dinyalakan ulang) TANPA memerlukan dialog kode pairing lagi.
     */
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

        // Multicast lock WAJIB di banyak ROM (terutama MIUI/ColorOS) — tanpa
        // ini paket mDNS masuk sering di-drop oleh Wi-Fi chipset saat layar
        // mati/idle walau NsdManager sendiri tidak melempar error apa pun,
        // membuat discovery "diam-diam" tidak pernah menemukan apa pun.
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

    private suspend fun resolveFirstMatch(
        nsdManager: NsdManager,
        serviceType: String,
    ): DiscoveredService = suspendCancellableCoroutine { continuation ->
        // Beberapa service bisa ditemukan sekaligus (mis. jaringan ramai) —
        // dikunci supaya hasil pertama yang BERHASIL di-resolve yang dipakai,
        // sisanya diabaikan begitu continuation sudah selesai.
        val resolvedOnce = CompletableDeferred<Unit>()

        lateinit var discoveryListener: NsdManager.DiscoveryListener

        fun safeStop() {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                // Gagal resolve satu entri BUKAN kegagalan fatal keseluruhan
                // discovery — service lain yang di-`onServiceFound` masih
                // bisa berhasil di-resolve setelahnya, jadi listener discovery
                // TETAP berjalan (tidak stop di sini).
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
                if (resolvedOnce.isCompleted) return
                runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
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

        continuation.invokeOnCancellation { safeStop() }

        runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
