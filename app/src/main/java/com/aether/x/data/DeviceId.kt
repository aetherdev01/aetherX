package com.aether.x.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.aether.x.core.security.DeviceFingerprint

object DeviceId {
    private const val TAG = "AetherX-DeviceId"

    fun read(context: Context): String =
        DeviceFingerprint.compute(context) ?: run {
            Log.w(TAG, "Device fingerprint native gagal — fallback ke ANDROID_ID mentah")
            "fallback:${readRawAndroidId(context)}"
        }

    @SuppressLint("HardwareIds")
    private fun readRawAndroidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
}
