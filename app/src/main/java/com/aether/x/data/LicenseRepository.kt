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

/**
 * Hasil pemeriksaan/aktivasi lisensi. UI (lihat MembershipViewModel/MembershipScreen di tab
 * Settings) memetakan tiap varian ini ke pesan dan aksi yang sesuai.
 */
sealed interface LicenseResult {
    /** Lisensi valid untuk device ini, dengan `expiresAtMillis` kapan dia kadaluarsa. */
    data class Valid(val expiresAtMillis: Long) : LicenseResult

    /** Kode lisensi tidak ditemukan di Firestore sama sekali. */
    data object NotFound : LicenseResult

    /** Kode ditemukan tapi statusnya `revoked` (dicabut admin). */
    data object Revoked : LicenseResult

    /** Kode sudah pernah dipakai, tapi oleh device LAIN (bukan device ini). */
    data object BoundToOtherDevice : LicenseResult

    /** Kode ditemukan dan cocok device-nya, tapi `expiresAt` sudah lewat. */
    data class Expired(val expiredAtMillis: Long) : LicenseResult

    /** Gagal menghubungi Firestore (offline, dsb) — bukan berarti kode salah. */
    data object NetworkError : LicenseResult
}

/**
 * Mengelola lisensi berbasis kode manual (dibuat admin lewat Firebase
 * Console) yang terkunci ke SATU `ANDROID_ID` device — begitu sebuah kode
 * berhasil dipakai oleh device A, device B tidak akan pernah bisa memakai
 * kode yang sama (lihat firestore.rules untuk penegakan sisi server).
 *
 * Skema dokumen di koleksi `licenses/{key}`:
 * ```
 * key: string            // sama dengan document ID
 * deviceId: string|null  // null = belum dipakai siapa pun
 * status: "unused" | "active" | "revoked"
 * activatedAt: timestamp|null
 * expiresAt: timestamp
 * createdAt: timestamp
 * ```
 *
 * Admin bertanggung jawab membuat dokumen `licenses/{key}` secara manual
 * (mis. lewat Firebase Console) dengan `status = "unused"`, `deviceId = null`,
 * dan `expiresAt` diisi sesuai masa berlaku yang dijual/diberikan.
 */
