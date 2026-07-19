package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface LicenseResult {

    data class Valid(val expiresAtMillis: Long) : LicenseResult

    data object NotFound : LicenseResult

    data object Revoked : LicenseResult

    data object BoundToOtherDevice : LicenseResult

    data class Expired(val expiredAtMillis: Long) : LicenseResult

    data object NetworkError : LicenseResult
}

class LicenseRepository(private val context: Context) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val deviceId: String get() = DeviceId.read(context)
    private val deviceRef by lazy { firestore.collection("devices").document(deviceId) }

    private companion object {
        const val COLLECTION = "licenses"
    }

    suspend fun activate(key: String): LicenseResult {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return LicenseResult.NotFound

        val result = runCatching { activateTransaction(trimmedKey) }
            .getOrElse { e ->
                if (e is LicenseSignal) return@getOrElse e.result
                if (e is FirebaseFirestoreException) return@getOrElse LicenseResult.NetworkError
                LicenseResult.NetworkError
            }

        if (result is LicenseResult.Valid) {
            recordLicenseStatus(active = true, expiresAtMillis = result.expiresAtMillis)
        }
        return result
    }

    fun observe(key: String): Flow<LicenseResult> = callbackFlow {
        val docRef = firestore.collection(COLLECTION).document(key)
        val registration = docRef.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(LicenseResult.NetworkError)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(LicenseResult.NotFound)
                return@addSnapshotListener
            }
            val status = snapshot.getString("status")
            val boundDeviceId = snapshot.getString("deviceId")
            val expiresAt = snapshot.getTimestamp("expiresAt")
            trySend(
                evaluate(status = status, boundDeviceId = boundDeviceId, expiresAtMillis = expiresAt?.toDate()?.time),
            )
        }
        awaitClose { registration.remove() }
    }

    suspend fun revalidate(key: String): LicenseResult {
        val result = runCatching { fetchAndCheck(key) }
            .getOrElse { e ->
                if (e is LicenseSignal) return@getOrElse e.result
                LicenseResult.NetworkError
            }

        when (result) {
            is LicenseResult.Valid -> recordLicenseStatus(active = true, expiresAtMillis = result.expiresAtMillis)
            is LicenseResult.Expired,
            LicenseResult.Revoked,
            LicenseResult.BoundToOtherDevice,
            LicenseResult.NotFound -> recordLicenseStatus(active = false, expiresAtMillis = null)
            LicenseResult.NetworkError -> Unit
        }
        return result
    }

    private suspend fun recordLicenseStatus(active: Boolean, expiresAtMillis: Long?) {
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                val data = mutableMapOf<String, Any?>(
                    "licenseActive" to active,
                    "licenseExpiresAt" to expiresAtMillis?.let { Timestamp(it / 1000, 0) },
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                )
                deviceRef.update(data)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(Unit) }
            }
        }.onFailure { e ->
            Log.w("LicenseRepository", "Gagal mencatat status lisensi ke devices/$deviceId", e)
        }
    }

    private class LicenseSignal(val result: LicenseResult) : Exception()

    private suspend fun fetchAndCheck(key: String): LicenseResult = suspendCancellableCoroutine { cont ->
        firestore.collection(COLLECTION).document(key).get()
            .addOnSuccessListener { snapshot ->
                if (!cont.isActive) return@addOnSuccessListener
                if (!snapshot.exists()) {
                    cont.resume(LicenseResult.NotFound)
                    return@addOnSuccessListener
                }
                val status = snapshot.getString("status")
                val boundDeviceId = snapshot.getString("deviceId")
                val expiresAt = snapshot.getTimestamp("expiresAt")

                cont.resume(
                    evaluate(status = status, boundDeviceId = boundDeviceId, expiresAtMillis = expiresAt?.toDate()?.time),
                )
            }
            .addOnFailureListener { if (cont.isActive) cont.resume(LicenseResult.NetworkError) }
    }

    private suspend fun activateTransaction(key: String): LicenseResult = suspendCancellableCoroutine { cont ->
        val docRef = firestore.collection(COLLECTION).document(key)
        firestore.runTransaction { txn ->
            val snapshot = txn.get(docRef)
            if (!snapshot.exists()) throw LicenseSignal(LicenseResult.NotFound)

            val status = snapshot.getString("status")
            val boundDeviceId = snapshot.getString("deviceId")
            val expiresAt = snapshot.getTimestamp("expiresAt")
            val expiresAtMillis = expiresAt?.toDate()?.time

            if (status == "revoked") throw LicenseSignal(LicenseResult.Revoked)

            if (boundDeviceId != null && boundDeviceId != deviceId) {
                throw LicenseSignal(LicenseResult.BoundToOtherDevice)
            }

            if (expiresAtMillis == null) throw LicenseSignal(LicenseResult.NetworkError)
            if (expiresAtMillis < System.currentTimeMillis()) {
                throw LicenseSignal(LicenseResult.Expired(expiresAtMillis))
            }

            if (boundDeviceId == null) {
                txn.update(
                    docRef,
                    mapOf(
                        "deviceId" to deviceId,
                        "status" to "active",
                        "activatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }

            expiresAtMillis
        }.addOnSuccessListener { expiresAtMillis ->
            if (cont.isActive) cont.resume(LicenseResult.Valid(expiresAtMillis as Long))
        }.addOnFailureListener { error ->
            if (!cont.isActive) return@addOnFailureListener
            val signal = error as? LicenseSignal ?: error.cause as? LicenseSignal
            if (signal != null) {
                cont.resume(signal.result)
            } else {
                cont.resumeWithException(error)
            }
        }
    }

    private fun evaluate(status: String?, boundDeviceId: String?, expiresAtMillis: Long?): LicenseResult {
        if (status == "revoked") return LicenseResult.Revoked
        if (boundDeviceId != null && boundDeviceId != deviceId) return LicenseResult.BoundToOtherDevice
        if (boundDeviceId == null) return LicenseResult.NotFound
        if (expiresAtMillis == null) return LicenseResult.NetworkError
        if (expiresAtMillis < System.currentTimeMillis()) return LicenseResult.Expired(expiresAtMillis)
        return LicenseResult.Valid(expiresAtMillis)
    }
}
