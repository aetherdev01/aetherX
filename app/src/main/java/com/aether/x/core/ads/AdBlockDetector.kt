package com.aether.x.core.ads

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deteksi (BUKAN pemblokiran/pemaksaan) tiga sinyal umum yang menunjukkan
 * pengguna kemungkinan memblokir iklan di level sistem/jaringan — interface
 * VPN aktif, DNS custom (baik IP resolver manual MAUPUN hostname Private
 * DNS seperti "dns.adguard.com") yang cocok penyedia DNS pemblokir iklan
 * yang dikenal, dan modul Magisk yang dikenal terkait adblock. Logika
 * pencocokan sinyal ada di native (lihat adblockguard.cpp) — file ini
 * HANYA bertugas mengumpulkan data OS/jaringan yang dibutuhkan pencocokan
 * itu (lewat API Android biasa + [PrivilegeManager] yang sudah ada untuk
 * shell root/Shizuku) lalu meneruskannya.
 *
 * =====================================================================
 * LINGKUP OBJECT INI — DIBACA DULU SEBELUM MENAMBAH FUNGSI APA PUN DI SINI:
 * Ini OBJECT DETEKSI SAJA. Tidak ada, dan TIDAK AKAN PERNAH ada, fungsi di
 * sini yang mencoba melewati/menonaktifkan adblock yang terdeteksi,
 * memaksa permintaan iklan tetap terkirim walau diblokir, atau
 * menyembunyikan hasil deteksi dari pengguna (mis. diam-diam menganggap
 * "adblock terdeteksi" tanpa memberi tahu pengguna sama sekali).
 * Pemakaian yang DIMAKSUDKAN untuk sinyal dari [detect] adalah: tampilkan
 * pesan JUJUR ke pengguna (mis. "Sepertinya kamu pakai adblock — iklan
 * bantu AetherX tetap gratis, nonaktifkan adblock atau upgrade ke
 * Membership") yang bisa mereka lihat, pahami, dan tanggapi secara sadar
 * — BUKAN dark pattern yang diam-diam mengalahkan proteksi yang sengaja
 * mereka pasang sendiri. Kalau ada permintaan untuk mewujudkan fungsi
 * "paksa iklan tetap tampil walau adblock aktif" di object ini di masa
 * depan: JANGAN — itu di luar lingkup yang disepakati saat object ini
 * dibuat.
 * =====================================================================
 */
object AdBlockDetector {

    private const val TAG = "AdBlockDetector"
    private const val MAGISK_MODULES_PATH = "/data/adb/modules/"

    init {
        // Library SAMA dengan SignatureGuard/NativeIntegrityGuard (bukan
        // library terpisah) — lihat catatan lengkap soal kenapa nama
        // HARUS persis "aetherX" di SignatureGuard.kt. Pemuatan ganda
        // System.loadLibrary("aetherX") dari beberapa object aman
        // (JVM/ART hanya benar-benar memuat .so sekali per proses,
        // pemanggilan berikutnya jadi no-op).
        System.loadLibrary("aetherX")
    }

    private external fun nativeDetectVpnInterface(): Boolean
    private external fun nativeMatchAdBlockDns(dnsServers: Array<String>): Boolean
    private external fun nativeMatchAdBlockModule(moduleListing: String): Boolean

    /**
     * Hasil gabungan ketiga sinyal — lihat KDoc masing-masing fungsi
     * `detect*` di bawah untuk kekuatan/batasan tiap sinyal secara
     * spesifik sebelum dipakai untuk keputusan apa pun di sisi pemanggil.
     */
    data class AdBlockSignals(
        val vpnInterfaceActive: Boolean,
        val knownAdBlockDnsActive: Boolean,
        val knownMagiskModuleActive: Boolean,
    ) {
        /**
         * true kalau SALAH SATU sinyal aktif. CATATAN PENTING: karena
         * [vpnInterfaceActive] adalah sinyal PALING LEMAH (VPN apa pun
         * aktif, termasuk yang sama sekali tidak berkaitan dengan
         * adblock, akan membuat ini true — lihat KDoc lengkap di
         * adblockguard.cpp), pemanggil yang butuh keyakinan lebih tinggi
         * sebaiknya memprioritaskan [knownAdBlockDnsActive] atau
         * [knownMagiskModuleActive] secara terpisah, bukan cuma
         * mengandalkan [anyDetected] mentah-mentah.
         */
        val anyDetected: Boolean get() = vpnInterfaceActive || knownAdBlockDnsActive || knownMagiskModuleActive
    }

    /**
     * Jalankan ketiga deteksi sekaligus di [Dispatchers.IO] (semuanya
     * melibatkan I/O: baca interface jaringan, query ConnectivityManager,
     * dan berpotensi shell root — tidak boleh di main thread).
     *
     * SETIAP deteksi individual dibungkus [runCatching] dan gagal-ke-false
     * sendiri-sendiri (lihat fungsi private di bawah) — kalau satu sinyal
     * gagal dibaca (mis. getifaddrs error, ConnectivityManager null,
     * shell root belum granted), sinyal LAIN tetap dicoba secara
     * independen, TIDAK saling menjatuhkan.
     */
    suspend fun detect(context: Context): AdBlockSignals = withContext(Dispatchers.IO) {
        AdBlockSignals(
            vpnInterfaceActive = detectVpnInterface(),
            knownAdBlockDnsActive = detectAdBlockDns(context),
            knownMagiskModuleActive = detectMagiskModule(),
        )
    }

    /**
     * Sinyal PALING LEMAH dari ketiganya — lihat KDoc [AdBlockSignals] dan
     * penjelasan lengkap di adblockguard.cpp (bagian "CATATAN JUJUR SOAL
     * BATASAN") kenapa "ada interface VPN aktif" TIDAK SAMA dengan
     * "pengguna ini memblokir iklan".
     */
    private fun detectVpnInterface(): Boolean =
        runCatching { nativeDetectVpnInterface() }.getOrDefault(false)

    /**
     * Baca DNS server AKTIF perangkat lewat [ConnectivityManager] (API
     * Android standar, TIDAK butuh root/Shizuku — hanya butuh
     * ACCESS_NETWORK_STATE yang sudah ada di AndroidManifest.xml) lalu
     * cocokkan lewat native terhadap daftar penyedia DNS pemblokir iklan
     * yang dikenal (lihat adblockguard.cpp untuk daftarnya & sumber
     * verifikasinya).
     *
     * BUG FIX — sebelumnya hanya membaca [android.net.LinkProperties.dnsServers]
     * (daftar IP resolver DNS biasa), TIDAK PERNAH membaca
     * [android.net.LinkProperties.privateDnsServerName] (API 28+) sama
     * sekali. Ini dua API BERBEDA: kalau pengguna set Private DNS mode
     * "Hostname" di Settings -> Network -> Private DNS (persis skenario
     * "dns.adguard.com" — BUKAN mengisi manual DNS1/DNS2 di pengaturan
     * WiFi), maka nilai HOSTNAME itu ("dns.adguard.com") hanya muncul di
     * [privateDnsServerName]; [dnsServers] pada mode ini seringkali TETAP
     * berisi resolver bawaan carrier/router (karena resolusi DoT terjadi
     * transparan di level sistem) atau malah kosong — jadi walau native
     * punya daftar IP AdGuard yang benar, tidak akan pernah cocok karena
     * IP AdGuard-nya tidak pernah benar-benar sampai ke sisi Kotlin ini.
     * Sekarang hostname itu ikut dikumpulkan sebagai kandidat tambahan,
     * supaya native BISA mencocokkan by-hostname (jauh lebih andal
     * daripada menebak IP resolver DoT yang bisa berubah-ubah) selain
     * by-IP seperti sebelumnya.
     */
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

        // privateDnsServerName ada sejak API 28 (Android 9) — hanya terisi
        // saat mode Private DNS "Hostname" AKTIF (bukan "Automatic"/"Off").
        // Selalu null di bawah API 28, aman diabaikan (guard versi di
        // bawah), dan null juga di device modern kalau pengguna sedang
        // TIDAK pakai Private DNS hostname sama sekali — bukan bug, memang
        // begitu kontraknya.
        val privateDnsHostname = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            linkProperties.privateDnsServerName
        } else {
            null
        }

        resolverIps + listOfNotNull(privateDnsHostname)
    }.getOrDefault(emptyList())

    /**
     * Cek listing `/data/adb/modules/` — HANYA berarti kalau backend AKTIF
     * adalah ROOT SPESIFIK (bukan Shizuku, bukan tanpa akses sama sekali).
     * [MAGISK_MODULES_PATH] adalah direktori MILIK ROOT; Shizuku (bahkan
     * lewat ADB shell uid 2000 di kebanyakan konfigurasi standar) TIDAK
     * bisa membacanya — mencoba lewat Shizuku hanya akan selalu gagal
     * senyap (permission denied dari shell-nya sendiri) dan SALAH
     * menyimpulkan "tidak ada modul adblock", padahal sebenarnya
     * pengecekannya sendiri yang tidak pernah benar-benar jalan. Gate ini
     * mencegah kesimpulan keliru itu dengan skip sama sekali (bukan
     * false-negative diam-diam) kalau bukan root.
     *
     * PENTING soal arsitektur: fungsi ini TIDAK membaca file secara
     * langsung dari proses native — proses app biasa (bahkan dengan
     * Magisk terpasang di device) TIDAK otomatis punya izin baca
     * [MAGISK_MODULES_PATH] tanpa lewat `su` eksplisit. Listing-nya
     * didapat lewat [PrivilegeManager.getExecutor] (abstraksi root/Shizuku
     * yang SUDAH ADA dan dipakai di seluruh app ini untuk tweak lain),
     * BUKAN ditulis ulang di sini — hasilnya (satu string mentah) baru
     * diteruskan ke native untuk PENCOCOKAN kata kunci saja.
     */
    private suspend fun detectMagiskModule(): Boolean {
        if (PrivilegeManager.status.value.activeBackend != PrivilegeBackend.ROOT) {
            return false
        }
        val executor = PrivilegeManager.getExecutor() ?: return false

        val result = runCatching {
            executor.exec("ls $MAGISK_MODULES_PATH 2>/dev/null")
        }.getOrElse { t ->
            Log.w(TAG, "Gagal membaca listing modul Magisk: ${t.message}")
            return false
        }
        // `ls` pada direktori kosong/tidak ada tetap bisa "success" dengan
        // output kosong (mis. Magisk terpasang tapi belum ada modul sama
        // sekali) — itu BUKAN kegagalan, hanya berarti tidak ada modul apa
        // pun untuk dicocokkan, jadi false secara wajar (bukan skip).
        if (!result.success || result.output.isEmpty()) return false

        return runCatching { nativeMatchAdBlockModule(result.outputText) }.getOrDefault(false)
    }
}
