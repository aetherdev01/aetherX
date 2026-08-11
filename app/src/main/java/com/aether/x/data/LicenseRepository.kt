package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
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

    /** Terlalu banyak percobaan gagal — rate limit SERVER-SIDE (lihat KDoc kelas ini). */
    data class RateLimited(val remainingSeconds: Int) : LicenseResult
}

/**
 * LicenseRepository — SELURUH validasi lisensi sekarang lewat Cloud
 * Functions (`activateLicense`/`revalidateLicense`, lihat
 * `cloud_functions/functions/src/` di root repo), BUKAN lagi akses
 * Firestore langsung dari client.
 *
 * KENAPA DIROMBAK TOTAL dari versi sebelumnya (yang memanggil
 * `FirebaseFirestore` langsung): versi lama bisa di-crack dengan salah
 * satu dari dua cara — (1) decompile APK, lihat persis logic evaluasi di
 * `evaluate()`, lalu patch smali supaya selalu return Valid tanpa
 * menyentuh Firestore sama sekali, atau (2) kalau Firestore Security
 * Rules longgar, langsung tulis dokumen `licenses/{key}` sendiri lewat
 * REST API Firestore tanpa lewat app. Client APK secara fundamental
 * tidak bisa dipercaya menyimpan logic keamanan — kodenya ada di tangan
 * attacker apa pun yang terjadi.
 *
 * Sekarang: client HANYA mengirim kunci lisensi dan menerima hasil
 * evaluasi jadi dari server. Tiga lapis pertahanan yang tidak bisa
 * dilihat/dipatch dari APK (karena jalan di infrastruktur Google, bukan
 * di device pengguna):
 *   1. Firebase App Check (Play Integrity) — Cloud Function menolak
 *      request yang tidak lolos verifikasi keaslian APK/perangkat
 *      SEBELUM kode evaluasi lisensi berjalan sama sekali.
 *   2. Firebase Auth anonim — identitas device untuk backend adalah
 *      `FirebaseAuth.currentUser.uid`, ditandatangani server Firebase
 *      sendiri, BUKAN string device ID yang dikirim bebas oleh client
 *      seperti versi lama (yang bisa dipalsukan attacker).
 *   3. Rate limit percobaan gagal disimpan di Firestore lewat Admin SDK
 *      (state di server, bukan SharedPreferences lokal yang bisa
 *      dihapus/dipalsukan lewat root/Frida/clear-app-data).
 *
 * Firestore Security Rules (`cloud_functions/firestore.rules`) mengunci
 * TOTAL akses client langsung ke koleksi `licenses`/`licenseAttempts` —
 * hanya Admin SDK di Cloud Functions yang bisa menyentuhnya.
 */
class LicenseRepository(context: Context) {

    private val appContext = context.applicationContext

    private val functions by lazy { Firebase.functions("asia-southeast2") }
    private val auth by lazy { Firebase.auth }

    private companion object {
        const val TAG = "LicenseRepository"
        const val POLL_INTERVAL_MILLIS = 60_000L
        const val SIGN_IN_RETRY_COUNT = 3
        const val SIGN_IN_RETRY_DELAY_MILLIS = 800L
    }

