package com.aether.x.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

object DeviceFingerprint {

    private const val TAG = "AetherX-DeviceFingerprint"

    private const val SEPARATOR = "\u0001"

    init {

        System.loadLibrary("aetherX")
    }

    private external fun nativeDeriveFingerprint(rawInput: ByteArray): ByteArray?

    fun compute(context: Context): String? {
        val rawInput = buildRawInput(context)
        val digest = runCatching { nativeDeriveFingerprint(rawInput.toByteArray(Charsets.UTF_8)) }
            .onFailure { e -> Log.w(TAG, "Gagal menghitung device fingerprint (native)", e) }
            .getOrNull()

        if (digest == null || digest.isEmpty()) {
            Log.w(TAG, "Device fingerprint native mengembalikan hasil kosong/null")
            return null
        }

        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    @SuppressLint("HardwareIds")
    private fun buildRawInput(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""
        return buildString {
            append(androidId)
            append(SEPARATOR)
            append(Build.FINGERPRINT)
            append(SEPARATOR)
            append(Build.MODEL)
        }
    }
}
