package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

    suspend fun recordDeviceLogin(userId: String) {
        runCatching {
            val docRef = firestore.collection(COLLECTION).document(deviceId)
            val isFirstLogin = !documentExists(docRef)

            if (isFirstLogin) {
                val data = mapOf(
                    "deviceId" to deviceId,
                    "firstLoginAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "userId" to userId,
                )
                setDocument(docRef, data, merge = false)
            } else {
                val data = mapOf(
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "userId" to userId,
                )
                setDocument(docRef, data, merge = true)
            }
        }.onFailure { e ->
            Log.w(TAG, "Gagal mendata perangkat ke Firestore", e)
        }
    }

    private suspend fun documentExists(
        docRef: com.google.firebase.firestore.DocumentReference,
    ): Boolean = suspendCancellableCoroutine { cont ->
        docRef.get()
            .addOnSuccessListener { snapshot -> if (cont.isActive) cont.resume(snapshot.exists()) }
            .addOnFailureListener { if (cont.isActive) cont.resume(false) }
    }

    private suspend fun setDocument(
        docRef: com.google.firebase.firestore.DocumentReference,
        data: Map<String, Any>,
        merge: Boolean,
    ): Unit = suspendCancellableCoroutine { cont ->
        val task = if (merge) docRef.set(data, SetOptions.merge()) else docRef.set(data)
        task
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { if (cont.isActive) cont.resume(Unit) }
    }
}
