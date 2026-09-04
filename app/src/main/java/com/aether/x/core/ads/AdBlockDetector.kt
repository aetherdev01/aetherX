package com.aether.x.core.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.SecretStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * v3.5 — DETEKSI DIPERKETAT setelah riset ulang teknik anti-adblock (lihat
 * ringkasan per-sinyal di KDoc masing-masing fungsi `detectXxx` di bawah).
 * Kelemahan utama versi SEBELUM ini: `knownMagiskModuleActive` cuma
 * mencocokkan NAMA folder module Magisk terhadap keyword ("adaway",
 * "systemless-hosts", dst) — trivial di-bypass dengan rename folder.
 * `vpnInterfaceActive` juga terlalu kasar (flag SEMUA VPN, termasuk VPN
 * pribadi/kantor yang sama sekali tidak nge-block iklan) tapi TIDAK
 * menangkap adblock VPN yang melakukan filtering murni di dalam tunnel-nya
 * sendiri tanpa menyentuh setting Private DNS Android sama sekali (AdGuard,
 * Blokada, DNS66, RethinkDNS semuanya bisa begini).
 *
 * Tiga sinyal BARU ditambahkan untuk menutup celah itu:
 * - [detectDnsSinkhole]: bait resolution ke domain iklan ASLI (bukan cuma
 *   provider DNS yang dikenal) — mekanisme-agnostik, nangkep DNS block,
 *   hosts file, ATAU in-VPN filtering sekaligus dalam SATU pengecekan,
 *   karena yang diuji adalah HASIL AKHIRNYA (domain di-sinkhole atau
 *   tidak), bukan mekanisme spesifiknya. Ini teknik yang sama dipakai
 *   package `adblock_detector` (Flutter, cross-platform) dan varian
 *   "FetchDoubleClick"/"BaitElementOrXhr" di banyak adblock-detector JS —
 *   prinsipnya portable ke native karena tidak bergantung DOM.
 * - [detectKnownAdBlockApp]: cek package terinstal terhadap daftar app
 *   adblock/DNS-filter populer (AdGuard, NetGuard, DNS66, RethinkDNS) lewat
 *   PackageManager — otomatis bisa lihat package apapun tanpa perlu
 *   <queries> tambahan karena app ini SUDAH punya QUERY_ALL_PACKAGES
 *   (lihat AndroidManifest.xml, awalnya untuk GameProfileCatalog) —
 *   menangkap app yang di-install tapi belum tentu aktif
 *   filtering saat dicek, JADI INI SINYAL PALING LEMAH dari semuanya
 *   (banyak false positive: user install AdGuard lalu nonaktifkan, atau
 *   pakai untuk fitur lain selain adblock) — SENGAJA tidak dipakai
 *   sendirian, cuma nambah ke `anyDetected` bersama sinyal lain.
 * - [detectHostsFileSinkhole]: baca ISI /system/etc/hosts mentah (bukan
 *   nama folder module) dan cari domain iklan yang di-sinkhole — lihat
 *   KDoc `hostsContentSinkholesKnownAdDomain` di adblockguard.cpp untuk
 *   alasan detail kenapa ini jauh lebih tahan evasion dibanding
 *   containsKnownAdBlockModuleKeyword yang lama.
 */
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

    /**
     * Package adblock/DNS-filter populer di Android (VPN-based ATAU
     * root/iptables-based — lihat referensi arsitektur di
     * github.com/pass-with-high-score/blockads-android yang eksplisit
     * mendukung dua mode itu). Daftar ini HANYA sinyal pendukung, bukan
     * bukti definitif filtering aktif (lihat KDoc class di atas) — dan
     * PERLU diverifikasi/update berkala karena app distribusi non-Play
     * Store (F-Droid/GitHub, cth. Blokada) kadang ganti applicationId
     * antar versi major. Deteksi ini jalan langsung tanpa perlu entri
     * <queries> tambahan di manifest karena app ini SUDAH punya
     * QUERY_ALL_PACKAGES (lihat AndroidManifest.xml — awalnya untuk
     * GameProfileCatalog, tapi otomatis berlaku di sini juga).
     */
    private val KNOWN_ADBLOCK_APP_PACKAGES = listOf(
        "com.adguard.android",
        "com.adguard.android.pro",
        "org.jak_linux.dns66",
        "eu.faircode.netguard",
        "com.celzero.bravedns",
        "org.blokada",
    )

    /** Domain iklan asli buat bait resolution — lihat kAdBlockBaitDomainsEncoded di adblockguard.cpp (sumber sama, disinkronkan manual). */
    private val AD_BAIT_HOSTNAMES = listOf(
        "unityads.unity3d.com",
        "configv2.unityads.unity3d.com",
        "doubleclick.net",
        "googlesyndication.com",
    )

    init {

        System.loadLibrary("aetherX")
    }

    private external fun nativeMatchAdBlockDns(dnsServers: Array<String>): Boolean
    private external fun nativeMatchAdBlockModule(moduleListing: String): Boolean
    private external fun nativeMatchAdBlockHosts(hostsContent: String): Boolean

    data class AdBlockSignals(
        val vpnInterfaceActive: Boolean,
        val knownAdBlockDnsActive: Boolean,
        val knownMagiskModuleActive: Boolean,
        val dnsSinkholeDetected: Boolean = false,
        val knownAdBlockAppInstalled: Boolean = false,
        val hostsSinkholeDetected: Boolean = false,
    ) {

        val anyDetected: Boolean
            get() = vpnInterfaceActive || knownAdBlockDnsActive || knownMagiskModuleActive ||
                dnsSinkholeDetected || knownAdBlockAppInstalled || hostsSinkholeDetected

        /** Semua sinyal masuk key supaya perubahan APA PUN (termasuk sinyal
         * baru yang baru aktif) menghasilkan key baru yang belum pernah
         * di-acknowledge, dan dialog akan muncul lagi — bukan cuma
         * kombinasi vpn/dns/module seperti versi sebelumnya. */
        val signalKey: String
            get() = "vpn=$vpnInterfaceActive;dns=$knownAdBlockDnsActive;module=$knownMagiskModuleActive;" +
                "sinkhole=$dnsSinkholeDetected;app=$knownAdBlockAppInstalled;hosts=$hostsSinkholeDetected"
    }

    suspend fun detect(context: Context): AdBlockSignals = withContext(Dispatchers.IO) {
        val vpnDeferred = async { detectVpnInterface(context) }
        val dnsDeferred = async { detectAdBlockDns(context) }
        val moduleDeferred = async { detectMagiskModule() }
        val sinkholeDeferred = async { detectDnsSinkhole() }
        val appDeferred = async { detectKnownAdBlockApp(context) }
        val hostsDeferred = async { detectHostsFileSinkhole() }

        awaitAll(vpnDeferred, dnsDeferred, moduleDeferred, sinkholeDeferred, appDeferred, hostsDeferred)

        AdBlockSignals(
            vpnInterfaceActive = vpnDeferred.await(),
            knownAdBlockDnsActive = dnsDeferred.await(),
            knownMagiskModuleActive = moduleDeferred.await(),
            dnsSinkholeDetected = sinkholeDeferred.await(),
            knownAdBlockAppInstalled = appDeferred.await(),
            hostsSinkholeDetected = hostsDeferred.await(),
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

    /**
     * Bait DNS resolution — resolve beberapa domain iklan ASLI (bukan
     * provider DNS) dan cek apakah hasilnya di-sinkhole ke loopback/
     * 0.0.0.0. Ini TIDAK butuh root sama sekali (murni `InetAddress`
     * biasa) dan justru sinyal PALING kuat karena menguji hasil akhir,
     * bukan mekanisme — jalan sama efektifnya baik blocking-nya lewat DNS
     * server custom, hosts file, ATAU filtering di dalam VPN tunnel app
     * lain yang sama sekali tidak kelihatan dari luar prosesnya.
     *
     * SENGAJA hanya menghitung resolusi yang benar-benar mendarat di
     * alamat sinkhole (loopback/any-local) sebagai positif — kegagalan
     * resolve total (UnknownHostException, timeout) TIDAK dihitung,
     * karena itu bisa juga cuma internet lambat/putus sesaat, captive
     * portal, dsb — bukan bukti kuat adblock. Timeout per-hostname 2.5
     * detik supaya tidak menahan proses gate ads terlalu lama kalau
     * jaringan memang lemot.
     */
    private suspend fun detectDnsSinkhole(): Boolean = withContext(Dispatchers.IO) {
        for (host in AD_BAIT_HOSTNAMES) {
            val sinkholed = withTimeoutOrNull(2_500L) {
                runCatching {
                    InetAddress.getAllByName(host).any { it.isLoopbackAddress || it.isAnyLocalAddress }
                }.getOrDefault(false)
            } ?: false
            if (sinkholed) return@withContext true
        }
        false
    }

    /**
     * Cek apakah salah satu app adblock/DNS-filter populer terinstal —
     * lihat KDoc [KNOWN_ADBLOCK_APP_PACKAGES] untuk keterbatasannya. Tidak
     * perlu entri <queries> tambahan (lihat alasan di sana).
     */
    private fun detectKnownAdBlockApp(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        KNOWN_ADBLOCK_APP_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    /**
     * Baca /system/etc/hosts MENTAH lewat root (kalau ada) dan cek isinya
     * langsung lewat native `nhosts` — lihat KDoc lengkap di
     * adblockguard.cpp (hostsContentSinkholesKnownAdDomain) untuk kenapa
     * ini pengganti yang jauh lebih ketat dibanding cuma cocokkan nama
     * folder module Magisk.
     */
    private suspend fun detectHostsFileSinkhole(): Boolean {
        if (!PrivilegeManager.status.value.rootGranted) return false
        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return false

        val result = runCatching {
            executor.exec("cat /system/etc/hosts 2>/dev/null")
        }.getOrElse { t ->
            Log.w(TAG, "Gagal membaca /system/etc/hosts: ${t.message}")
            return false
        }

        if (!result.success || result.output.isEmpty()) return false

        return runCatching { nativeMatchAdBlockHosts(result.outputText) }.getOrDefault(false)
    }
}
