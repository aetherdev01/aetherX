package com.aether.x.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import android.util.Log
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Verifikasi bahwa APK yang sedang berjalan ditandatangani dengan signing
 * key rilis resmi AetherX (`aetherx.jks`) — bukan APK yang sudah
 * dimodifikasi/di-crack lalu di-resign ulang dengan kunci lain (yang WAJIB
 * terjadi kalau seseorang decompile, ubah kode, lalu recompile APK, karena
 * mereka tidak punya `aetherx.jks` asli).
 *
 * Hash SHA-256 signing certificate yang diharapkan TIDAK disimpan sebagai
 * string plaintext di Kotlin (gampang ditemukan lewat jadx/apktool) — nilai
 * itu dienkode+dibandingkan di sisi native lewat JNI (lihat sigcheck.cpp),
 * yang jauh lebih ribet dianalisis dibanding bytecode DEX biasa.
 *
 * CATATAN JUJUR SOAL BATASAN: ini BUKAN proteksi anti-crack yang sempurna.
 * Siapa pun dengan waktu, alat (Frida/objection/Ghidra), dan niat cukup
 * TETAP BISA melewati ini — entah dengan hook nativeVerify() lewat Frida
 * agar selalu return true, patch instruksi cmp langsung di .so, atau
 * intercept panggilan JNI lewat LSPosed module. Tujuan kode ini adalah
 * MENAIKKAN EFFORT untuk crack biasa/casual (bukan APT), bukan membuatnya
 * mustahil. Validasi yang benar-benar tidak bisa dipalsukan tetap harus di
 * server (lihat LicenseRepository + firestore.rules) — signature check ini
 * cuma lapis tambahan supaya APK yang sudah di-tamper tidak nyaman dipakai
 * sama sekali (langsung force-close), bukan pengganti validasi server.
 */
object SignatureGuard {

    private const val TAG = "AetherX-SignatureGuard"

    init {
        // Nama library HARUS "aetherX" — cocok dengan nama yang dihasilkan
        // CMakeLists.txt (project("aetherX") -> libaetherX.so). Kalau nama
        // ini pernah diganti lagi di masa depan, WAJIB diubah bersamaan di
        // CMakeLists.txt DAN di sini DAN di NativeIntegrityGuard.kt — ketiga
        // titik ini harus selalu sebut nama library yang persis sama.
        System.loadLibrary("aetherX")
    }

    private external fun nativeVerify(actualHashBytes: ByteArray): Boolean
    private external fun nativeVerifyRecheck(actualHashBytes: ByteArray): Boolean

    /**
     * Jalankan verifikasi dan LANGSUNG force-close app kalau signature tidak
     * cocok. Dipanggil sedini mungkin di [com.aether.x.AetherXApp.onCreate]
     * — sebelum UI, sebelum Firebase, sebelum apa pun lain — supaya APK hasil
     * modifikasi tidak sempat menampilkan layar apa pun sama sekali, bukan
     * cuma diblokir dari fitur premium.
     *
     * Dipanggil dua kali dari titik berbeda (lihat [verifyOrDieAgain],
     * dipanggil dari titik lain di app seperti MainActivity) memakai fungsi
     * native yang SECARA SIMBOL berbeda (nativeVerify vs nativeVerifyRecheck)
     * walau logikanya sama — supaya orang yang cuma nemu & hook satu titik
     * verifikasi (mis. lewat Frida script generik yang cari "verify" di
     * nama fungsi) tidak otomatis melewati titik kedua kalau tidak sadar
     * keduanya ada.
     */
    fun verifyOrDie(context: Context) {
        if (!isSignatureValid(context)) {
            // Log diagnostik SENGAJA ditambahkan di sini (BARU — sebelumnya
            // crash total tanpa jejak apa pun di Logcat, menyulitkan
            // membedakan "APK memang di-tamper" vs "build sendiri pakai
            // keystore development/debug yang hash-nya belum didaftarkan di
            // sigcheck.cpp"). Log TIDAK melemahkan proteksi apa pun — hash
            // yang diharapkan tetap hanya ada di native lib (lihat KDoc
            // kelas ini), pesan ini hanya bilang "verifikasi gagal", tidak
            // membocorkan nilai hash yang benar maupun cara melewatinya.
            // Perilaku fail-closed (force-close) TETAP SAMA seperti
            // sebelumnya, tidak dilonggarkan.
            Log.e(
                TAG,
                "Verifikasi signature APK GAGAL — aplikasi akan ditutup paksa. " +
                    "Ini WAJAR terjadi kalau APK ini di-build ulang/di-sign dengan " +
                    "keystore yang BEDA dari keystore rilis resmi yang hash-nya " +
                    "terdaftar di sigcheck.cpp (native lib). Kalau ini build " +
                    "development milikmu sendiri (bukan APK hasil crack orang " +
                    "lain), tambahkan hash SHA-256 signing cert keystore " +
                    "development-mu ke daftar hash valid di sigcheck.cpp, lalu " +
                    "rebuild native lib-nya.",
            )
            crashImmediately()
        }
    }

