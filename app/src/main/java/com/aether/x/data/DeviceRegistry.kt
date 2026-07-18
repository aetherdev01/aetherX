package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Mendata setiap perangkat yang membuka aplikasi ke Firestore, dikunci per
 * `ANDROID_ID` (Settings.Secure.ANDROID_ID) sebagai document ID di koleksi
 * `devices`. Perlu dicatat sifat identifier ini:
 *
 * - ANDROID_ID unik per kombinasi app-signing key + akun pengguna + perangkat,
 *   BUKAN serial number/IMEI (yang aksesnya sudah dibatasi ketat sejak
 *   Android 10 dan dilarang dipakai untuk analytics/tracking oleh kebijakan
 *   Google Play).
 * - Nilainya di-reset kalau aplikasi di-uninstall lalu dipasang ulang (sejak
 *   Android 8), jadi ini BUKAN identifier permanen yang tidak bisa direset
 *   pengguna.
 * - Tidak butuh permission khusus untuk dibaca.
 *
 * Field `firstLoginAt` sengaja ditulis dengan `FieldValue.serverTimestamp()`
 * dan hanya dipasang lewat `SetOptions.merge()` — kalau field itu sudah ada
 * di dokumen, Firestore tidak menimpanya lagi kecuali kita override manual.
 * Untuk memastikan hanya tertulis SEKALI (login pertama yang sesungguhnya,
 * bukan setiap kali app dibuka), kita cek dulu keberadaan dokumennya sebelum
 * memutuskan apakah field ini perlu disertakan.
 */
class DeviceRegistry(private val context: Context) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private companion object {
        const val TAG = "DeviceRegistry"
        const val COLLECTION = "devices"
    }

    val deviceId: String
        get() = DeviceId.read(context)

    /**
     * Menulis/memperbarui dokumen perangkat ini di Firestore. Dipanggil sekali
     * per sesi aplikasi (mis. dari [TweakViewModel] saat pertama kali dibuka).
     * Gagal secara diam-diam (dicatat ke Logcat) supaya tidak pernah memblokir
     * alur utama aplikasi — pendataan ini bersifat best-effort.
     *
     * Bentuk data sengaja dipisah antara dokumen BARU vs yang SUDAH ADA supaya
     * cocok dengan Firestore Security Rules (lihat firestore.rules):
     * - create: `deviceId`, `firstLoginAt`, `lastLoginAt`, DAN `userId` wajib
     *   dikirim sekaligus (`userId` harus berupa String 8 karakter — rules
     *   menolak kalau tidak ada), timestamp keduanya berupa
     *   `FieldValue.serverTimestamp()`.
     * - update: `deviceId` dan `firstLoginAt` TIDAK dikirim ulang (rules
     *   menolak kalau nilainya berubah), hanya `lastLoginAt` dan `userId`.
     *
     * @param userId ID pengguna (string 8 karakter) yang SUDAH teresolusi
     *   (lihat [UserIdRepository.resolveUserId]) — wajib non-null karena
     *   rules `create` mensyaratkan field ini ada. Panggil fungsi ini
     *   setelah `resolveUserId()` selesai, bukan sebelumnya.
     */
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
