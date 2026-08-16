package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

sealed interface LicenseResult {

    data class Valid(val expiresAtMillis: Long) : LicenseResult

    data object NotFound : LicenseResult

    data object Revoked : LicenseResult

    data object BoundToOtherDevice : LicenseResult

    data class Expired(val expiredAtMillis: Long) : LicenseResult

    data object NetworkError : LicenseResult

    /** Terlalu banyak percobaan gagal — rate limit CLIENT-SIDE (lihat LicenseAttemptGuard). */
    data class RateLimited(val remainingSeconds: Int) : LicenseResult
}

/**
 * LicenseRepository — ROLLBACK ke akses Firestore LANGSUNG dari client
 * (bukan lewat Cloud Functions `activateLicense`/`revalidateLicense` lagi).
 *
 * KENAPA DIROMBAK BALIK: source Cloud Functions (`cloud_functions/functions/
 * src) hilang/tidak bisa ditemukan lagi dan APK didistribusikan lewat
 * Telegram (sideload manual, bukan Play Store) — Firebase App Check dengan
 * Play Integrity provider TIDAK BISA mengeluarkan token valid untuk APK yang
 * tidak melalui jalur distribusi resmi Play Console, apa pun signing key-nya.
 * Ini bikin SEMUA panggilan Cloud Function ditolak (UNAUTHENTICATED) sebelum
 * logic lisensi sempat jalan. Tanpa source Cloud Functions untuk
 * didiagnosis/diperbaiki, dan tanpa jalur Play Console yang dipakai, jalur
 * client -> Cloud Function -> Firestore Admin SDK tidak bisa dipertahankan.
 *
 * Rules `firestore.rules` yang ada SUDAH didesain untuk mode ini —
 * `isVerifiedApp()` sudah `return true` (App Check tidak diwajibkan di
 * level rules), dan struktur `match /licenses/{key}` sudah mendukung
 * transisi device-binding one-shot (unused -> active) langsung dari client
 * lewat `allow update`.
 *
 * TRADE-OFF YANG DISADARI (dibanding versi Cloud Functions):
 *   - Logic evaluasi lisensi (expired/revoked/device-binding) sekarang ada
 *     di APK, bisa dibaca lewat decompile. Firestore Security Rules tetap
 *     jadi penjaga TERAKHIR yang tidak bisa dipatch dari APK (rules jalan
 *     di server Firebase, bukan di device) — SELAMA rules-nya ketat, client
 *     yang di-patch untuk selalu menganggap sukses tetap tidak bisa menulis
 *     dokumen licenses/{key} yang melanggar rules.
 *   - Rate limit percobaan gagal sekarang murni client-side
 *     (LicenseAttemptGuard, SharedPreferences) — bisa dilewati lewat
 *     root/Frida/clear-app-data. Diterima sebagai trade-off sementara
 *     karena tidak ada infra server (Cloud Functions) yang bisa dipakai.
 *   - Device ID pengikat lisensi TETAP dikirim sebagai field biasa
 *     (bukan lagi UID Firebase Auth yang ditandatangani server) — sama
 *     seperti arsitektur "versi lama" yang disebut di riwayat proyek ini.
 *     Ini risiko yang sama dengan versi lama, tapi diterima sebagai kompromi wajar untuk app
 *     hobi/skala kecil yang didistribusikan lewat Telegram.
 */
class LicenseRepository(context: Context) {

    private val appContext = context.applicationContext

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private companion object {
        const val TAG = "LicenseRepository"
        const val POLL_INTERVAL_MILLIS = 60_000L
        const val COLLECTION = "licenses"

        const val FIELD_DEVICE_ID = "deviceId"
        const val FIELD_STATUS = "status"
        const val FIELD_ACTIVATED_AT = "activatedAt"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_CREATED_AT = "createdAt"

        const val STATUS_UNUSED = "unused"
        const val STATUS_ACTIVE = "active"
    }

    private val deviceId: String by lazy { DeviceId.read(appContext) }

