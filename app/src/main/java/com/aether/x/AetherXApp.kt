package com.aether.x

import android.app.Application
import android.os.Process
import android.util.Log
import com.aether.x.core.ads.InterstitialAdGate
import com.aether.x.core.ads.InterstitialAdManager
import com.aether.x.core.ads.RewardedAdManager
import com.aether.x.core.ads.UnityInterstitialAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.AppCheckInitializer
import com.aether.x.core.security.NativeIntegrityGuard
import com.aether.x.core.security.SignatureGuard
import com.topjohnwu.superuser.Shell

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
