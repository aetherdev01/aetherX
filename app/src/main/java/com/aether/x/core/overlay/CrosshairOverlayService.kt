package com.aether.x.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.CrosshairStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CrosshairOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.aether.x.action.START_CROSSHAIR"
        const val ACTION_STOP = "com.aether.x.action.STOP_CROSSHAIR"
        const val ACTION_SET_DRAG_MODE = "com.aether.x.action.SET_DRAG_MODE"
        const val EXTRA_DRAG_MODE = "extra_drag_mode"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_crosshair_overlay"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context) {
            val intent = Intent(context, CrosshairOverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CrosshairOverlayService::class.java).setAction(ACTION_STOP))
        }

        fun setDragMode(context: Context, enabled: Boolean) {
            val intent = Intent(context, CrosshairOverlayService::class.java)
                .setAction(ACTION_SET_DRAG_MODE)
                .putExtra(EXTRA_DRAG_MODE, enabled)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private lateinit var preferences: AetherXPreferences
    private lateinit var windowManager: WindowManager
    private var crosshairView: CrosshairView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragModeEnabled = false

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartOffsetX = 0
    private var dragStartOffsetY = 0

    override fun onCreate() {
        super.onCreate()
        preferences = AetherXPreferences(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_DRAG_MODE -> {
                dragModeEnabled = intent.getBooleanExtra(EXTRA_DRAG_MODE, false)
                setDragTouchable(dragModeEnabled)
                return START_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                showOverlay()
                observePreferences()
                return START_STICKY
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.crosshair_notification_channel),
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

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, CrosshairOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(getString(R.string.crosshair_notification_title))
            .setContentText(getString(R.string.crosshair_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.overlay_notification_action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun showOverlay() {
        if (crosshairView != null) return

        val view = CrosshairView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            baseTouchFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        view.setOnTouchListener { _, event -> handleDragTouch(event) }

        windowManager.addView(view, params)
        crosshairView = view
        layoutParams = params
    }

    /**
     * Flag dasar window overlay crosshair. FLAG_NOT_TOUCHABLE disertakan
     * secara DEFAULT supaya crosshair tidak mencegat sentuhan sama sekali
     * — tanpa ini, tap tepat di titik crosshair terasa "tertutup"/tidak
     * merespons karena window overlay ikut mengonsumsi event sentuh
     * sebelum diteruskan ke game/aplikasi di baliknya. Flag ini dilepas
     * sementara hanya saat drag mode (geser posisi) aktif — lihat
     * [setDragTouchable] — lalu dipasang kembali begitu drag selesai.
     */
    private fun baseTouchFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    /**
     * Mengaktifkan/menonaktifkan kemampuan window overlay menerima
     * sentuhan. Dipanggil saat drag mode diaktifkan (touchable = true,
     * supaya jari bisa menggeser crosshair) dan saat drag berakhir atau
     * dimatikan (touchable = false, kembali ke default tembus-sentuh).
     */
    private fun setDragTouchable(touchable: Boolean) {
        val params = layoutParams ?: return
        val view = crosshairView ?: return
        params.flags = if (touchable) {
            baseTouchFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            baseTouchFlags()
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun handleDragTouch(event: MotionEvent): Boolean {
        if (!dragModeEnabled) return false
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartOffsetX = params.x
                dragStartOffsetY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX).roundToInt()
                val dy = (event.rawY - dragStartRawY).roundToInt()
                params.x = dragStartOffsetX + dx
                params.y = dragStartOffsetY + dy
                runCatching { windowManager.updateViewLayout(crosshairView, params) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                serviceScope.launch {
                    preferences.setCrosshairOffset(params.x, params.y)
                }
                return true
            }
        }
        return false
    }

    private fun observePreferences() {
        preferences.preferences.onEach { prefs ->
            val view = crosshairView ?: return@onEach
            val params = layoutParams ?: return@onEach

            view.style = prefs.crosshairStyle
            view.colorArgb = prefs.crosshairColor
            view.crosshairSizePx = prefs.crosshairSize.toFloat()
            view.rotationDegrees = prefs.crosshairRotationDegrees

            if (params.x != prefs.crosshairOffsetX || params.y != prefs.crosshairOffsetY) {
                params.x = prefs.crosshairOffsetX
                params.y = prefs.crosshairOffsetY
                runCatching { windowManager.updateViewLayout(view, params) }
            }

            if (!prefs.crosshairEnabled) {
                stopSelf()
            }
        }.launchIn(serviceScope)
    }

    override fun onDestroy() {
        crosshairView?.let { runCatching { windowManager.removeView(it) } }
        crosshairView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
