package com.aether.x.core.security

import android.content.Context
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

object NativeIntegrityGuard {

    private const val TAG = "NativeIntegrityGuard"

    private const val RESULT_MISMATCH = 0
    private const val RESULT_MATCH = 1
    private const val RESULT_NOT_CONFIGURED = 2

    init {

        System.loadLibrary("aetherX")
    }

    private external fun nativeVerifyIntegrity(): Int

    fun verifyOrDie(@Suppress("UNUSED_PARAMETER") context: Context) {
        val result = runCatching { nativeVerifyIntegrity() }.getOrDefault(RESULT_MISMATCH)
        when (result) {
            RESULT_MATCH -> {

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

        runCatching { exitProcess(0) }
        Process.killProcess(Process.myPid())
    }
}
