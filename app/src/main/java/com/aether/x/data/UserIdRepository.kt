package com.aether.x.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class UserIdResolution(
    val userId: String,
    /** True when this device already had an identity, locally or in Firestore. */
    val alreadyRegistered: Boolean,
)

class UserIdRepository(private val preferences: AetherXPreferences, private val deviceId: String) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val devicesRef by lazy { firestore.collection("devices") }
    private val deviceRef by lazy { devicesRef.document(deviceId) }

    private companion object {
        const val TAG = "UserIdRepository"
        const val MAX_ATTEMPTS = 4
        const val INITIAL_BACKOFF_MILLIS = 1_000L

        const val DIGIT_COUNT = 6
        const val LETTER_COUNT = 2
        const val MAX_COLLISION_RETRIES = 5

        const val DIGITS = "0123456789"
        const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    }

    suspend fun resolveUserId(): String? = resolveUserIdentity()?.userId

    /**
     * Mengembalikan identitas beserta apakah perangkat ini sudah terdaftar.
     * Callback dipanggil tepat sebelum alokasi identitas baru dimulai, supaya
     * splash dapat menampilkan tahap pendaftaran yang sesuai pekerjaan nyata.
     */
    suspend fun resolveUserIdentity(
        onCheckingDevice: (() -> Unit)? = null,
        onRegisteringDevice: (() -> Unit)? = null,
        forceRemoteCheck: Boolean = false,
    ): UserIdResolution? {
        if (!forceRemoteCheck) {
            preferences.getSyncedUserId()?.let {
                return UserIdResolution(userId = it, alreadyRegistered = true)
            }
        }

        var backoff = INITIAL_BACKOFF_MILLIS
        repeat(MAX_ATTEMPTS) { attempt ->
            val resolved = try {
                resolveExistingOrAllocate(onCheckingDevice, onRegisteringDevice)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure(attempt, error)
                null
            }

            if (resolved != null) {
                preferences.setSyncedUserId(resolved.userId)
                return resolved
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                delay(backoff)
                backoff *= 2
            }
        }

        Log.w(TAG, "Gagal resolusi ID pengguna setelah $MAX_ATTEMPTS percobaan — tidak memakai fallback lokal.")
        return null
    }

    private fun logFailure(attempt: Int, e: Throwable) {
        val code = (e as? FirebaseFirestoreException)?.code
        val hint = when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore rules menolak baca/tulis ke devices/{id} — cek tab Rules di Firebase Console, pastikan sudah di-deploy ke project yang benar."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Firestore tidak terjangkau (offline / jaringan bermasalah)."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "Database Firestore tidak ditemukan di project ini — pastikan Firestore sudah dibuat (bukan cuma Realtime Database) di Firebase Console."
            else -> null
        }
        Log.w(
            TAG,
            "Percobaan ${attempt + 1}/$MAX_ATTEMPTS resolusi ID gagal" +
                (code?.let { " [kode Firestore: $it]" } ?: "") +
                (hint?.let { " — $it" } ?: ""),
            e,
        )
    }

    private suspend fun resolveExistingOrAllocate(
        onCheckingDevice: (() -> Unit)? = null,
        onRegisteringDevice: (() -> Unit)? = null,
    ): UserIdResolution {
        onCheckingDevice?.invoke()
        val existing = fetchExistingUserId()
        if (existing != null) {
            return UserIdResolution(userId = existing, alreadyRegistered = true)
        }

        onRegisteringDevice?.invoke()
        return UserIdResolution(
            userId = allocateAndRegister(),
            alreadyRegistered = false,
        )
    }

    private suspend fun fetchExistingUserId(): String? = suspendCancellableCoroutine { cont ->
        deviceRef.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.getString("userId")
                if (cont.isActive) cont.resume(existing)
            }
            .addOnFailureListener { error ->

                if (cont.isActive) cont.resumeWithException(error)
            }
    }

    private fun generateCandidateId(random: Random): String {
        val chars = buildList {
            repeat(DIGIT_COUNT) { add(DIGITS[random.nextInt(DIGITS.length)]) }
            repeat(LETTER_COUNT) { add(LETTERS[random.nextInt(LETTERS.length)]) }
        }.shuffled(random)
        return chars.joinToString(separator = "")
    }

    private suspend fun generateUniqueUserId(): String {
        val random = Random(System.nanoTime())
        repeat(MAX_COLLISION_RETRIES) {
            val candidate = generateCandidateId(random)
            if (!userIdExists(candidate)) return candidate
        }

        return generateCandidateId(random)
    }

    private suspend fun userIdExists(candidate: String): Boolean = suspendCancellableCoroutine { cont ->
        devicesRef.whereEqualTo("userId", candidate).limit(1).get(Source.SERVER)
            .addOnSuccessListener { snapshot -> if (cont.isActive) cont.resume(!snapshot.isEmpty) }
            .addOnFailureListener { if (cont.isActive) cont.resume(false) }
    }

    private suspend fun allocateAndRegister(): String {
        val candidate = generateUniqueUserId()
        return suspendCancellableCoroutine { cont ->
            firestore.runTransaction { txn ->
                val deviceSnapshot = txn.get(deviceRef)
                val now = FieldValue.serverTimestamp()

                if (!deviceSnapshot.exists()) {
                    txn.set(
                        deviceRef,
                        mapOf(
                            "deviceId" to deviceId,
                            "userId" to candidate,
                            "firstLoginAt" to now,
                            "lastLoginAt" to now,
                            "licenseActive" to false,
                            "licenseExpiresAt" to null,
                        ),
                    )
                } else {

                    txn.update(
                        deviceRef,
                        mapOf(
                            "userId" to candidate,
                            "lastLoginAt" to now,
                        ),
                    )
                }

                candidate
            }.addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result as String)
            }.addOnFailureListener { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
        }
    }
}
