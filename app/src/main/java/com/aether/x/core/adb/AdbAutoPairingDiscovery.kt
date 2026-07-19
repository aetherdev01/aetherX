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

    private suspend fun resolveFirstMatch(
        nsdManager: NsdManager,
        serviceType: String,
    ): DiscoveredService = suspendCancellableCoroutine { continuation ->

        val resolvedOnce = CompletableDeferred<Unit>()

        lateinit var discoveryListener: NsdManager.DiscoveryListener

        fun safeStop() {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {

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