    suspend fun activate(key: String): LicenseResult {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return LicenseResult.NotFound

        val docRef = firestore.collection(COLLECTION).document(trimmedKey)

        return runCatching {
            firestore.runTransaction { tx ->
                val snapshot = tx.get(docRef)
                if (!snapshot.exists()) {
                    return@runTransaction LicenseResult.NotFound
                }

                val status = snapshot.getString(FIELD_STATUS)
                val boundDeviceId = snapshot.getString(FIELD_DEVICE_ID)
                val expiresAt = snapshot.getTimestamp(FIELD_EXPIRES_AT)

                when {
                    // Sudah terikat ke device INI sebelumnya — anggap sukses
                    // lagi (mis. pengguna re-input key yang sama setelah
                    // clear app data), tidak perlu tulis ulang dokumen.
                    boundDeviceId == deviceId && status == STATUS_ACTIVE -> {
                        val millis = expiresAt?.toDate()?.time
                        if (millis != null && millis > System.currentTimeMillis()) {
                            LicenseResult.Valid(millis)
                        } else if (millis != null) {
                            LicenseResult.Expired(millis)
                        } else {
                            LicenseResult.NetworkError
                        }
                    }

                    boundDeviceId != null && boundDeviceId != deviceId -> {
                        LicenseResult.BoundToOtherDevice
                    }

                    status != STATUS_UNUSED -> {
                        LicenseResult.Revoked
                    }

                    expiresAt != null && expiresAt.toDate().time <= System.currentTimeMillis() -> {
                        LicenseResult.Expired(expiresAt.toDate().time)
                    }

                    else -> {
                        // Klaim key ini untuk device sekarang — field yang
                        // ditulis di sini WAJIB persis cocok dengan yang
                        // diwajibkan firestore.rules (match /licenses/{key}
                        // allow update, cabang pertama), kalau tidak
                        // transaksi akan ditolak PERMISSION_DENIED oleh
                        // server walau logic di atas sudah lolos di client.
                        val updates = mapOf(
                            FIELD_DEVICE_ID to deviceId,
                            FIELD_STATUS to STATUS_ACTIVE,
                            FIELD_ACTIVATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            FIELD_EXPIRES_AT to expiresAt,
                            FIELD_CREATED_AT to snapshot.get(FIELD_CREATED_AT),
                        )
                        tx.update(docRef, updates)
                        val millis = expiresAt?.toDate()?.time ?: return@runTransaction LicenseResult.NetworkError
                        LicenseResult.Valid(millis)
                    }
                }
            }.await()
        }.getOrElse { e -> mapFirestoreException(e) }
    }

    /**
     * Cek ulang status lisensi yang sudah pernah diaktivasi, dipanggil
     * berkala oleh [MembershipViewModel]. Baca dokumen langsung (bukan
     * snapshot listener realtime) supaya konsisten dengan pola polling
     * yang sudah ada — bisa diganti addSnapshotListener nanti kalau mau
     * update instan tanpa nunggu interval.
     */
    fun observe(key: String): Flow<LicenseResult> = flow {
        while (true) {
            emit(revalidateOnce(key))
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun revalidateOnce(key: String): LicenseResult {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return LicenseResult.NotFound

        return runCatching {
            val snapshot = firestore.collection(COLLECTION).document(trimmedKey).get().await()
            if (!snapshot.exists()) return@runCatching LicenseResult.NotFound

            val status = snapshot.getString(FIELD_STATUS)
            val boundDeviceId = snapshot.getString(FIELD_DEVICE_ID)
            val expiresAt = snapshot.getTimestamp(FIELD_EXPIRES_AT)?.toDate()?.time

            when {
                boundDeviceId != null && boundDeviceId != deviceId -> LicenseResult.BoundToOtherDevice
                status != STATUS_ACTIVE -> LicenseResult.NotFound
                expiresAt == null -> LicenseResult.NetworkError
                expiresAt <= System.currentTimeMillis() -> LicenseResult.Expired(expiresAt)
                else -> LicenseResult.Valid(expiresAt)
            }
        }.getOrElse { e -> mapFirestoreException(e) }
    }

    private fun mapFirestoreException(e: Throwable): LicenseResult {
        val firestoreException = e as? FirebaseFirestoreException
            ?: return LicenseResult.NetworkError.also {
                Log.w(TAG, "Gagal mengakses Firestore lisensi", e)
            }

        return when (firestoreException.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> LicenseResult.Revoked
            FirebaseFirestoreException.Code.NOT_FOUND -> LicenseResult.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> LicenseResult.NetworkError
            else -> {
                Log.w(TAG, "Firestore lisensi mengembalikan error tak terduga: ${firestoreException.code}", e)
                LicenseResult.NetworkError
            }
        }
    }
}
