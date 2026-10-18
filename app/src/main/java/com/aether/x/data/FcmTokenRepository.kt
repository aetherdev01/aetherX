package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class FcmTokenSyncStage {
    FETCHING_TOKEN,
    WRITING_TOKEN,
}

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

    /**
     * Reports progress only when the corresponding real operation starts.
     * Returns true only after the token has been written successfully to Firestore.
     */
    suspend fun syncTokenToFirestore(
        context: Context,
        token: String? = null,
        onStage: ((FcmTokenSyncStage) -> Unit)? = null,
    ): Boolean {
        return try {
            val fcmToken = if (token != null) {
                token
            } else {
                onStage?.invoke(FcmTokenSyncStage.FETCHING_TOKEN)
                resolveCurrentToken()
            }

            if (fcmToken.isNullOrBlank()) {
                Log.w(TAG, "Tidak berhasil mengambil token FCM saat ini, sinkronisasi dilewati.")
                return false
            }

            onStage?.invoke(FcmTokenSyncStage.WRITING_TOKEN)
            val deviceId = DeviceId.read(context)
            updateDeviceDocument(FirebaseFirestore.getInstance(), deviceId, fcmToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyinkronkan token FCM ke Firestore", e)
            false
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
    ): Boolean = suspendCancellableCoroutine { cont ->
        firestore.collection("devices").document(deviceId)
            .update(
                mapOf(
                    "fcmToken" to fcmToken,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
            .addOnFailureListener { e ->
                Log.w(
                    TAG,
                    "Gagal update fcmToken ke devices/$deviceId (dokumen mungkin belum dibuat UserIdRepository)",
                    e,
                )
                if (cont.isActive) cont.resume(false)
            }
    }
}
