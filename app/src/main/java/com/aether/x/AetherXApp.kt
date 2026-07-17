package com.aether.x

import android.app.Application
import android.os.Process
import android.util.Log
import com.aether.x.core.ads.InterstitialAdGate
import com.aether.x.core.ads.InterstitialAdManager
import com.aether.x.core.ads.RewardedAdManager
import com.aether.x.core.ads.UnityInterstitialAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.adb.WirelessDebuggingMonitor
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.AppCheckInitializer
import com.aether.x.core.security.NativeIntegrityGuard
import com.aether.x.core.security.SignatureGuard
import com.aether.x.data.FcmTokenRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AetherXApp : Application() {

    companion object {
        init {
            // Konfigurasi shell root global — harus diset sebelum shell pertama dibuat.
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }

        // Singleton tunggal untuk seluruh app — SENGAJA satu instance yang
        // dipakai bersama oleh semua ViewModel yang butuh RewardGate (lihat
        // core/ads/RewardGate.kt), bukan dibuat baru per ViewModel. Kalau
        // dibuat baru tiap ViewModel, iklan yang sudah selesai di-preload di
        // satu layar akan "hilang" (instance manager-nya beda) saat pindah
        // ke layar lain yang punya reward-gate juga.
        //
        // BuildConfig.DEBUG dipakai sebagai testMode Unity Ads — WAJIB true
        // untuk build debug (supaya tidak menghabiskan/merusak metrik
        // inventory iklan asli saat development) dan otomatis false di
        // build release.
        val rewardedAdManager: RewardedAdManager by lazy {
            UnityRewardedAdManager(testMode = BuildConfig.DEBUG)
        }

        // Sama alasannya dengan rewardedAdManager di atas: satu instance
        // dipakai bersama supaya iklan yang sudah di-preload di satu layar
        // tidak "hilang" saat pindah layar, dan supaya cooldown di
        // InterstitialAdGate benar-benar global (bukan per-ViewModel).
        val interstitialAdManager: InterstitialAdManager by lazy {
            UnityInterstitialAdManager(testMode = BuildConfig.DEBUG)
        }

        val interstitialAdGate: InterstitialAdGate by lazy {
            InterstitialAdGate(interstitialAdManager)
        }

        // Scope umur-aplikasi (bukan umur-Activity/ViewModel) untuk
        // pekerjaan FCM yang harus tetap jalan sampai selesai walau
        // Activity yang memicunya (di sini: AetherXApp.onCreate, jadi
        // sebenarnya tidak terikat Activity manapun sejak awal) sudah
        // berpindah/berhenti. SupervisorJob supaya kegagalan sync token
        // (mis. offline saat pertama install) tidak mengganggu pekerjaan
        // lain yang memakai scope yang sama di masa depan.
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        // WAJIB dipanggil PALING PERTAMA, sebelum apa pun lain (bahkan
        // sebelum AppCheckInitializer) — kalau APK ini sudah dimodifikasi
        // dan di-resign ulang dengan kunci lain, app harus force-close
        // sesegera mungkin, sebelum sempat menampilkan splash screen atau
        // menyentuh Firebase/Firestore sama sekali. Lihat SignatureGuard.kt
        // untuk detail & batasan proteksi ini.
        //
        // Dibungkus try-catch KHUSUS UnsatisfiedLinkError/LinkageError (BUKAN
        // Exception biasa — keduanya adalah Error, kelas terpisah yang tidak
        // ikut tertangkap runCatching biasa di dalam SignatureGuard sendiri)
        // supaya kalau libaetherX.so gagal dimuat sama sekali (mis. APK
        // di-build tanpa folder jniLibs/cpp yang lengkap, atau ABI device
        // tidak match dengan .so yang di-bundle), pesan Logcat-nya jelas
        // menyebut MASALAH LINKING NATIVE LIB — bukan force-close misterius
        // tanpa jejak. App TETAP ditutup paksa setelahnya (fail-closed) —
        // ini BUKAN cara melewati/melonggarkan proteksi, hanya membuat
        // penyebabnya lebih mudah didiagnosis lewat `adb logcat`.
        try {
            SignatureGuard.verifyOrDie(this)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(
                "AetherXApp",
                "GAGAL memuat libaetherX.so (UnsatisfiedLinkError) — app akan " +
                    "ditutup paksa. Ini BUKAN masalah signature/tamper, melainkan " +
                    "native library tidak berhasil di-link. Penyebab umum: (1) APK " +
                    "di-build dari source tanpa folder native/jniLibs lengkap, " +
                    "(2) ABI .so yang di-bundle tidak cocok dengan CPU device ini, " +
                    "(3) App Bundle split config tidak menyertakan .so untuk ABI ini.",
                e,
            )
            Process.killProcess(Process.myPid())
            return
        }

        // Guard TAMBAHAN yang melengkapi baris di atas: SignatureGuard hanya
        // tahu kalau APK di-resign dengan kunci lain — tidak tahu kalau
        // libaetherX.so ITU SENDIRI dipatch langsung (byte instruksi
        // diubah lewat lief/radare2/Ghidra) tanpa perlu resign APK sama
        // sekali. Dipanggil di sini, tepat setelah SignatureGuard, supaya
        // urutan cek tetap: (1) APK resmi? (2) kalau ya, .so-nya belum
        // disunting? Lihat integrityguard.cpp & NativeIntegrityGuard.kt.
        try {
            NativeIntegrityGuard.verifyOrDie(this)
        } catch (e: UnsatisfiedLinkError) {
            Process.killProcess(Process.myPid())
            return
        }

        // WAJIB dipanggil SEBELUM Firestore dipakai di mana pun (mis. sebelum
        // LicenseRepository/UserIdRepository melakukan panggilan pertama) —
        // App Check harus terpasang sebelum instance FirebaseFirestore dibuat
        // supaya semua request Firestore sejak awal sudah membawa token App
        // Check. Lihat SECURITY.md dan firestore.rules (fungsi isVerifiedApp()).
        AppCheckInitializer.init(this)
        PrivilegeManager.init(this)

        // FITUR BARU — perbaikan alur "server Wireless debugging mati"
        // (lihat perintah rework: "ga perlu setup isi kode 6 digit lagi").
        // Dipantau seumur aplikasi (bukan seumur layar Izin Akses) supaya
        // begitu pengguna menyalakan lagi toggle Wireless debugging dari
        // Pengaturan sistem, AetherX otomatis coba sambungkan ulang dari
        // pairing tersimpan di background — terlepas dari layar mana yang
        // sedang dibuka saat itu. Lihat KDoc WirelessDebuggingMonitor.
        WirelessDebuggingMonitor.startObserving(this)

        // FITUR BARU — Firebase Cloud Messaging: subscribe device ini ke
        // topic broadcast default (maintenance/update/membership/general)
        // dan sinkronkan token FCM saat ini ke Firestore SEKALI di setiap
        // start aplikasi (bukan hanya saat pertama install) — supaya kalau
        // token sempat berubah selagi app benar-benar tidak pernah dibuka
        // sama sekali dalam rentang waktu tertentu (jarang tapi mungkin,
        // mis. dipicu pembersihan data Google Play Services oleh sistem),
        // Firestore tetap punya token terbaru begitu app dibuka lagi —
        // pelengkap dari onNewToken() di AetherXFirebaseMessagingService
        // yang menangani kasus token berubah SELAGI app tidak sedang
        // dibuka user secara aktif.
        //
        // Dipanggil best-effort lewat appScope (BUKAN blocking main thread):
        // subscribeToTopic/getToken adalah panggilan jaringan yang tidak
        // boleh menunda splash screen atau bagian lain startup aplikasi.
        FcmTokenRepository.subscribeToDefaultTopics()
        appScope.launch {
            FcmTokenRepository.syncTokenToFirestore(this@AetherXApp)
        }

        // Inisialisasi SDK ads (rewarded MAUPUN interstitial) SENGAJA TIDAK
        // dilakukan di sini. UnityRewardedAdManager.initialize() dan
        // UnityInterstitialAdManager.initialize() butuh parameter bertipe
        // Activity (persyaratan Unity Ads SDK), sedangkan AetherXApp adalah
        // Application — dua tipe berbeda yang tidak bisa saling
        // menggantikan (`this` di sini adalah Application, bukan Activity).
        // Inisialisasi sesungguhnya terjadi di MainActivity.onCreate, titik
        // pertama di app ini yang benar-benar punya instance Activity.
        // Lihat komentar di sana untuk urutan pemanggilannya.
    }
}
