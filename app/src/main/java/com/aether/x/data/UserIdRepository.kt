package com.aether.x.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sumber ID pengguna GLOBAL yang sebenarnya: setiap install baru mengambil
 * angka urut berikutnya dari satu dokumen counter di Firestore (`meta/user_counter`,
 * field `count`), sehingga nomornya benar-benar mencerminkan urutan/jumlah
 * total pengguna. Dokumen perangkat (`devices/{deviceId}`) sekarang berfungsi
 * ganda sebagai "kartu identitas" device ini di Firestore — berisi `userId`,
 * `deviceId`, DAN status lisensi (`licenseActive`, `licenseExpiresAt`) supaya
 * semua informasi tentang satu device ada di satu dokumen yang gampang dicek
 * lewat Firebase Console.
 *
 * SENGAJA TIDAK ADA fallback angka acak. ID pengguna dipakai sebagai
 * identitas yang ditampilkan ke pengguna — angka acak yang "keliru dikira
 * asli" lebih berbahaya daripada sekadar tidak menampilkan apa pun sementara.
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
 * `userId` disimpan) ikut terhapus saat app di-uninstall, padahal
 * `ANDROID_ID` device pada umumnya tetap sama (lihat catatan di [DeviceId]).
 * Sebelum mengalokasikan nomor BARU dari counter, [resolveUserId] sekarang
 * cek dulu apakah device ini SUDAH punya dokumen di koleksi `devices` (dari
 * sesi sebelum uninstall) — kalau ada dan field `userId`-nya masih tersimpan
 * di sana, angka itu yang dipakai lagi (badge "ID-…" balik ke nomor yang
 * sama, bukan nomor baru atau kosong). Baru kalau device ini benar-benar
 * belum pernah tercatat, counter global dinaikkan DAN dokumen `devices/{id}`
 * dibuat dalam SATU transaksi atomik yang sama (lihat [allocateAndRegister]) —
 * sebelumnya ini dua transaksi terpisah (naikkan counter, baru BELAKANGAN
 * tulis dokumen device dari pemanggil lain), yang berarti kalau step kedua
 * gagal/tidak sempat terpanggil, counter sudah kadung naik tapi device-nya
 * tidak pernah tercatat dan badge ID pengguna macet permanen di
 * "Menyambungkan…" walau sebenarnya sudah dapat nomor.
 */
class UserIdRepository(private val preferences: AetherXPreferences, private val deviceId: String) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val counterRef by lazy { firestore.collection("meta").document("user_counter") }
    private val deviceRef by lazy { firestore.collection("devices").document(deviceId) }

    private companion object {
        const val TAG = "UserIdRepository"
        const val MAX_ATTEMPTS = 4
        const val INITIAL_BACKOFF_MILLIS = 1_000L
    }

    /**
     * Mengembalikan ID pengguna asli, atau `null` kalau setelah [MAX_ATTEMPTS]
     * percobaan (dengan backoff) tetap gagal menghubungi Firestore — TIDAK
     * PERNAH mengembalikan angka acak. Pemanggil (mis. [TweakViewModel])
     * bebas memanggil ulang fungsi ini lagi nanti (mis. saat koneksi pulih)
     * untuk mencoba lagi; setiap panggilan yang gagal tidak meninggalkan efek
     * samping yang perlu dibersihkan.
     */
    suspend fun resolveUserId(): Int? {
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

        Log.w(TAG, "Gagal resolusi ID pengguna setelah $MAX_ATTEMPTS percobaan — tidak memakai fallback acak.")
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
                "Firestore rules menolak baca/tulis ke devices/{id} atau meta/user_counter — cek tab Rules di Firebase Console, pastikan sudah di-deploy ke project yang benar."
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
     * pakai lagi nomor itu supaya konsisten setelah install ulang. Hanya kalau
     * device ini benar-benar baru (dokumen belum ada / belum punya `userId`)
     * baru minta nomor baru + catat device dalam satu transaksi atomik.
     */
    private suspend fun resolveExistingOrAllocate(): Int {
        val existing = fetchExistingUserId()
        if (existing != null) return existing
        return allocateAndRegister()
    }

    private suspend fun fetchExistingUserId(): Int? = suspendCancellableCoroutine { cont ->
        deviceRef.get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.getLong("userId")?.toInt()
                if (cont.isActive) cont.resume(existing)
            }
            .addOnFailureListener { error ->
                // Gagal baca (mis. offline) bukan berarti device belum pernah
                // terdaftar — jangan diam-diam alokasikan nomor baru di sini,
                // biarkan resolveUserId() yang retry lewat backoff di atas.
                if (cont.isActive) cont.resumeWithException(error)
            }
    }

    /**
     * Menaikkan counter global DAN membuat/memperbarui dokumen `devices/{deviceId}`
     * dalam SATU transaksi Firestore — keduanya sukses bersamaan atau
     * keduanya gagal bersamaan, tidak pernah setengah jalan seperti sebelumnya.
     *
     * Dokumen `devices/{deviceId}` yang ditulis di sini sekarang berisi:
     * - `deviceId`: sama dengan document ID (hash fingerprint device —
     *   lihat [DeviceId]/[com.aether.x.core.security.DeviceFingerprint],
     *   BUKAN lagi ANDROID_ID mentah)
     * - `userId`: nomor urut hasil alokasi
     * - `firstLoginAt` / `lastLoginAt`: timestamp server
     * - `licenseActive`: false (default; diperbarui terpisah oleh
     *   [LicenseRepository] begitu ada lisensi yang berhasil diaktivasi)
     * - `licenseExpiresAt`: null (idem)
     *
     * Ini memenuhi rule `create` di firestore.rules yang mensyaratkan
     * `deviceId`, `firstLoginAt`, `lastLoginAt`, dan `userId` wajib ada
     * sekaligus saat dokumen pertama kali dibuat.
     */
    private suspend fun allocateAndRegister(): Int = suspendCancellableCoroutine { cont ->
        firestore.runTransaction { txn ->
            val counterSnapshot = txn.get(counterRef)
            val current = counterSnapshot.getLong("count") ?: 0L
            val next = current + 1

            val deviceSnapshot = txn.get(deviceRef)
            val now = FieldValue.serverTimestamp()

            if (!deviceSnapshot.exists()) {
                txn.set(
                    deviceRef,
                    mapOf(
                        "deviceId" to deviceId,
                        "userId" to next,
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
                        "userId" to next,
                        "lastLoginAt" to now,
                    ),
                )
            }

            // txn.set (bukan update) supaya percobaan PERTAMA kali dokumen
            // meta/user_counter belum pernah ada sama sekali tetap berhasil
            // dibuat (Firestore rules memperlakukan ini sebagai "create").
            // Transaksi ini otomatis di-retry oleh SDK kalau ada tabrakan
            // (dua device mendaftar bersamaan) — retry membaca ulang
            // counterSnapshot & deviceSnapshot dari awal fungsi lambda ini,
            // jadi `next` selalu dihitung dari nilai TERBARU, tidak pernah stale.
            txn.set(counterRef, mapOf("count" to next))
            next
        }.addOnSuccessListener { next ->
            if (cont.isActive) cont.resume(next.toInt())
        }.addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
    }
}
