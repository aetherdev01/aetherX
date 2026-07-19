package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FcmTokenRepository {

    private const val TAG = "FcmTokenRepository"

    object Topics {
        const val MAINTENANCE = "maintenance"
        const val UPDATE = "update"
        const val MEMBERSHIP = "membership"
        const val GENERAL = "general"
    }

    private val ALL_DEFAULT_TOPICS = listOf(
        Topics.MAINTENANCE,
        Topics.UPDATE,
        Topics.MEMBERSHIP,
        Topics.GENERAL,
    )

    fun subscribeToDefaultTopics() {
        val messaging = FirebaseMessaging.getInstance()
        for (topic in ALL_DEFAULT_TOPICS) {
            messaging.subscribeToTopic(topic)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Gagal subscribe topic FCM \"$topic\" (akan dicoba lagi otomatis oleh SDK)", e)
                }
        }
    }

    suspend fun syncTokenToFirestore(context: Context, token: String? = null) {
        runCatching {
            val fcmToken = token ?: resolveCurrentToken()
            if (fcmToken != null) {
                val deviceId = DeviceId.read(context)
                val firestore = FirebaseFirestore.getInstance()
                updateDeviceDocument(firestore, deviceId, fcmToken)
            } else {
                Log.w(TAG, "Tidak berhasil mengambil token FCM saat ini, sinkronisasi dilewati.")
            }
        }.onFailure { e ->
            Log.w(TAG, "Gagal menyinkronkan token FCM ke Firestore", e)
        }
    }

    private suspend fun resolveCurrentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Gagal mengambil token FCM dari sistem", e)
                if (cont.isActive) cont.resume(null)
            }
    }

    private suspend fun updateDeviceDocument(
        firestore: FirebaseFirestore,
        deviceId: String,
        fcmToken: String,
    ): Unit = suspendCancellableCoroutine { cont ->
        firestore.collection("devices").document(deviceId)
            .update(
                mapOf(
                    "fcmToken" to fcmToken,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { e ->
                Log.w(
                    TAG,
                    "Gagal update fcmToken ke devices/$deviceId (dokumen mungkin belum dibuat UserIdRepository)",
                    e,
                )
                if (cont.isActive) cont.resume(Unit)
            }
    }
}
