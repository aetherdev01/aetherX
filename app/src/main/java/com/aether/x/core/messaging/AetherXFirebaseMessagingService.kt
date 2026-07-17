package com.aether.x.core.messaging

import android.util.Log
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.data.FcmTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Titik masuk penerimaan push Firebase Cloud Messaging (FCM) — BERBEDA dari
 * [com.aether.x.data.MaintenanceRepository]/[com.aether.x.data.UpdateRepository]/
 * [com.aether.x.data.LicenseRepository] yang memantau Firestore lewat
 * `addSnapshotListener` dan HANYA jalan selagi aplikasi punya proses hidup
 * (foreground/background dengan proses masih ada). Service ini dibangunkan
 * oleh sistem Android sendiri (lewat Google Play Services) SETIAP KALI ada
 * pesan FCM masuk, TERMASUK saat aplikasi sudah ditutup total (swipe dari
 * recent apps) — itulah kenapa notifikasi maintenance/update/membership
 * tetap sampai walau aplikasi tidak sedang dipakai.
 *
 * Didaftarkan di AndroidManifest.xml dengan intent-filter
 * `com.google.firebase.MESSAGING_EVENT`. TIDAK perlu request permission
 * apa pun tambahan di sini di luar `POST_NOTIFICATIONS` yang sudah dipegang
 * [AetherXNotifier] secara terpusat.
 *
 * Format pesan yang DIHARAPKAN datang dari sisi bot (lihat lib/fcmClient.js):
 * pesan dikirim sebagai **data message murni** (bukan notification message
 * bawaan FCM) — field `data` berisi:
 * ```
 * kind: "maintenance" | "update" | "membership" | "general"
 * title: string
 * text: string
 * bigText: string (opsional, kalau kosong dianggap sama dengan text)
 * ```
 * Data message (bukan notification message) dipilih dengan SENGAJA supaya
 * [onMessageReceived] SELALU dipanggil di sisi klien apa pun kondisi
 * aplikasinya (foreground/background/killed) — notification message bawaan
 * FCM sebaliknya, kalau aplikasi sedang background/killed, akan langsung
 * ditampilkan otomatis oleh sistem TANPA pernah memanggil
 * [onMessageReceived] sama sekali, sehingga channel/styling
 * kustom dari [AetherXNotifier] tidak akan pernah dipakai untuk kasus itu.
 */
class AetherXFirebaseMessagingService : FirebaseMessagingService() {

    // Scope umur-pendek khusus untuk sinkronisasi token — sengaja BUKAN
    // scope yang mengikuti lifecycle Activity/ViewModel manapun karena
    // service ini bisa dibangunkan sistem tanpa ada Activity yang hidup
    // sama sekali. SupervisorJob supaya kegagalan satu coroutine (mis. sync
    // token gagal karena offline) tidak membatalkan scope untuk pesan
    // berikutnya.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Dipanggil setiap kali sistem mengalokasikan/memperbarui token FCM
     * untuk install ini (mis. pertama kali app dipasang, atau token
     * di-rotasi otomatis oleh sistem/Google Play Services). Token BARU
     * langsung disinkronkan ke Firestore di sini — TIDAK menunggu app
     * dibuka lagi — supaya bot admin selalu punya token per-device yang
     * terbaru untuk kasus kirim push ke satu device tertentu (lihat KDoc
     * [FcmTokenRepository]).
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenRepository.syncTokenToFirestore(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val kind = data["kind"].orEmpty()
        // Fallback ke field `notification` bawaan FCM kalau bot suatu saat
        // mengirim notification message alih-alih data message murni (mis.
        // saat testing manual lewat Firebase Console "Compose notification",
        // yang SELALU berupa notification message, bukan data message) —
        // supaya pesan tetap tampil dengan title/body apa adanya walau tanpa
        // styling channel kustom, alih-alih diam-diam hilang.
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title.orEmpty()
        val text = data["text"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body.orEmpty()
        val bigText = data["bigText"]?.takeIf { it.isNotBlank() }

        if (title.isBlank() && text.isBlank()) {
            Log.w(TAG, "Pesan FCM diterima tanpa title maupun text yang bisa ditampilkan, diabaikan.")
            return
        }

        val notificationKind = when (kind) {
            "maintenance" -> AetherXNotifier.NotificationKind.MAINTENANCE
            "update" -> AetherXNotifier.NotificationKind.UPDATE
            "membership" -> AetherXNotifier.NotificationKind.MEMBERSHIP
            else -> AetherXNotifier.NotificationKind.GENERAL
        }

        AetherXNotifier.notify(
            context = applicationContext,
            kind = notificationKind,
            title = title,
            text = text,
            bigText = bigText,
        )
    }

    private companion object {
        const val TAG = "AetherXFcmService"
    }
}