class LicenseRepository(private val context: Context) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val deviceId: String get() = DeviceId.read(context)
    private val deviceRef by lazy { firestore.collection("devices").document(deviceId) }

    private companion object {
        const val COLLECTION = "licenses"
    }

    /**
     * Mencoba mengaktivasi [key] untuk device ini. Kalau kode belum pernah
     * dipakai, kode langsung dikunci ke device ini lewat transaksi atomik
     * (mencegah race condition kalau dua device mencoba kode yang sama nyaris
     * bersamaan). Kalau kode sudah terkunci ke device ini sebelumnya (mis.
     * app di-uninstall lalu dipasang ulang lalu kode yang sama dimasukkan
     * lagi), ini berlaku sebagai validasi ulang biasa.
     */
    suspend fun activate(key: String): LicenseResult {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return LicenseResult.NotFound

        val result = runCatching { activateTransaction(trimmedKey) }
            .getOrElse { e ->
                if (e is LicenseSignal) return@getOrElse e.result
                if (e is FirebaseFirestoreException) return@getOrElse LicenseResult.NetworkError
                LicenseResult.NetworkError
            }

        // Catat status lisensi ke devices/{deviceId} — best-effort, terpisah
        // dari transaksi licenses/{key} di atas (dua collection berbeda,
        // masing-masing dengan rules sendiri, sengaja TIDAK digabung jadi
        // satu transaksi supaya kegagalan di sini tidak pernah membuat
        // aktivasi lisensi yang sudah sukses jadi ikut gagal/rollback).
        // Kalau ini gagal (mis. offline), status lisensi di device masih
        // benar lewat cache lokal (AetherXPreferences) — dokumen `devices`
        // hanya ringkasan tambahan untuk memudahkan admin lihat di Console.
        if (result is LicenseResult.Valid) {
            recordLicenseStatus(active = true, expiresAtMillis = result.expiresAtMillis)
        }
        return result
    }

    /**
     * Memantau dokumen `licenses/{key}` SECARA REALTIME lewat
     * `addSnapshotListener` (bukan `get()` sekali jalan seperti [revalidate]) —
     * setiap kali admin mengubah status lisensi ini lewat bot Telegram/Firebase
     * Console (mis. revoke, atau perpanjang `expiresAt`), Firestore mendorong
     * perubahan itu ke listener ini dan [LicenseResult] baru langsung di-emit
     * ke Flow tanpa pengguna perlu menutup-buka ulang aplikasi.
     *
     * Dipakai oleh [com.aether.x.ui.membership.MembershipViewModel] sebagai
     * pengganti panggilan [revalidate] satu kali di `init` — status di tab
     * Membership sekarang selalu mengikuti kondisi server saat ini secara
     * langsung (mis. begitu admin klik revoke, badge di tab Membership
     * otomatis berubah dari "Aktif" ke status baru dalam hitungan detik,
     * tanpa refresh manual).
     *
     * `MetadataChanges.EXCLUDE` dipakai supaya listener HANYA emit saat data
     * benar-benar berubah dari server (bukan tiap kali metadata cache/pending
     * write berubah), mengurangi emisi yang tidak perlu.
     */
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

    /**
     * Validasi ulang lisensi yang SUDAH tersimpan sebagai cache lokal (lihat
     * [AetherXPreferences.setLicenseCache]) — dipanggil tiap app dibuka untuk
     * memastikan status di server belum berubah (expired lebih cepat karena
     * diperbarui admin, atau di-revoke). Sekarang dipertahankan sebagai
     * fallback satu-kali (mis. dipanggil manual/pull-to-refresh) — alur utama
     * di tab Membership sudah pindah ke [observe] yang realtime.
     */
    suspend fun revalidate(key: String): LicenseResult {
        val result = runCatching { fetchAndCheck(key) }
            .getOrElse { e ->
                if (e is LicenseSignal) return@getOrElse e.result
                LicenseResult.NetworkError
            }

        // Sinkronkan devices/{deviceId} kalau statusnya berubah sejak
        // terakhir dicek (mis. admin revoke lisensi lewat bot Telegram, atau
        // lisensi kadaluarsa) — supaya dokumen devices tidak menampilkan
        // "aktif" yang sudah basi.
        when (result) {
            is LicenseResult.Valid -> recordLicenseStatus(active = true, expiresAtMillis = result.expiresAtMillis)
            is LicenseResult.Expired,
            LicenseResult.Revoked,
            LicenseResult.BoundToOtherDevice,
            LicenseResult.NotFound -> recordLicenseStatus(active = false, expiresAtMillis = null)
            LicenseResult.NetworkError -> Unit // offline: jangan timpa status terakhir yang diketahui
        }
        return result
    }

    /**
     * Menulis ringkasan status lisensi ke `devices/{deviceId}` (field
     * `licenseActive`, `licenseExpiresAt`) supaya admin bisa langsung lihat
     * status lisensi tiap device dari satu dokumen di Firebase Console,
     * tanpa perlu query silang ke koleksi `licenses`. Best-effort: kegagalan
     * di sini dicatat ke Logcat tapi tidak pernah dilempar sebagai error ke
     * pemanggil (lihat KDoc [activate]).
     *
     * Sengaja hanya memakai `update` (bukan `set(merge=true)`): dokumen
     * `devices/{deviceId}` seharusnya sudah dibuat lebih dulu oleh
     * [UserIdRepository] (yang mengisi field wajib `deviceId`/`firstLoginAt`/
     * `userId` sesuai firestore.rules). Kalau dokumennya belum ada sama
     * sekali (mis. pengguna buka tab Membership sebelum tab Tweak sempat
     * jalan), `update` akan gagal dengan NOT_FOUND — itu di-log lalu
     * dilewati begitu saja, bukan mencoba `set` tanpa field wajib yang pasti
     * ditolak rules juga.
     */
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

    /** Exception internal dipakai untuk keluar cepat dari transaction/fetch dengan hasil tertentu. */
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

            // Cek kadaluarsa SEBELUM mengunci device — kode yang sudah lewat
            // masa berlakunya tidak boleh "menghabiskan" slot device sama sekali,
            // baik itu kode baru maupun yang sebelumnya sudah terkunci ke device ini.
            if (expiresAtMillis == null) throw LicenseSignal(LicenseResult.NetworkError)
            if (expiresAtMillis < System.currentTimeMillis()) {
                throw LicenseSignal(LicenseResult.Expired(expiresAtMillis))
            }

            // Belum pernah dipakai siapa pun DAN belum kadaluarsa: kunci ke device ini sekarang.
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
