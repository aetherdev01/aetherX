package com.aether.x.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import android.util.Log
import java.security.MessageDigest
import kotlin.system.exitProcess

object SignatureGuard {

    private const val TAG = "AetherX-SignatureGuard"

    init {

        System.loadLibrary("aetherX")
    }

    private external fun nativeVerify(actualHashBytes: ByteArray): Boolean
    private external fun nativeVerifyRecheck(actualHashBytes: ByteArray): Boolean

    fun verifyOrDie(context: Context) {
        if (!isSignatureValid(context)) {

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

            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(signatures[0].toByteArray())
        }.getOrNull()
    }

    private fun crashImmediately() {

        runCatching { exitProcess(0) }
        Process.killProcess(Process.myPid())
    }
}
