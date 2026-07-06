package com.aether.x.core.security

import android.content.Context
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * Guard lapis TAMBAHAN untuk [SignatureGuard]: memverifikasi bahwa byte
 * code fungsi-fungsi kritis di dalam `libaetherX.so` (nativeVerify /
 * nativeVerifyRecheck di sigcheck.cpp) belum dipatch langsung di binary
 * native-nya — lihat integrityguard.cpp untuk penjelasan lengkap kenapa ini
 * perlu (celah yang TIDAK ditutup oleh cek signing certificate saja: orang
 * bisa patch instruksi `cmp`/branch di dalam .so memakai lief/radare2/Ghidra
 * tanpa perlu resign APK sama sekali).
 *
 * BEDA dari SignatureGuard: guard ini memverifikasi INTEGRITAS KODE native
 * itu sendiri (byte instruksi mesin), bukan signing certificate APK. Dua
 * hal yang berbeda dan saling melengkapi — signature valid tidak berarti
 * .so-nya belum disunting, dan sebaliknya.
 *
 * CATATAN JUJUR SOAL BATASAN: sama seperti SignatureGuard, ini BUKAN
 * pertahanan sempurna — Frida tetap bisa hook fungsi verifikasi checksum
 * ini juga. Tujuannya menaikkan effort untuk crack casual, melengkapi
 * (bukan menggantikan) SignatureGuard dan validasi sisi server.
 */
object NativeIntegrityGuard {

    private const val TAG = "NativeIntegrityGuard"

    /** Kode hasil dari sisi native — lihat integrityguard.cpp untuk arti tiap nilai. */
    private const val RESULT_MISMATCH = 0
    private const val RESULT_MATCH = 1
    private const val RESULT_NOT_CONFIGURED = 2

    init {
        // Sama seperti SignatureGuard — kedua fungsi native ada di .so yang
        // sama (libaetherX.so), jadi load lib yang sama, bukan lib terpisah.
        // Nama HARUS persis sama dengan yang dipanggil SignatureGuard.kt dan
        // yang dihasilkan CMakeLists.txt (project("aetherX")) — lihat catatan
        // di SignatureGuard.kt.
        System.loadLibrary("aetherX")
    }

    private external fun nativeVerifyIntegrity(): Int

    /**
     * Jalankan verifikasi integritas dan force-close app kalau byte code
     * fungsi verifikasi signature terbukti sudah berubah dari hasil compile
     * resmi. Dipanggil dari [com.aether.x.AetherXApp.onCreate] setelah
     * [SignatureGuard.verifyOrDie] — urutan ini penting: percuma
     * memverifikasi integritas .so kalau APK-nya sendiri sudah bukan APK
     * resmi (itu tugas SignatureGuard, dicek lebih dulu).
     *
     * Kalau hasil native masih [RESULT_NOT_CONFIGURED] (checksum belum
     * digenerate untuk build ini — lihat catatan REGENERASI CHECKSUM di
     * integrityguard.cpp), guard ini SENGAJA di-skip (tidak force-close,
     * tidak dianggap valid) supaya build dev/staging yang belum sempat
     * generate checksum tidak tiba-tiba terkunci karena kesalahan
     * konfigurasi, bukan karena benar-benar di-tamper.
     */
    fun verifyOrDie(@Suppress("UNUSED_PARAMETER") context: Context) {
        val result = runCatching { nativeVerifyIntegrity() }.getOrDefault(RESULT_MISMATCH)
        when (result) {
            RESULT_MATCH -> {
                // Utuh, lanjut seperti biasa.
            }
            RESULT_NOT_CONFIGURED -> {
                Log.w(
                    TAG,
                    "Checksum integritas belum dikonfigurasi untuk build ini — " +
                        "guard dilewati. Lihat catatan REGENERASI CHECKSUM di integrityguard.cpp " +
                        "sebelum rilis ke publik.",
                )
            }
            else -> {
                Log.e(
                    TAG,
                    "Verifikasi integritas native GAGAL (byte code libaetherX.so " +
                        "tidak cocok dengan checksum resmi) — aplikasi akan ditutup paksa. " +
                        "Kalau kamu baru saja mengubah/rebuild native lib ini sendiri " +
                        "(bukan hasil tamper), regenerasi checksum-nya — lihat catatan " +
                        "REGENERASI CHECKSUM di integrityguard.cpp.",
                )
                crashImmediately()
            }
        }
    }

    private fun crashImmediately() {
        // Sama seperti SignatureGuard.crashImmediately() — tanpa UI/pesan,
        // langsung force-close, exitProcess dulu lalu killProcess sebagai
        // fallback.
        runCatching { exitProcess(0) }
        Process.killProcess(Process.myPid())
    }
}
