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
            // REWORK (lihat perintah rework — "perbaiki notifikasi tidak
            // pernah muncul mengambang dan senyap"): dinaikkan dari
            // IMPORTANCE_DEFAULT ke IMPORTANCE_HIGH — di Android 8+ (API
            // 26+), HANYA channel dengan importance HIGH yang ditampilkan
            // sebagai heads-up (mengambang di atas layar) DAN otomatis
            // bunyi/getar; IMPORTANCE_DEFAULT hanya muncul senyap di status
            // bar tanpa bunyi/mengambang sama sekali — inilah AKAR MASALAH
            // "notifikasi tidak pernah muncul mengambang dan senyap".
            importance = NotificationManager.IMPORTANCE_HIGH,
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
            // Sama seperti UPDATE — dinaikkan ke HIGH, lihat KDoc di atas.
            importance = NotificationManager.IMPORTANCE_HIGH,
            notificationId = 1003,
        ),

        /**
         * FITUR BARU (lihat perintah rework — "perbaiki notifikasi ...
         * setiap aktifkan fitur, monitor dll"): konfirmasi heads-up
         * singkat setiap kali pengguna MENGAKTIFKAN/MENONAKTIFKAN sebuah
         * fitur (Crosshair, FPS Monitor, Game Profile Monitor, dll) —
         * SEBELUMNYA tidak ada notifikasi APA PUN untuk kejadian ini sama
         * sekali (lihat [notifyFeatureToggled], titik panggil baru dari
         * layar Tweak/Settings).
         */
        FEATURE_TOGGLE(
            channelId = "aetherx_feature_toggle_alerts",
            channelNameRes = R.string.notif_channel_feature_toggle_name,
            channelDescRes = R.string.notif_channel_feature_toggle_desc,
            importance = NotificationManager.IMPORTANCE_HIGH,
            notificationId = 1004,
        ),
    }

    /**
     * Buat (kalau belum ada) channel notifikasi untuk [kind]. Aman dipanggil
     * berulang kali — [NotificationManager.createNotificationChannel] no-op
     * kalau channel dengan ID yang sama sudah ada (TERMASUK properti
     * getar/suara di dalamnya — sekali dibuat, mengubah [ensureChannel]
     * lagi TIDAK akan mengubah channel yang sudah ada di device pengguna;
     * kalau perlu redefinisi properti channel untuk pengguna existing,
     * channel ID harus diganti). Hanya berlaku Android O+ (API 26); di
     * bawah itu channel tidak ada konsepnya sama sekali (properti getar/
     * suara di [notify] lewat [NotificationCompat.Builder.setDefaults]
     * tetap berlaku untuk API <26).
     *
     * REWORK (lihat perintah rework — "perbaiki notifikasi ... senyap"):
     * [NotificationChannel.enableVibration] dan [NotificationChannel.setSound]
     * SEKARANG di-set EKSPLISIT — importance HIGH SEHARUSNYA sudah cukup
     * untuk getar+suara otomatis menurut dokumentasi Android, TAPI
     * beberapa skin OEM (mis. sebagian ROM MIUI/ColorOS versi tertentu)
     * diketahui butuh properti ini diset eksplisit di channel, tidak cukup
     * hanya mengandalkan importance level saja.
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
            enableVibration(true)
            vibrationPattern = DEFAULT_VIBRATION_PATTERN
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setShowBadge(true)
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
            // REWORK (lihat perintah rework — "perbaiki notifikasi ...
            // senyap"): CATEGORY_EVENT membantu beberapa OEM launcher/ROM
            // memprioritaskan notifikasi ini untuk tampil heads-up alih-alih
            // langsung diam di status bar. setDefaults + setVibrate di sini
            // adalah FALLBACK untuk Android <8 (API <26) yang tidak punya
            // konsep NotificationChannel sama sekali (properti getar/suara
            // channel di ensureChannel tidak berlaku di API itu) — untuk
            // API 26+, properti channel di ensureChannel yang menentukan,
            // tapi memanggil setDefaults/setVibrate di sini tetap aman
            // (diabaikan sistem kalau channel API 26+ sudah override-nya).
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(DEFAULT_VIBRATION_PATTERN)

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        val notification: Notification = builder.build()
        NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
    }

    /**
     * FITUR BARU (lihat perintah rework — "perbaiki notifikasi ... setiap
     * aktifkan fitur, monitor dll"): tampilkan konfirmasi heads-up singkat
     * setiap kali sebuah fitur DIAKTIFKAN/DINONAKTIFKAN — SEBELUMNYA toggle
     * Crosshair/FPS Monitor/dll TIDAK memicu notifikasi apa pun sama sekali.
     * Dipakai dari layar Tweak/Settings tepat setelah state toggle berhasil
     * disimpan ke preferences (bukan optimistic sebelum tersimpan).
     *
     * [featureName] contoh: "Crosshair Overlay", "FPS Monitor", "Game
     * Profile Monitor" — nama fitur apa adanya, dipakai sebagai judul.
     */
    fun notifyFeatureToggled(context: Context, featureName: String, enabled: Boolean) {
        notify(
            context = context,
            kind = NotificationKind.FEATURE_TOGGLE,
            title = featureName,
            text = context.getString(
                if (enabled) R.string.notif_feature_toggle_enabled else R.string.notif_feature_toggle_disabled,
            ),
        )
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

    // Pola getar pendek "buzz-jeda-buzz" (delay, buzz, pause, buzz) dalam
    // milidetik — dipakai baik oleh channel (ensureChannel, API 26+) maupun
    // builder (notify, fallback API <26).
    private val DEFAULT_VIBRATION_PATTERN = longArrayOf(0, 200, 100, 200)
}
