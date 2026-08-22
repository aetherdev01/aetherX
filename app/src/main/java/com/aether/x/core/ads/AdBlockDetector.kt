package com.aether.x.core.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.SecretStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdBlockDetector {

    private const val TAG = "AdBlockDetector"

    /**
     * Path listing modul Magisk — disimpan terenkripsi (lihat
     * SecretStrings.kt) supaya tidak muncul plaintext di classes.dex.
     * Payload digenerate lewat tools/encode_secret.py.
     */
    private val MAGISK_MODULES_PATH: String by lazy {
        SecretStrings.reveal("rJIfp2/ULymoDP3v5gdzpSsbYd0hMYi9dNDQlNbjaA+jUNfPnUWBRHjB4lPiew==")
    }

    init {

        System.loadLibrary("aetherX")
    }

    private external fun nativeMatchAdBlockDns(dnsServers: Array<String>): Boolean
    private external fun nativeMatchAdBlockModule(moduleListing: String): Boolean

    data class AdBlockSignals(
        val vpnInterfaceActive: Boolean,
        val knownAdBlockDnsActive: Boolean,
        val knownMagiskModuleActive: Boolean,
    ) {

        val anyDetected: Boolean get() = vpnInterfaceActive || knownAdBlockDnsActive || knownMagiskModuleActive

        val signalKey: String get() = "vpn=$vpnInterfaceActive;dns=$knownAdBlockDnsActive;module=$knownMagiskModuleActive"
    }

    suspend fun detect(context: Context): AdBlockSignals = withContext(Dispatchers.IO) {
        AdBlockSignals(
            vpnInterfaceActive = detectVpnInterface(context),
            knownAdBlockDnsActive = detectAdBlockDns(context),
            knownMagiskModuleActive = detectMagiskModule(),
        )
    }

    private fun detectVpnInterface(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val capabilities = cm.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)

    private fun detectAdBlockDns(context: Context): Boolean {
        val dnsServers = readConfiguredDnsServers(context)
        if (dnsServers.isEmpty()) return false
        return runCatching { nativeMatchAdBlockDns(dnsServers.toTypedArray()) }.getOrDefault(false)
    }

    private fun readConfiguredDnsServers(context: Context): List<String> = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val linkProperties = cm.getLinkProperties(network) ?: return emptyList()

        val resolverIps = linkProperties.dnsServers.mapNotNull { it.hostAddress }

        val privateDnsHostname = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            linkProperties.privateDnsServerName
        } else {
            null
        }

        resolverIps + listOfNotNull(privateDnsHostname)
    }.getOrDefault(emptyList())

    private suspend fun detectMagiskModule(): Boolean {
        if (!PrivilegeManager.status.value.rootGranted) {
            return false
        }
        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return false

        val result = runCatching {
            executor.exec("ls $MAGISK_MODULES_PATH 2>/dev/null")
        }.getOrElse { t ->
            Log.w(TAG, "Gagal membaca listing modul Magisk: ${t.message}")
            return false
        }

        if (!result.success || result.output.isEmpty()) return false

        return runCatching { nativeMatchAdBlockModule(result.outputText) }.getOrDefault(false)
    }
}
