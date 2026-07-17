package com.aether.x.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Mengelola sisi Android dari integrasi Firebase Cloud Messaging (FCM) —
 * lihat juga [com.aether.x.core.messaging.AetherXFirebaseMessagingService]
 * untuk sisi PENERIMA notifikasinya.
 *
 * Ada dua mekanisme pengiriman berbeda yang SENGAJA keduanya dipakai (bukan
 * saling menggantikan):
 *
 * 1. **Topic broadcast** (dipakai repository ini): device otomatis
 *    subscribe ke topic `maintenance`, `update`, dan `membership` sekali
 *    saat app pertama kali start (lihat [subscribeToDefaultTopics]) — bot
 *    Telegram sisi admin cukup kirim SATU notifikasi FCM ke topic tsb dan
 *    otomatis sampai ke SEMUA device yang subscribe, tanpa admin perlu tahu
 *    daftar token device satu-satu. Ini yang dipakai untuk notifikasi
 *    maintenance/update yang sifatnya broadcast ke semua pengguna.
 *
 * 2. **Token per-device** (disimpan lewat [syncTokenToFirestore] ke
 *    `devices/{deviceId}.fcmToken`): dipakai untuk kasus admin ingin kirim
 *    push ke SATU device tertentu saja (mis. peringatan lisensi device
 *    tertentu akan kedaluwarsa) — bot bisa query token dari dokumen device
 *    itu di Firestore lalu kirim FCM target token tunggal, bukan topic.
 *
 * Token FCM BUKAN rahasia yang perlu diproteksi ketat seperti kunci lisensi
 * (token ini cuma alamat pengiriman push, tidak bisa dipakai untuk
 * membaca/mengubah data), jadi disimpan sebagai field biasa di dokumen
 * `devices/{deviceId}` yang sudah ada (sama seperti `licenseActive` dsb),
 * BUKAN koleksi terpisah.
 */
object FcmTokenRepository {

    private const val TAG = "FcmTokenRepository"

    /**
     * Topic yang di-subscribe SEMUA install AetherX secara default — nama
     * topic ini HARUS sama persis dengan yang dipakai sisi bot Telegram
     * (lihat lib/fcmClient.js, fungsi sendToTopic) saat mengirim notifikasi
     * maintenance/update/membership massal.
     */
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

    /**
     * Subscribe device ini ke semua topic broadcast default. Aman dipanggil
     * berulang kali (mis. tiap [com.aether.x.AetherXApp.onCreate]) —
     * subscribe ke topic yang sudah pernah di-subscribe sebelumnya adalah
     * no-op di sisi FCM, tidak menghasilkan subscription duplikat maupun
     * efek samping lain. Best-effort: kegagalan (mis. offline saat app
     * pertama dibuka) hanya di-log, TIDAK melempar exception ke pemanggil —
     * FCM SDK otomatis mencoba lagi sendiri di background saat konektivitas
     * pulih, jadi tidak perlu retry manual di sini.
     */
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
     * Ambil token FCM saat ini (dari sistem, kalau [token] tidak diisi) lalu
     * simpan ke `devices/{deviceId}.fcmToken` di Firestore.
     *
     * Dipanggil sekali saat app start ([com.aether.x.AetherXApp.onCreate])
     * DAN setiap kali token berganti (lihat
     * [com.aether.x.core.messaging.AetherXFirebaseMessagingService.onNewToken])
     * — token FCM bisa berubah sewaktu-waktu (reinstall, clear data Google
     * Play Services, rotasi token berkala oleh sistem), jadi tidak cukup
     * hanya disimpan sekali di awal saja.
     *
     * Sengaja pakai `update` (BUKAN `set(merge=true)`), sama pola dengan
     * [LicenseRepository.recordLicenseStatus]: dokumen `devices/{deviceId}`
     * seharusnya sudah dibuat lebih dulu oleh [UserIdRepository] (yang
     * mengisi field wajib `deviceId`/`firstLoginAt`/`userId` sesuai
     * firestore.rules saat CREATE). Kalau dipaksa `set(merge=true)` padahal
     * dokumennya belum ada sama sekali, Firestore memperlakukannya sebagai
     * operasi CREATE BARU yang isinya cuma `fcmToken` — field wajib yang
     * disyaratkan rules jadi tidak lengkap dan kemungkinan besar DITOLAK
     * (PERMISSION_DENIED). Kalau dokumennya memang belum ada (mis. token FCM
     * datang duluan sebelum tab mana pun sempat memicu
     * [UserIdRepository.resolveUserId]), `update` di sini gagal dengan
     * NOT_FOUND — di-log lalu dilewati begitu saja (best-effort, TIDAK
     * pernah dilempar sebagai error yang mengganggu alur startup app); token
     * akan tersinkron belakangan lewat pemanggilan berikutnya.
     */
    suspend fun syncTokenToFirestore(context: Context, token: String? = null) {
        runCatching {
            val fcmToken = token ?: resolveCurrentToken()
            if (fcmToken != null) {
                val deviceId = DeviceId.read(context)
                val firestore = FirebaseFirestore.getInstance()
                updateDeviceDocument(firestore, deviceId, fcmToken)
            } else {
                Log.w(TAG, "Tidak berhasil mengambil token FCM saat ini, sinkronisasi dilewati.")
            }
        }.onFailure { e ->
            Log.w(TAG, "Gagal menyinkronkan token FCM ke Firestore", e)
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
    ): Unit = suspendCancellableCoroutine { cont ->
        firestore.collection("devices").document(deviceId)
            .update(
                mapOf(
                    "fcmToken" to fcmToken,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { e ->
                Log.w(
                    TAG,
                    "Gagal update fcmToken ke devices/$deviceId (dokumen mungkin belum dibuat UserIdRepository)",
                    e,
                )
                if (cont.isActive) cont.resume(Unit)
            }
    }
}
