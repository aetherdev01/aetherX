package com.aether.x.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.widget.doAfterTextChanged
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import kotlin.math.roundToInt

/**
 * FITUR BARU — notifikasi mengambang untuk kode pairing Wireless debugging
 * (permintaan: "notifikasi saat pairing wireless debugging itu pakai
 * notifikasi mengambang, bukan notifikasi dari dalam apk, jadi bisa buka
 * aplikasi pengaturan buat isi pairing code nya, jadi ga ribet pakai
 * layar split").
 *
 * SEBELUMNYA kode 6-digit diminta lewat [com.aether.x.ui.components.AdbAutoPairingCodeDialog],
 * sebuah Compose `AlertDialog` yang terikat ke Activity AetherX — begitu
 * pengguna pindah ke aplikasi Pengaturan untuk MELIHAT kode pairing-nya,
 * dialog itu otomatis tertutup/tersembunyi di belakang, memaksa pengguna
 * memakai split-screen supaya kedua layar terlihat sekaligus.
 *
 * Overlay ini menggantikan dialog tersebut dengan window
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] SUNGGUHAN, persis
 * pola [CrosshairOverlayService]/[FpsMonitorOverlayService] — kartu kecil
 * yang melayang DI ATAS aplikasi apa pun (termasuk Pengaturan), sehingga
 * pengguna bisa membuka Wireless debugging, melihat kode 6-digit di sana,
 * lalu langsung mengetiknya di kartu mengambang ini tanpa pernah
 * berpindah balik ke AetherX atau membagi layar.
 *
 * Butuh izin "Tampil di atas aplikasi lain" (SYSTEM_ALERT_WINDOW /
 * Settings.canDrawOverlays) — izin yang sama yang sudah dipakai
 * fitur overlay lain di AetherX (crosshair, Monitor FPS, Game Booster),
 * jadi tidak ada permintaan izin baru untuk pengguna yang sudah
 * mengaktifkan salah satu dari fitur itu sebelumnya.
 */
class AdbPairingOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_SEARCHING = "com.aether.x.action.ADB_PAIRING_OVERLAY_SEARCHING"
        const val ACTION_SHOW_CODE_INPUT = "com.aether.x.action.ADB_PAIRING_OVERLAY_CODE_INPUT"
        const val ACTION_SHOW_BUSY = "com.aether.x.action.ADB_PAIRING_OVERLAY_BUSY"
        const val ACTION_SHOW_ERROR = "com.aether.x.action.ADB_PAIRING_OVERLAY_ERROR"
        const val ACTION_STOP = "com.aether.x.action.ADB_PAIRING_OVERLAY_STOP"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_adb_pairing_overlay"
        private const val NOTIFICATION_ID = 4103

        fun showSearching(context: Context) {
            context.startForegroundService(Intent(context, AdbPairingOverlayService::class.java).setAction(ACTION_SHOW_SEARCHING))
        }

        fun showCodeInput(context: Context) {
            context.startForegroundService(Intent(context, AdbPairingOverlayService::class.java).setAction(ACTION_SHOW_CODE_INPUT))
        }

        fun showBusy(context: Context) {
            context.startForegroundService(Intent(context, AdbPairingOverlayService::class.java).setAction(ACTION_SHOW_BUSY))
        }

        fun showError(context: Context, message: String) {
            val intent = Intent(context, AdbPairingOverlayService::class.java)
                .setAction(ACTION_SHOW_ERROR)
                .putExtra(EXTRA_ERROR_MESSAGE, message)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AdbPairingOverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var windowManager: WindowManager
    private var cardView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Referensi widget di dalam kartu supaya bisa diperbarui tanpa
    // membongkar-pasang seluruh overlay tiap kali state berubah.
    private var statusLabel: TextView? = null
    private var hintLabel: TextView? = null
    private var codeInput: EditText? = null
    private var confirmButton: TextView? = null
    private var progressSpinner: ProgressBar? = null

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                ensureOverlayShown()
                applyState(intent?.action, intent?.getStringExtra(EXTRA_ERROR_MESSAGE))
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------
    // Notifikasi foreground service (persyaratan Android untuk overlay
    // yang jalan di background) — dibuat prioritas MIN, sama seperti
    // overlay lain, supaya tidak mengganggu tapi tetap patuh kebijakan.
    // -------------------------------------------------------------------
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.adb_pairing_overlay_notification_channel),
                NotificationManager.IMPORTANCE_MIN,
            )
            manager?.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aetherx_mark)
            .setContentTitle(getString(R.string.adb_pairing_overlay_notification_title))
            .setContentText(getString(R.string.adb_pairing_overlay_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    // -------------------------------------------------------------------
    // Konstruksi kartu mengambang — dibuat sekali secara programatik
    // (tanpa layout XML, sama seperti overlay lain di modul ini), lalu
    // kontennya diperbarui lewat applyState() sesuai fase pairing.
    // -------------------------------------------------------------------
    private fun ensureOverlayShown() {
        if (cardView != null) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val accent = Color.parseColor("#C97B45")
        val surface = Color.parseColor("#2E2927")
        val textPrimary = Color.parseColor("#ECDAD0")
        val textMuted = Color.parseColor("#8A8078")
        val onAccent = Color.parseColor("#1F0F08")

        val root = FrameLayout(this)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(surface)
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                marginEnd = dp(10)
            }
        }
        headerRow.addView(spinner)

        val status = TextView(this).apply {
            text = getString(R.string.adb_pairing_overlay_searching_title)
            setTextColor(textPrimary)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(
            status,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(textMuted)
            textSize = 14f
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { PrivilegeManager.cancelAutoPairAdb(); stopSelf() }
        }
        headerRow.addView(closeButton)

        card.addView(headerRow)


        val hintText = TextView(this).apply {
            text = getString(R.string.adb_pairing_overlay_searching_hint)
            setTextColor(textMuted)
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(hintText)

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.adb_pairing_overlay_code_hint)
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#34302E"))
            }
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        card.addView(input)

        val confirm = TextView(this).apply {
            text = getString(R.string.adb_pairing_overlay_confirm)
            setTextColor(onAccent)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(accent)
            }
            visibility = View.GONE
            alpha = 0.5f
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        card.addView(confirm)

        input.doAfterTextChanged { text ->
            val ready = (text?.length ?: 0) == 6
            confirm.isEnabled = ready
            confirm.alpha = if (ready) 1f else 0.5f
        }
        confirm.setOnClickListener {
            val code = input.text?.toString()?.trim().orEmpty()
            if (code.length == 6) {
                PrivilegeManager.confirmAutoPairAdbCode(applicationContext, code)
            }
        }

        root.addView(card, FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT))
        root.setOnTouchListener { _, event -> handleDragTouch(event) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            // SENGAJA TIDAK FLAG_NOT_FOCUSABLE — kartu ini punya EditText
            // yang harus bisa menerima fokus & memunculkan keyboard,
            // berbeda dengan overlay crosshair/FPS yang murni visual.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }

        windowManager.addView(root, params)
        cardView = root
        layoutParams = params
        statusLabel = status
        hintLabel = hint
        codeInput = input
        confirmButton = confirm
        progressSpinner = spinner
    }

    /**
     * Memperbarui isi kartu sesuai fase yang diminta lewat action Intent —
     * SEARCHING (menunggu dialog pairing Android dibuka), CODE_INPUT (host
     * & port sudah ditemukan lewat mDNS, tinggal menunggu kode 6-digit),
     * BUSY (kode sudah dikirim, sedang mencoba menyambung), atau ERROR
     * (pairing gagal, tampilkan pesan lalu kembali ke mode input).
     */
    private fun applyState(action: String?, errorMessage: String?) {
        val status = statusLabel ?: return
        val hint = hintLabel ?: return
        val input = codeInput ?: return
        val confirm = confirmButton ?: return
        val spinner = progressSpinner ?: return

        when (action) {
            ACTION_SHOW_CODE_INPUT -> {
                spinner.visibility = View.GONE
                status.text = getString(R.string.adb_pairing_overlay_found_title)
                hint.text = getString(R.string.adb_pairing_overlay_code_hint_detail)
                input.visibility = View.VISIBLE
                input.isEnabled = true
                confirm.visibility = View.VISIBLE
                input.requestFocus()
            }
            ACTION_SHOW_BUSY -> {
                spinner.visibility = View.VISIBLE
                status.text = getString(R.string.adb_pairing_overlay_connecting_title)
                hint.text = getString(R.string.adb_pairing_overlay_connecting_hint)
                input.isEnabled = false
                confirm.isEnabled = false
                confirm.alpha = 0.5f
            }
            ACTION_SHOW_ERROR -> {
                spinner.visibility = View.GONE
                status.text = getString(R.string.adb_pairing_overlay_error_title)
                hint.text = errorMessage.orEmpty().ifBlank { getString(R.string.adb_pairing_overlay_code_hint_detail) }
                input.visibility = View.VISIBLE
                input.isEnabled = true
                input.setText("")
                confirm.visibility = View.VISIBLE
                confirm.isEnabled = false
                confirm.alpha = 0.5f
            }
            else -> {
                // ACTION_SHOW_SEARCHING (default begitu Start ditekan).
                spinner.visibility = View.VISIBLE
                status.text = getString(R.string.adb_pairing_overlay_searching_title)
                hint.text = getString(R.string.adb_pairing_overlay_searching_hint)
                input.visibility = View.GONE
                confirm.visibility = View.GONE
            }
        }
    }

    private fun removeOverlay() {
        cardView?.let { runCatching { windowManager.removeView(it) } }
        cardView = null
        layoutParams = null
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Kartu bisa digeser (long-drag di header) supaya pengguna bisa
     * memindahkannya kalau menutupi kode pairing di layar Pengaturan. */
    private fun handleDragTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartX = params.x
                dragStartY = params.y
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX)
                val dy = (event.rawY - dragStartRawY)
                if (!isDragging && (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12)) {
                    isDragging = true
                }
                if (isDragging) {
                    params.x = dragStartX
                    params.y = (dragStartY + dy).roundToInt()
                    runCatching { windowManager.updateViewLayout(cardView, params) }
                    return true
                }
                return false
            }
            else -> return false
        }
    }
}
