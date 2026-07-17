package com.aether.x.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager

/**
 * REWORK — sistem notifikasi Pairing (lihat perintah rework: "bukan pakai
 * floating window/dialog mengambang, tetapi pakai notifikasi sistem dengan
 * notifikasi mengambang").
 *
 * FIX (rework kedua) — dua masalah dari implementasi awal:
 *
 * 1. "saat klik Sambungkan perangkat dengan kode penyambungan itu langsung
 *    muncul lagi notifikasi mengambang" — sebelumnya [baseBuilder] memakai
 *    `setOnlyAlertOnce(true)`, yang membuat notifikasi tahap berikutnya
 *    ([showCodeInput]) dengan NOTIFICATION_ID sama dianggap "update biasa"
 *    oleh sistem dan TIDAK heads-up lagi. Padahal justru momen "Pairing
 *    found" ini yang paling butuh muncul mengambang lagi (kode 6-digit
 *    kedaluwarsa cepat). `setOnlyAlertOnce` sudah DIHAPUS — setiap kali
 *    [AdbConnectionState] berpindah tahap, notifikasi heads-up ulang.
 *
 * 2. "reply nya itu ga perlu ada balas tapi langsung edit text gitu
 *    dibawah title Pairing found" — CATATAN PENTING soal batasan platform:
 *    Android TIDAK menyediakan API apa pun (baik `RemoteInput` standar,
 *    `MessagingStyle`/conversation notification, maupun `RemoteViews`
 *    custom) yang bisa menampilkan kotak ketik langsung terbuka di
 *    notifikasi TANPA satu ketukan pada action reply lebih dulu — ini
 *    berlaku sama persis untuk notifikasi WhatsApp/Telegram/SMS sekalipun,
 *    kotak ketik mereka pun baru terbuka setelah action "Balas" diketuk.
 *    `RemoteViews` custom dengan `EditText` sendiri TIDAK bisa dipakai di
 *    sini karena `RemoteViews` berjalan di proses SystemUI terpisah —
 *    app tidak bisa membaca isi `EditText` di dalamnya kecuali lewat
 *    `RemoteInput` yang tetap butuh action Builder standar. Yang DIUBAH
 *    di [showCodeInput] & [showError]: aksinya sekarang HANYA SATU
 *    (reply kode), diletakkan sebagai action pertama dengan label yang
 *    langsung menunjukkan fungsinya ("Isi kode 6-digit", bukan "Balas"
 *    generik) — supaya begitu notifikasi di-expand, satu-satunya tindakan
 *    yang terlihat memang mengarah ke kotak kode, sedekat mungkin dengan
 *    "langsung terlihat" dalam batas yang diizinkan platform.
 *
 * Menggantikan [com.aether.x.core.overlay.AdbPairingOverlayService]
 * SEPENUHNYA — TIDAK ADA lagi `WindowManager` / `TYPE_APPLICATION_OVERLAY`
 * / izin "Tampil di atas aplikasi lain" yang dipakai untuk alur pairing.
 * Sebagai gantinya dipakai **notifikasi sistem heads-up** biasa (channel
 * `IMPORTANCE_HIGH`, persis mekanisme yang membuat notifikasi WhatsApp/SMS
 * muncul melayang di atas layar), dengan aksi kode 6-digit (`RemoteInput`)
 * langsung tertanam di notifikasi itu sendiri — TANPA perlu membuka
 * AetherX sama sekali, TANPA window overlay, dan TANPA izin
 * SYSTEM_ALERT_WINDOW.
 *
 * Alur (mengikuti urutan [com.aether.x.core.adb.AdbConnectionState] persis
 * seperti overlay sebelumnya):
 *  1. [showSearching] — tombol "Mulai Penyandingan" ditekan -> notifikasi
 *     heads-up "Searching for Pairing…" muncul, dengan aksi "Batalkan".
 *  2. [showCodeInput] — service pairing terdeteksi otomatis via mDNS ->
 *     notifikasi heads-up ULANG berganti jadi "Pairing found" dengan aksi
 *     tunggal "Isi kode 6-digit" (RemoteInput) + aksi "Batalkan".
 *  3. [showBusy] — kode terkirim, notifikasi berganti "Menyandingkan…"
 *     tanpa aksi apa pun (indeterminate, `setOngoing`).
 *  4. [showError] — pairing gagal -> notifikasi heads-up ULANG, kembali ke
 *     mode isi kode dengan pesan error di bawahnya, supaya pengguna bisa
 *     langsung coba lagi dari notifikasi yang sama tanpa mengulang dari
 *     awal.
 *  5. [stop] — dipanggil saat Connected/NotPaired/PairedNotConnected,
 *     notifikasi pairing dibatalkan dari tray.
 *
 * Balasan RemoteInput dan aksi "Batalkan" ditangani [AdbPairingReplyReceiver]
 * — notifikasi ini TIDAK pernah membuka Activity apa pun untuk menerima
 * input kode, benar-benar diproses di background lewat BroadcastReceiver,
 * sesuai pola notifikasi "Direct Reply" standar Android.
 */
object AdbPairingNotifier {

    private const val CHANNEL_ID = "aetherx_adb_pairing"
    const val NOTIFICATION_ID = 4103

    const val ACTION_REPLY_CODE = "com.aether.x.action.ADB_PAIRING_REPLY_CODE"
    const val ACTION_CANCEL = "com.aether.x.action.ADB_PAIRING_CANCEL"
    const val REMOTE_INPUT_KEY = "key_pairing_code"

