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
            // FIX (permintaan "notifikasi tidak perlu mengambang, cukup ada
            // suaranya"): diturunkan dari IMPORTANCE_HIGH ke
            // IMPORTANCE_DEFAULT. Di Android 8+ (API 26+), HANYA channel
            // IMPORTANCE_HIGH yang ditampilkan sebagai heads-up (mengambang
            // di atas layar) — IMPORTANCE_DEFAULT tetap muncul normal di
            // status bar & notification tray DAN tetap bunyi/getar (lihat
            // enableVibration/setSound eksplisit di ensureChannel di bawah),
            // hanya TIDAK muncul sebagai pop-up mengambang.
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1001,
        ),
        MAINTENANCE(
            channelId = "aetherx_maintenance_alerts",
            channelNameRes = R.string.notif_channel_maintenance_name,
            channelDescRes = R.string.notif_channel_maintenance_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1002,
        ),
        GENERAL(
            channelId = "aetherx_general_alerts",
            channelNameRes = R.string.notif_channel_general_name,
            channelDescRes = R.string.notif_channel_general_desc,
            // Sama seperti UPDATE — diturunkan ke DEFAULT, lihat KDoc di atas.
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = 1003,
        ),

        /**
         * FITUR BARU (lihat perintah rework — "perbaiki notifikasi ...
         * setiap aktifkan fitur, monitor dll"): konfirmasi singkat setiap
         * kali pengguna MENGAKTIFKAN/MENONAKTIFKAN sebuah fitur (Crosshair,
         * FPS Monitor, Game Profile Monitor, dll) — SEBELUMNYA tidak ada
         * notifikasi APA PUN untuk kejadian ini sama sekali (lihat
         * [notifyFeatureToggled], titik panggil baru dari layar
         * Tweak/Settings).
         */
        FEATURE_TOGGLE(
            channelId = "aetherx_feature_toggle_alerts",
            channelNameRes = R.string.notif_channel_feature_toggle_name,
            channelDescRes = R.string.notif_channel_feature_toggle_desc,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
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
     * REWORK (permintaan "notifikasi tidak perlu mengambang, cukup ada
     * suaranya"): [NotificationChannel.enableVibration] dan
     * [NotificationChannel.setSound] tetap di-set EKSPLISIT walau importance
     * sekarang DEFAULT (bukan lagi HIGH) — getar & suara TETAP jalan normal
     * di importance DEFAULT, cuma heads-up (mengambang) yang hilang karena
     * itu eksklusif untuk importance HIGH. Beberapa skin OEM (mis. sebagian
     * ROM MIUI/ColorOS versi tertentu) diketahui butuh properti ini diset
     * eksplisit di channel, tidak cukup hanya mengandalkan importance level
     * saja.
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
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setContentIntent(contentIntent)
            .setPriority(importanceToPriority(kind.importance))
            // FIX (permintaan "notifikasi tidak perlu mengambang, cukup ada
            // suaranya"): CATEGORY_EVENT DIHAPUS — kategori itu sebelumnya
            // dipakai untuk mendorong sebagian OEM launcher/ROM menampilkan
            // notifikasi ini sebagai heads-up, yang sekarang justru
            // berlawanan dengan tujuan (importance sudah DEFAULT, bukan
            // HIGH, supaya tidak mengambang). setDefaults + setVibrate tetap
            // dipertahankan sebagai FALLBACK bunyi/getar untuk Android <8
            // (API <26) yang tidak punya konsep NotificationChannel sama
            // sekali (properti getar/suara channel di ensureChannel tidak
            // berlaku di API itu) — untuk API 26+, properti channel di
            // ensureChannel yang menentukan bunyi/getar, tapi setDefaults/
            // setVibrate di sini tetap aman dipanggil (diabaikan sistem
            // kalau channel API 26+ sudah override-nya).
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
