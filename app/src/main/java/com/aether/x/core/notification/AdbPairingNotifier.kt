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
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager

/**
 * REWORK — sistem notifikasi Pairing (lihat perintah rework: "bukan pakai
 * floating window/dialog mengambang, tetapi pakai notifikasi sistem dengan
 * notifikasi mengambang").
 *
 * FIX (rework ketiga) — dua masalah dilaporkan dari implementasi
 * sebelumnya:
 *
 * 1. "saat notifikasi Pairing found itu jadikan notifikasi mengambang nya
 *    expand bukan saat diklik malah bukan apk AXKM" — sebelumnya
 *    [baseBuilder] SELALU memasang `setContentIntent(...)` ke MainActivity
 *    di SEMUA tahap, termasuk [showCodeInput]/[showError]. Karena
 *    notifikasi ini juga masih ber-`setOngoing(true)` + dua aksi (Isi
 *    kode & Batalkan), area "body" notifikasi (di luar dua tombol aksi)
 *    tetap mengarah ke content intent itu — begitu pengguna menekan area
 *    body (bukan tombolnya persis), yang terbuka jadi AetherX (MainActivity)
 *    alih-alih kotak isi kode. `setContentIntent` SEKARANG DIHAPUS TOTAL
 *    dari [showCodeInput] & [showError] (lihat [baseBuilderNoContentIntent])
 *    — notifikasi tahap ini TIDAK PERNAH membuka Activity apa pun lagi,
 *    satu-satunya tindakan yang mungkin dari notifikasi ini adalah aksi
 *    "Isi kode 6-digit" itu sendiri.
 *
 * 2. "hapus action batalkan" — aksi "Batalkan" DIHAPUS dari [showCodeInput]
 *    & [showError]. Begitu host+port sudah ditemukan (`PairingFound`),
 *    satu-satunya aksi yang relevan/terlihat adalah mengisi kode; aksi
 *    Batalkan yang berdampingan sebelumnya membuat area tap-target aksi
 *    "Isi kode 6-digit" lebih kecil/mudah salah pencet, sekaligus jadi
 *    aksi kedua yang tidak dibutuhkan pengguna di tahap ini. Aksi
 *    "Batalkan" TETAP dipertahankan HANYA di [showSearching] (tahap
 *    menunggu, sebelum host+port ditemukan) — di situ pengguna memang
 *    mungkin ingin membatalkan pencarian yang belum berbuah hasil.
 *
 * CATATAN PENTING soal "expand" notifikasi: Android TIDAK menyediakan API
 * apa pun bagi aplikasi pihak ketiga untuk memaksa sebuah notifikasi
 * heads-up tampil dalam keadaan "expanded" (menampilkan action button)
 * tanpa satu ketukan/gesture dari pengguna — ini berlaku sama untuk semua
 * aplikasi termasuk WhatsApp/Telegram/Gmail. Yang BISA dan SUDAH dilakukan
 * di sini supaya sedekat mungkin dengan "langsung terlihat": (a) heads-up
 * (`PRIORITY_HIGH` + channel `IMPORTANCE_HIGH`) membuat notifikasi ini
 * SELALU muncul mengambang di atas layar apa pun yang sedang dibuka
 * (termasuk dialog Wireless debugging), (b) `BigTextStyle` membuat body
 * teks tetap terbaca penuh walau notifikasi belum di-expand manual, dan
 * (c) sekarang HANYA ada SATU aksi (poin #2 di atas) sehingga begitu
 * pengguna men-tap/expand notifikasi manapun caranya, satu-satunya tombol
 * yang terlihat memang "Isi kode 6-digit" — tidak ada lagi ambiguitas
 * dengan aksi Batalkan atau body yang membuka aplikasi.
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
 *     heads-up "Searching for Pairing…" muncul, dengan aksi "Batalkan"
 *     (satu-satunya tahap yang masih punya aksi ini, lihat fix #2 di atas).
 *  2. [showCodeInput] — service pairing terdeteksi otomatis via mDNS ->
 *     notifikasi heads-up ULANG berganti jadi "Pairing found" dengan SATU
 *     aksi saja: "Isi kode 6-digit" (RemoteInput), TANPA content intent
 *     & TANPA aksi Batalkan (lihat fix #1 & #2).
 *  3. [showBusy] — kode terkirim, notifikasi berganti "Menyandingkan…"
 *     tanpa aksi apa pun (indeterminate, `setOngoing`).
 *  4. [showError] — pairing gagal -> notifikasi heads-up ULANG, kembali ke
 *     mode isi kode (SATU aksi saja, sama seperti #2) dengan pesan error
 *     di bawahnya, supaya pengguna bisa langsung coba lagi dari notifikasi
 *     yang sama tanpa mengulang dari awal.
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
     * Aksi tunggal untuk mengisi kode 6-digit (`RemoteInput`) — diberi
     * label yang langsung menyebut fungsinya ("Isi kode 6-digit"), BUKAN
     * "Balas" generik. Sejak fix #2 (lihat KDoc kelas), ini SEKARANG
     * SATU-SATUNYA aksi yang ditambahkan di [showCodeInput]/[showError] —
     * tidak ada lagi aksi "Batalkan" berdampingan, dan notifikasi tahap
     * ini tidak lagi punya content intent yang bisa "merebut" tap yang
     * dimaksudkan untuk aksi ini (lihat [baseBuilderNoContentIntent]).
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

    /**
     * Builder dasar UMUM (dipakai [showSearching] & [showBusy]) — masih
     * memasang content intent ke MainActivity karena kedua tahap itu TIDAK
     * punya aksi RemoteInput yang bisa "direbut tap"-nya (Searching hanya
     * punya aksi Batalkan yang tombolnya sendiri sudah jelas terpisah;
     * Busy tidak punya aksi apa pun), jadi aman kalau body notifikasi
     * dipakai membuka AetherX.
     */
    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, com.aether.x.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
        // SENGAJA TIDAK ada .setOnlyAlertOnce(true) di sini — setiap tahap
        // pairing (Searching -> Found -> Busy -> Error) harus heads-up
        // ulang saat berpindah, karena masing-masing tahap adalah kejadian
        // baru yang perlu perhatian pengguna, bukan sekadar progress
        // update dari notifikasi yang sama.
    }

    /**
     * FIX (rework ketiga, lihat KDoc kelas poin #1) — builder KHUSUS untuk
     * [showCodeInput] & [showError]: TIDAK memasang content intent apa
     * pun (`setContentIntent(null)` eksplisit), supaya tap di area body
     * notifikasi TIDAK membuka MainActivity/AXKM. Notifikasi tahap ini
     * hanya boleh direspons lewat SATU jalur: aksi "Isi kode 6-digit"
     * ([replyAction]) yang membawa `RemoteInput` sendiri.
     */
    private fun baseBuilderNoContentIntent(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(null)
        // SENGAJA TIDAK ada .setOnlyAlertOnce(true) — lihat penjelasan sama
        // di [baseBuilder].
    }

    private fun push(context: Context, notification: Notification) {
        if (!hasNotificationPermission(context)) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Tahap 1 — "Mulai Penyandingan" ditekan: notifikasi "Searching for
     * Pairing…". Satu-satunya tahap yang masih punya aksi "Batalkan"
     * (lihat KDoc kelas fix #2) karena di sini pengguna memang mungkin
     * ingin membatalkan pencarian yang belum menemukan apa pun. */
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
     * berganti jadi "Pairing found" dengan SATU aksi saja: "Isi kode
     * 6-digit" (lihat [replyAction]). TIDAK ADA lagi content intent
     * (tidak membuka AXKM saat body di-tap) maupun aksi "Batalkan" —
     * lihat KDoc kelas fix #1 & #2.
     */
    fun showCodeInput(context: Context) {
        val hint = context.getString(R.string.adb_pairing_overlay_code_hint_detail)
        val notification = baseBuilderNoContentIntent(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_found_title))
            .setContentText(hint)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hint))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
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
     * Tahap 4 — pairing gagal: notifikasi heads-up ULANG, kembali ke SATU
     * aksi saja "Isi kode 6-digit" (sama seperti [showCodeInput], lihat
     * KDoc kelas fix #1 & #2) dengan pesan error tampil di isi notifikasi,
     * supaya pengguna bisa langsung coba isi ulang kode dari notifikasi
     * yang sama.
     */
    fun showError(context: Context, message: String) {
        val notification = baseBuilderNoContentIntent(context)
            .setContentTitle(context.getString(R.string.adb_pairing_overlay_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction(context))
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
 * balasan RemoteInput berisi kode 6-digit, dan aksi "Batalkan" (hanya
 * relevan dari tahap [AdbPairingNotifier.showSearching] sejak fix #2 —
 * lihat KDoc kelas [AdbPairingNotifier]).
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
