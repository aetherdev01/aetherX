package com.aether.x.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.aether.x.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class DeviceRegistry(private val context: Context) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private companion object {
        const val TAG = "DeviceRegistry"
        const val COLLECTION = "devices"
    }

    val deviceId: String
        get() = DeviceId.read(context)

    /** Returns true only after Firestore confirms the device status update. */
    suspend fun recordDeviceLogin(userId: String): Boolean = suspendCancellableCoroutine { cont ->
        firestore.collection(COLLECTION).document(deviceId)
            .update(
                mapOf(
                    "deviceId" to deviceId,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "userId" to userId,
                    "deviceModel" to Build.MODEL.orEmpty(),
                    "manufacturer" to Build.MANUFACTURER.orEmpty(),
                    "buildFingerprint" to Build.FINGERPRINT.orEmpty(),
                    "androidVersion" to Build.VERSION.RELEASE.orEmpty(),
                    "sdkInt" to Build.VERSION.SDK_INT,
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "appVersionCode" to BuildConfig.VERSION_CODE,
                    "fingerprintSource" to if (deviceId.startsWith("fallback:")) "android-id-fallback" else "native-hmac-sha256",
                ),
            )
            .addOnSuccessListener {
                if (cont.isActive) cont.resume(true)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Gagal memperbarui status perangkat di Firestore", error)
                if (cont.isActive) cont.resume(false)
            }
    }
}