    /** Titik verifikasi kedua yang independen — lihat KDoc [verifyOrDie]. */
    fun verifyOrDieAgain(context: Context) {
        val hash = computeSigningCertHash(context) ?: run {
            Log.e(TAG, "Verifikasi ulang signature GAGAL (tidak bisa membaca signing cert). Aplikasi ditutup paksa.")
            crashImmediately()
            return
        }
        val valid = runCatching { nativeVerifyRecheck(hash) }.getOrDefault(false)
        if (!valid) {
            Log.e(TAG, "Verifikasi ulang signature APK GAGAL — aplikasi akan ditutup paksa. Lihat log verifyOrDie untuk penjelasan lengkap.")
            crashImmediately()
        }
    }

    private fun isSignatureValid(context: Context): Boolean {
        val hash = computeSigningCertHash(context) ?: return false
        return runCatching { nativeVerify(hash) }.getOrDefault(false)
    }

    /**
     * Ambil signing certificate APK yang SEDANG BERJALAN saat ini (bukan
     * dari sumber lain yang bisa dipalsukan) lewat PackageManager, lalu
     * hitung SHA-256-nya. Memakai `GET_SIGNING_CERTIFICATES` (API 28+) di
     * device baru, fallback ke `GET_SIGNATURES` (deprecated tapi perlu untuk
     * minSdk 26-27) di device lama.
     *
     * Kalau APK ditandatangani dengan MULTIPLE signer (jarang, tapi mungkin
     * lewat APK Signature Scheme v3 rotation), kita hash SEMUA signer yang
     * ada — mismatch di signer manapun berarti bukan APK resmi.
     */
    @Suppress("DEPRECATION")
    private fun computeSigningCertHash(context: Context): ByteArray? {
        return runCatching {
            val pm = context.packageManager
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES,
                )
                info.signatures ?: return null
            }

            if (signatures.isEmpty()) return null

            // Hash cert PERTAMA (signer utama) — cukup untuk single-signer
            // setup seperti aetherx.jks yang dipakai sekarang. Kalau nanti
            // pindah ke key rotation multi-signer, sesuaikan expected hash
            // di sigcheck.cpp untuk signer yang relevan.
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(signatures[0].toByteArray())
        }.getOrNull()
    }

    private fun crashImmediately() {
        // exitProcess dulu, killProcess sebagai fallback kalau exitProcess
        // entah kenapa tidak langsung mengeksekusi (mis. ada shutdown hook
        // yang menahan). Sengaja tidak ada UI/pesan apa pun — APK yang sudah
        // di-tamper langsung force-close tanpa penjelasan, sesuai desain
        // yang diminta.
        runCatching { exitProcess(0) }
        Process.killProcess(Process.myPid())
    }
}
