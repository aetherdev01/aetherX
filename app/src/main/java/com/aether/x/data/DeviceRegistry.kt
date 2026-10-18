package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
                    "userId" to userId,
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