    /**
     * Channel KHUSUS pairing dengan `IMPORTANCE_HIGH` — SENGAJA beda dari
     * channel lain di [AetherXNotifier] (yang sekarang sengaja DEFAULT
     * supaya tidak heads-up, lihat KDoc di sana) karena notifikasi pairing
     * justru WAJIB tampil mengambang: kode 6-digit di dialog Wireless
     * debugging kedaluwarsa dalam hitungan detik, pengguna harus langsung
     * lihat aksi isi kode tanpa perlu membuka notification tray dulu.
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.adb_pairing_notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.adb_pairing_notif_channel_desc)
            enableVibration(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.app.ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun cancelPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, AdbPairingReplyReceiver::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Aksi tunggal untuk mengisi kode 6-digit (`RemoteInput`) — SENGAJA
     * diberi label yang langsung menyebut fungsinya ("Isi kode 6-digit"),
     * BUKAN "Balas" generik, dan SENGAJA jadi action PERTAMA (lihat
     * [showCodeInput]/[showError]) supaya begitu notifikasi di-expand,
     * satu-satunya tindakan yang terlihat memang mengarah ke kotak kode
     * (lihat KDoc kelas soal batasan platform Android — tidak ada cara
     * membuka kotak ketik tanpa satu ketukan pada action).
     */
    private fun replyAction(context: Context): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(context.getString(R.string.adb_pairing_notif_reply_label))
            .build()

        val replyIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, AdbPairingReplyReceiver::class.java).setAction(ACTION_REPLY_CODE),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Action.Builder(
            R.drawable.logo,
            context.getString(R.string.adb_pairing_notif_reply_action),
            replyIntent,
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun cancelAction(context: Context): NotificationCompat.Action = NotificationCompat.Action.Builder(
        R.drawable.logo,
        context.getString(R.string.adb_pairing_notif_cancel_action),
        cancelPendingIntent(context),
    ).build()

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
        // SENGAJA TIDAK ada .setOnlyAlertOnce(true) di sini (lihat KDoc
        // fix #1 di atas) — setiap tahap pairing (Searching -> Found ->
        // Busy -> Error) harus heads-up ulang saat berpindah, karena
        // masing-masing tahap adalah kejadian baru yang perlu perhatian
        // pengguna, bukan sekadar progress update dari notifikasi yang
        // sama.
    }

    private fun push(context: Context, notification: Notification) {
        if (!hasNotificationPermission(context)) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Tahap 1 — "Mulai Penyandingan" ditekan: notifikasi "Searching for Pairing…". */
    fun showSearching(context: Context) {
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_searching_title))
            .setContentText(context.getString(R.string.adb_pairing_overlay_searching_hint))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.adb_pairing_overlay_searching_hint)))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(cancelAction(context))
            .build()
        push(context, notification)
    }

    /**
     * Tahap 2 — host+port ditemukan otomatis: notifikasi heads-up ULANG
     * (lihat KDoc fix #1) berganti jadi "Pairing found" dengan aksi
     * tunggal "Isi kode 6-digit" (lihat [replyAction]) sebagai action
     * pertama + aksi "Batalkan".
     */
    fun showCodeInput(context: Context) {
        val hint = context.getString(R.string.adb_pairing_overlay_code_hint_detail)
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_found_title))
            .setContentText(hint)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hint))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
            .addAction(cancelAction(context))
            .build()
        push(context, notification)
    }

    /** Tahap 3 — kode terkirim, sedang mencoba menyambung: tanpa aksi. */
    fun showBusy(context: Context) {
        val hint = context.getString(R.string.adb_pairing_overlay_connecting_hint)
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_connecting_title))
            .setContentText(hint)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hint))
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(0, 0, true)
            .build()
        push(context, notification)
    }

    /**
     * Tahap 4 — pairing gagal: notifikasi heads-up ULANG (lihat KDoc fix
     * #1), kembali ke aksi tunggal "Isi kode 6-digit" dengan pesan error
     * tampil di isi notifikasi, supaya pengguna bisa langsung coba isi
     * ulang kode dari notifikasi yang sama.
     */
    fun showError(context: Context, message: String) {
        val notification = baseBuilder(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
            .addAction(cancelAction(context))
            .build()
        push(context, notification)
    }

    /** Batalkan notifikasi pairing dari tray (Connected / NotPaired / PairedNotConnected). */
    fun stop(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/**
 * Menangani aksi dari notifikasi sistem pairing (lihat [AdbPairingNotifier]):
 * balasan RemoteInput berisi kode 6-digit, dan aksi "Batalkan".
 *
 * Terdaftar sebagai `<receiver>` biasa di AndroidManifest (BUKAN Service),
 * sesuai pola resmi Android "Direct Reply" — sistem menjalankan
 * [onReceive] singkat di background begitu pengguna mengetik kode lalu
 * menekan kirim di notifikasi, tanpa pernah membuka Activity apa pun.
 */
class AdbPairingReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdbPairingNotifier.ACTION_REPLY_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(AdbPairingNotifier.REMOTE_INPUT_KEY)?.toString()?.trim().orEmpty()
                if (code.length == 6 && code.all { it.isDigit() }) {
                    PrivilegeManager.confirmAutoPairAdbCode(context.applicationContext, code)
                } else {
                    AdbPairingNotifier.showError(
                        context.applicationContext,
                        context.getString(R.string.adb_pairing_notif_invalid_code),
                    )
                }
            }
            AdbPairingNotifier.ACTION_CANCEL -> {
                PrivilegeManager.cancelAutoPairAdb()
                AdbPairingNotifier.stop(context.applicationContext)
            }
        }
    }
}