    /**
     * Pastikan ada sesi Firebase Auth anonim sebelum memanggil Cloud
     * Function apa pun — UID sesi ini yang dipakai server sebagai
     * identitas device (lihat KDoc kelas). Kalau sudah ada sesi anonim
     * dari sebelumnya (persist otomatis oleh Firebase Auth SDK selama
     * app tidak di-uninstall), tidak membuat sesi baru — UID yang sama
     * dipertahankan supaya lisensi yang sudah di-bind sebelumnya tetap
     * cocok.
     */
    private suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        // Retry ringan dengan backoff — koneksi yang sempat putus-nyambung
        // (mis. pindah dari WiFi ke data seluler) tidak boleh langsung
        // dianggap gagal permanen hanya karena percobaan pertama gagal.
        repeat(SIGN_IN_RETRY_COUNT) { attempt ->
            val success = runCatching {
                auth.signInAnonymously().await()
                true
            }.getOrElse { e ->
                Log.w(TAG, "Gagal membuat sesi Firebase Auth anonim (percobaan ${attempt + 1})", e)
                false
            }
            if (success) return true
            if (attempt < SIGN_IN_RETRY_COUNT - 1) {
                delay(SIGN_IN_RETRY_DELAY_MILLIS * (attempt + 1))
            }
        }
        return false
    }

    suspend fun activate(key: String): LicenseResult {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return LicenseResult.NotFound

        if (!ensureSignedIn()) return LicenseResult.NetworkError

        return runCatching {
            val response = functions
                .getHttpsCallable("activateLicense")
                .call(mapOf("key" to trimmedKey))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as? Map<String, Any?>
                ?: return LicenseResult.NetworkError

            val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong()
                ?: return LicenseResult.NetworkError

            LicenseResult.Valid(expiresAtMillis)
        }.getOrElse { e -> mapFunctionsException(e) }
    }

    /**
     * Cek ulang status lisensi yang sudah pernah diaktivasi, dipanggil
     * berkala (bukan real-time push seperti snapshot listener versi
     * lama — lihat KDoc `revalidateLicense.ts` untuk alasan trade-off
     * ini). Pemanggil ([MembershipViewModel]) sudah cocok dengan pola
     * `Flow` yang emit ulang tiap kali ada perubahan status, jadi
     * signature ini dipertahankan sama walau implementasinya sekarang
     * polling, bukan listener.
     */
    fun observe(key: String): Flow<LicenseResult> = flow {
        while (true) {
            emit(revalidateOnce())
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun revalidateOnce(): LicenseResult {
        if (!ensureSignedIn()) return LicenseResult.NetworkError

        return runCatching {
            val response = functions
                .getHttpsCallable("revalidateLicense")
                .call()
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as? Map<String, Any?>
                ?: return LicenseResult.NetworkError

            val status = data["status"] as? String
            val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong()

            when (status) {
                "valid" -> expiresAtMillis?.let { LicenseResult.Valid(it) } ?: LicenseResult.NetworkError
                "expired" -> expiresAtMillis?.let { LicenseResult.Expired(it) } ?: LicenseResult.NotFound
                "revoked" -> LicenseResult.Revoked
                "not_activated" -> LicenseResult.NotFound
                else -> LicenseResult.NetworkError
            }
        }.getOrElse { e -> mapFunctionsException(e) }
    }

    /**
     * Terjemahkan error dari pemanggilan Cloud Function (HttpsError di
     * sisi server, lihat `activateLicense.ts`/`revalidateLicense.ts`)
     * ke [LicenseResult] yang dikonsumsi UI. Kode error ini SENGAJA
     * ditentukan lengkap di server (bukan client menebak dari pesan
     * teks) — client hanya mem-mapping kode standar Firebase Functions.
     */
    private fun mapFunctionsException(e: Throwable): LicenseResult {
        val functionsException = e as? FirebaseFunctionsException
            ?: return LicenseResult.NetworkError.also {
                Log.w(TAG, "Gagal memanggil Cloud Function lisensi", e)
            }

        return when (functionsException.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> LicenseResult.NotFound
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> LicenseResult.Revoked
            FirebaseFunctionsException.Code.ALREADY_EXISTS -> LicenseResult.BoundToOtherDevice
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                val details = functionsException.details as? Map<*, *>
                val expiredAt = (details?.get("expiredAtMillis") as? Number)?.toLong()
                    ?: System.currentTimeMillis()
                LicenseResult.Expired(expiredAt)
            }
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                val details = functionsException.details as? Map<*, *>
                val remaining = (details?.get("remainingSeconds") as? Number)?.toInt() ?: 30
                LicenseResult.RateLimited(remaining)
            }
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> LicenseResult.NetworkError
            else -> {
                Log.w(TAG, "Cloud Function lisensi mengembalikan error tak terduga: ${functionsException.code}", e)
                LicenseResult.NetworkError
            }
        }
    }
}
