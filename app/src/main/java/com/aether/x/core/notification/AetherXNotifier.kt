package com.aether.x.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aether.x.MainActivity
import com.aether.x.R

/**
 * Helper notifikasi SISTEM terpusat (FITUR BARU — lihat perintah rework:
 * "tambahkan notifikasi di semua fitur dari update, maintenance dan lain
 * lain") untuk alert yang ditampilkan lewat notification tray Android biasa
 * — BERBEDA dari notifikasi foreground service yang sudah ada di
 * [com.aether.x.core.overlay.CrosshairOverlayService],
 * [com.aether.x.core.overlay.FpsMonitorOverlayService], dan
 * [com.aether.x.core.monitor.GameProfileMonitorService] (yang notifikasinya
 * WAJIB ada selama service hidup dan tidak merepresentasikan "kejadian",
 * cuma indikator "service sedang jalan").
 *
 * Notifier ini dipakai untuk KEJADIAN yang perlu diketahui pengguna meski
 * aplikasi sedang di background atau ditutup total: versi baru tersedia,
 * jendela maintenance server dimulai, dan event lain di masa depan (mis.
 * lisensi akan kedaluwarsa). Setiap jenis notifikasi punya channel
 * Android-nya sendiri (lihat [NotificationKind]) supaya pengguna bisa
 * menonaktifkan salah satu jenis lewat pengaturan sistem tanpa mematikan
 * yang lain — mis. mematikan notifikasi maintenance tapi tetap menerima
 * notifikasi update.
 *
 * SEMUA pemanggilan [notify] aman dipanggil dari Composable (lewat
 * `LocalContext.current`) berkat pengecekan [Manifest.permission.POST_NOTIFICATIONS]
 * internal (wajib diminta terpisah di Android 13/API 33 ke atas) — kalau
 * permission belum diberikan, [notify] diam-diam tidak melakukan apa pun
 * alih-alih throw [SecurityException].
 */
object AetherXNotifier {

    enum class NotificationKind(
        val channelId: String,
        val channelNameRes: Int,
        val channelDescRes: Int,
        val importance: Int,
        val notificationId: Int,
    ) {
        UPDATE(
            channelId = "aetherx_update_alerts",
            channelNameRes = R.string.notif_channel_update_name,
            channelDescRes = R.string.notif_channel_update_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1001,
        ),
        MAINTENANCE(
            channelId = "aetherx_maintenance_alerts",
            channelNameRes = R.string.notif_channel_maintenance_name,
            channelDescRes = R.string.notif_channel_maintenance_desc,
            importance = NotificationManager.IMPORTANCE_HIGH,
            notificationId = 1002,
        ),
        GENERAL(
            channelId = "aetherx_general_alerts",
            channelNameRes = R.string.notif_channel_general_name,
            channelDescRes = R.string.notif_channel_general_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1003,
        ),
    }

    /**
     * Buat (kalau belum ada) channel notifikasi untuk [kind]. Aman dipanggil
     * berulang kali — [NotificationManager.createNotificationChannel] no-op
     * kalau channel dengan ID yang sama sudah ada. Hanya berlaku Android O+
     * (API 26); di bawah itu channel tidak ada konsepnya sama sekali.
     */
    private fun ensureChannel(context: Context, kind: NotificationKind) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            kind.channelId,
            context.getString(kind.channelNameRes),
            kind.importance,
        ).apply {
            description = context.getString(kind.channelDescRes)
        }
        manager.createNotificationChannel(channel)
    }

    /** True kalau aplikasi punya izin memunculkan notifikasi di keadaan sekarang. */
    private fun hasNotificationPermission(context: Context): Boolean {
        // Di bawah Android 13 (API 33), notifikasi tidak butuh permission
        // runtime — POST_NOTIFICATIONS baru diperkenalkan di API 33.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Tampilkan notifikasi [kind] dengan [title]/[text]. Mengetuk notifikasi
     * membuka [MainActivity] (perilaku default semua notifikasi AetherX —
     * belum ada kebutuhan deep-link ke tab tertentu, jadi cukup buka
     * aplikasi dan biarkan pengguna navigasi sendiri).
     *
     * TIDAK melempar exception kalau permission belum diberikan — cukup
     * tidak melakukan apa pun. Halaman yang memanggil TIDAK PERLU membungkus
     * pemanggilan ini dengan try/catch atau pengecekan permission sendiri.
     */
    fun notify(
        context: Context,
        kind: NotificationKind,
        title: String,
        text: String,
        bigText: String? = null,
        ongoing: Boolean = false,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context, kind)

        val contentIntent = PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_aetherx_mark)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setContentIntent(contentIntent)
            .setPriority(importanceToPriority(kind.importance))

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        val notification: Notification = builder.build()
        NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
    }

    /** Batalkan notifikasi [kind] yang sedang tampil (kalau ada). */
    fun cancel(context: Context, kind: NotificationKind) {
        NotificationManagerCompat.from(context).cancel(kind.notificationId)
    }

    private fun importanceToPriority(importance: Int): Int = when (importance) {
        NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
        NotificationManager.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
        NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
}
