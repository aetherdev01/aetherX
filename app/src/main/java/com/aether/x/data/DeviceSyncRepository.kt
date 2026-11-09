package com.aether.x.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.aether.x.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Refreshes the existing device registry document from the native fingerprint source.
 * The native path sends a one-way HMAC fingerprint; the legacy Android ID fallback is
 * retained only when the native library is unavailable to preserve existing device binding.
 */
class DeviceSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun sync(): Boolean {
        val deviceId = runCatching { DeviceId.read(appContext) }.getOrElse { error ->
            Log.w(TAG, "Tidak dapat membaca identitas perangkat", error)
            return false
        }
        if (deviceId.isBlank()) return false

        val payload = mapOf(
            "deviceId" to deviceId,
            "deviceModel" to Build.MODEL.orEmpty(),
            "manufacturer" to Build.MANUFACTURER.orEmpty(),
            "buildFingerprint" to Build.FINGERPRINT.orEmpty(),
            "androidVersion" to Build.VERSION.RELEASE.orEmpty(),
            "sdkInt" to Build.VERSION.SDK_INT,
            "appVersion" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "fingerprintSource" to if (deviceId.startsWith("fallback:")) "android-id-fallback" else "native-hmac-sha256",
            "lastSeenAt" to FieldValue.serverTimestamp(),
        )

        return runCatching {
            // update() intentionally requires an existing devices/{deviceId} record;
            // it will not accidentally create a partial user record before registration.
            firestore.collection(COLLECTION).document(deviceId).update(payload).await()
            true
        }.getOrElse { error ->
            Log.w(TAG, "Sinkronisasi perangkat ke server gagal", error)
            false
        }
    }

    private companion object {
        const val TAG = "AetherXDeviceSync"
        const val COLLECTION = "devices"
    }
}
