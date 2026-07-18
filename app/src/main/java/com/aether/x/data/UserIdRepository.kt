package com.aether.x.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Sumber ID pengguna yang ditampilkan (mis. "ID-K3p9X2q7"): string 8 karakter
 * acak — 6 digit angka + 2 huruf (besar/kecil campur), posisi huruf diacak
 * supaya polanya tidak mudah ditebak — dibuat sekali per device dan disimpan
 * permanen di Firestore (`devices/{deviceId}`, field `userId`) supaya nilainya
 * konsisten selama device yang sama dipakai.
 *
 * Dokumen perangkat (`devices/{deviceId}`) berfungsi ganda sebagai "kartu
 * identitas" device ini di Firestore — berisi `userId`, `deviceId`, DAN status
 * lisensi (`licenseActive`, `licenseExpiresAt`) supaya semua informasi
 * tentang satu device ada di satu dokumen yang gampang dicek lewat Firebase
 * Console.
 *
 * SENGAJA TIDAK ADA fallback ID lokal-saja. ID pengguna dipakai sebagai
 * identitas yang ditampilkan ke pengguna — nilai yang "keliru dikira asli"
 * lebih berbahaya daripada sekadar tidak menampilkan apa pun sementara.
 * Kalau alokasi gagal (offline, dsb), [resolveUserId] mencoba lagi beberapa
 * kali dengan jeda yang membesar (exponential backoff), dan kalau tetap
 * gagal, mengembalikan `null` — UI ([TweakScreen]) sudah menangani `userId
 * == null` dengan cara paling aman: pill ID pengguna cuma disembunyikan,
 * bukan menampilkan nilai yang salah.
 *
 * Sekali berhasil dialokasikan, ID tersebut disimpan permanen secara lokal
 * ([AetherXPreferences.setSyncedUserId]) supaya panggilan berikutnya tidak
 * perlu ke jaringan lagi dan nilainya tidak pernah berubah.
 *
 * PEMULIHAN SETELAH UNINSTALL/INSTALL ULANG: preferensi lokal (tempat
 * `userId` disimpan) ikut terhapus saat app di-uninstall, padahal device
 * fingerprint pada umumnya tetap sama (lihat catatan di [DeviceId]).
 * Sebelum mengalokasikan ID BARU, [resolveUserId] sekarang cek dulu apakah
 * device ini SUDAH punya dokumen di koleksi `devices` (dari sesi sebelum
 * uninstall) — kalau ada dan field `userId`-nya masih tersimpan di sana, ID
 * itu yang dipakai lagi (badge "ID-…" balik ke nilai yang sama, bukan nilai
 * baru atau kosong).
 *
 * KEMUNGKINAN TABRAKAN (collision): dengan ruang 8 karakter (6 digit + 2
 * huruf dari 52 kombinasi besar/kecil), total kombinasi jauh lebih dari
 * cukup untuk skala pengguna yang realistis, tapi [generateCandidateId] tetap
 * mengecek keberadaan ID tersebut di koleksi `devices` sebelum dipakai
 * (lihat [allocateAndRegister]) dan mencoba ulang dengan kandidat baru kalau
 * ternyata sudah dipakai device lain.
 */
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

    /**
     * Mengembalikan ID pengguna asli, atau `null` kalau setelah [MAX_ATTEMPTS]
     * percobaan (dengan backoff) tetap gagal menghubungi Firestore — TIDAK
     * PERNAH mengembalikan nilai lokal yang belum tersinkron. Pemanggil (mis.
     * [TweakViewModel]) bebas memanggil ulang fungsi ini lagi nanti (mis.
     * saat koneksi pulih) untuk mencoba lagi; setiap panggilan yang gagal
     * tidak meninggalkan efek samping yang perlu dibersihkan.
     */
    suspend fun resolveUserId(): String? {
        preferences.getSyncedUserId()?.let { return it }

        var backoff = INITIAL_BACKOFF_MILLIS
        repeat(MAX_ATTEMPTS) { attempt ->
            val resolved = runCatching { resolveExistingOrAllocate() }
                .onFailure { e -> logFailure(attempt, e) }
                .getOrNull()

            if (resolved != null) {
                preferences.setSyncedUserId(resolved)
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

    /**
     * Log yang menyertakan kode error Firestore secara eksplisit (mis.
     * PERMISSION_DENIED, UNAVAILABLE, dsb) supaya kalau ini muncul di Logcat,
     * penyebabnya langsung terlihat tanpa perlu menebak dari stacktrace saja.
     */
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

    /**
     * Cek dulu dokumen `devices/{deviceId}` yang sudah ada (mis. dari sebelum
     * app di-uninstall) — kalau device ini sudah pernah dialokasikan `userId`,
     * pakai lagi nilai itu supaya konsisten setelah install ulang. Hanya kalau
     * device ini benar-benar baru (dokumen belum ada / belum punya `userId`)
     * baru generate ID baru + catat device dalam satu transaksi atomik.
     */
    private suspend fun resolveExistingOrAllocate(): String {
        val existing = fetchExistingUserId()
        if (existing != null) return existing
        return allocateAndRegister()
    }

    private suspend fun fetchExistingUserId(): String? = suspendCancellableCoroutine { cont ->
        deviceRef.get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.getString("userId")
                if (cont.isActive) cont.resume(existing)
            }
            .addOnFailureListener { error ->
                // Gagal baca (mis. offline) bukan berarti device belum pernah
                // terdaftar — jangan diam-diam alokasikan ID baru di sini,
                // biarkan resolveUserId() yang retry lewat backoff di atas.
                if (cont.isActive) cont.resumeWithException(error)
            }
    }

    /**
     * Membuat string ID 8 karakter: [DIGIT_COUNT] digit angka + [LETTER_COUNT]
     * huruf (besar/kecil campur dari [LETTERS]), lalu posisi seluruh karakter
     * diacak sekali lagi (shuffle) supaya huruf tidak selalu nongol di ujung —
     * polanya tidak mudah ditebak hanya dari melihat beberapa contoh ID.
     */
    private fun generateCandidateId(random: Random): String {
        val chars = buildList {
            repeat(DIGIT_COUNT) { add(DIGITS[random.nextInt(DIGITS.length)]) }
            repeat(LETTER_COUNT) { add(LETTERS[random.nextInt(LETTERS.length)]) }
        }.shuffled(random)
        return chars.joinToString(separator = "")
    }

    /**
     * Menggenerate kandidat ID lalu memastikan belum dipakai device lain
     * (query `devices` where `userId == kandidat`) sebelum dipakai — kalau
     * sudah dipakai (kemungkinan sangat kecil, lihat KDoc kelas ini), coba
     * lagi dengan kandidat baru hingga [MAX_COLLISION_RETRIES] kali.
     */
    private suspend fun generateUniqueUserId(): String {
        val random = Random(System.nanoTime())
        repeat(MAX_COLLISION_RETRIES) {
            val candidate = generateCandidateId(random)
            if (!userIdExists(candidate)) return candidate
        }
        // Semua percobaan tabrakan (secara statistik nyaris mustahil) —
        // pakai kandidat terakhir apa adanya; transaksi di allocateAndRegister
        // tetap aman karena document ID tetap deviceId, bukan userId ini.
        return generateCandidateId(random)
    }

    private suspend fun userIdExists(candidate: String): Boolean = suspendCancellableCoroutine { cont ->
        devicesRef.whereEqualTo("userId", candidate).limit(1).get()
            .addOnSuccessListener { snapshot -> if (cont.isActive) cont.resume(!snapshot.isEmpty) }
            .addOnFailureListener { if (cont.isActive) cont.resume(false) }
    }

    /**
     * Membuat/memperbarui dokumen `devices/{deviceId}` dengan ID baru hasil
     * generate, dalam SATU transaksi Firestore.
     *
     * Dokumen `devices/{deviceId}` yang ditulis di sini sekarang berisi:
     * - `deviceId`: sama dengan document ID (hash fingerprint device —
     *   lihat [DeviceId]/[com.aether.x.core.security.DeviceFingerprint],
     *   BUKAN lagi ANDROID_ID mentah)
     * - `userId`: string 8 karakter hasil generate acak
     * - `firstLoginAt` / `lastLoginAt`: timestamp server
     * - `licenseActive`: false (default; diperbarui terpisah oleh
     *   [LicenseRepository] begitu ada lisensi yang berhasil diaktivasi)
     * - `licenseExpiresAt`: null (idem)
     *
     * Ini memenuhi rule `create` di firestore.rules yang mensyaratkan
     * `deviceId`, `firstLoginAt`, `lastLoginAt`, dan `userId` wajib ada
     * sekaligus saat dokumen pertama kali dibuat.
     */
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
                    // Dokumen device sudah ada tapi tanpa userId (mis. dibuat versi
                    // app lama) — lengkapi dengan userId baru tanpa menyentuh
                    // firstLoginAt yang sudah ada (rules melarang field ini berubah).
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
