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
import com.aether.x.core.apps.GameLauncher
import com.aether.x.core.monitor.ForegroundAppReader
import com.aether.x.core.monitor.GfxInfoFpsReader
import com.aether.x.core.monitor.SystemStatsProvider
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.FpsMonitorStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service yang menggambar panel Monitor FPS sebagai overlay di atas
 * semua aplikasi lain, mirip [CrosshairOverlayService] tapi untuk statistik
 * performa (FPS, CPU, GPU, suhu).
 *
 * Gaya ROG bisa digeser bebas oleh pengguna (offset disimpan & dipulihkan).
 * Gaya Classic SENGAJA tidak menerima sentuhan sama sekali dan selalu
 * dikunci ke pojok kiri bawah layar — sesuai permintaan desain "posisi tetap".
 */
class FpsMonitorOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.aether.x.action.START_FPS_MONITOR"
        const val ACTION_STOP = "com.aether.x.action.STOP_FPS_MONITOR"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_fps_monitor_overlay"
        private const val NOTIFICATION_ID = 4103
        private const val STATS_REFRESH_INTERVAL_MS = 1200L

        fun start(context: Context) {
            val intent = Intent(context, FpsMonitorOverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FpsMonitorOverlayService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private lateinit var preferences: AetherXPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var statsProvider: SystemStatsProvider
    private val foregroundAppReader = ForegroundAppReader()
    private var fpsReader: GfxInfoFpsReader? = null
    private var fpsReaderPackage: String? = null
    private var statsLoopStarted = false

    private var monitorView: FpsMonitorView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentStyle: FpsMonitorStyle = FpsMonitorStyle.CLASSIC

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartOffsetX = 0
    private var dragStartOffsetY = 0

    override fun onCreate() {
        super.onCreate()
        preferences = AetherXPreferences(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        statsProvider = SystemStatsProvider()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                showOverlay()
                startStatsLoop()
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
                getString(R.string.fps_monitor_notification_channel),
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
            .setContentTitle(getString(R.string.fps_monitor_notification_title))
            .setContentText(getString(R.string.fps_monitor_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun showOverlay() {
        if (monitorView != null) return

        val view = FpsMonitorView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )

        view.setOnTouchListener { _, event -> handleDragTouch(event) }

        windowManager.addView(view, params)
        monitorView = view
        layoutParams = params
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Gaya Classic TIDAK PERNAH menerima drag — permintaannya "posisi tetap".
     * Hanya gaya ROG yang boleh digeser bebas oleh pengguna.
     */
    private fun handleDragTouch(event: MotionEvent): Boolean {
        if (currentStyle != FpsMonitorStyle.ROG) return false
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
                runCatching { windowManager.updateViewLayout(monitorView, params) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                serviceScope.launch {
                    preferences.setFpsMonitorOffset(params.x, params.y)
                }
                return true
            }
        }
        return false
    }

    private fun startStatsLoop() {
        if (statsLoopStarted) return
        statsLoopStarted = true

        serviceScope.launch {
            while (true) {
                monitorView?.let { view ->
                    val executor = PrivilegeManager.getExecutor()
                    view.fps = if (executor != null) {
                        readFpsForForegroundApp(executor)
                    } else {
                        // Tanpa Shizuku/Root, FPS asli tidak bisa dibaca — tampilkan 0
                        // apa adanya daripada memalsukan angka Hz layar.
                        0
                    }
                    view.cpuPercent = statsProvider.readCpuLoadPercent()
                    view.gpuPercent = statsProvider.readGpuLoadPercent()
                    view.temperatureCelsius = statsProvider.readTemperatureCelsius(applicationContext)
                }
                delay(STATS_REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Deteksi ulang aplikasi yang sedang di foreground di TIAP siklus, lalu baca
     * FPS render sungguhan untuk package itu lewat `dumpsys gfxinfo`.
     *
     * Sebelumnya AetherX hanya mengecek APK mana yang terpasang satu kali saat
     * overlay pertama kali dinyalakan (lewat [GameLauncher.detectInstalled]) —
     * kalau game belum dibuka saat itu, atau pengguna sedang di aplikasi lain,
     * `dumpsys gfxinfo` dijalankan ke package yang tidak sedang merender apapun
     * sehingga selalu menghasilkan 0. Dengan deteksi foreground berulang, target
     * pembacaan FPS otomatis mengikuti aplikasi yang benar-benar aktif di layar.
     */
    private suspend fun readFpsForForegroundApp(executor: ShellExecutor): Int {
        val foregroundPackage = foregroundAppReader.readForegroundPackage(executor)
            ?: fallbackInstalledGamePackage()
            ?: return 0

        // AetherX sendiri, launcher, atau system UI tidak punya frame game untuk
        // dibaca — tampilkan 0 apa adanya daripada angka yang menyesatkan.
        if (foregroundPackage == packageName) return 0

        if (fpsReader == null || fpsReaderPackage != foregroundPackage) {
            fpsReader = GfxInfoFpsReader(foregroundPackage)
            fpsReaderPackage = foregroundPackage
        }

        return fpsReader?.readFps(executor) ?: 0
    }

    /** Fallback kalau deteksi foreground gagal: pakai game yang terpasang, kalau ada. */
    private fun fallbackInstalledGamePackage(): String? =
        GameLauncher.detectInstalled(applicationContext).firstOrNull()?.packageName

    private fun observePreferences() {
        preferences.preferences.onEach { prefs ->
            val view = monitorView ?: return@onEach
            val params = layoutParams ?: return@onEach

            if (view.style != prefs.fpsMonitorStyle) {
                view.style = prefs.fpsMonitorStyle
            }
            view.temperatureUnit = prefs.temperatureUnit
            currentStyle = prefs.fpsMonitorStyle

            applyGravityAndPosition(params, prefs.fpsMonitorStyle, prefs.fpsMonitorOffsetX, prefs.fpsMonitorOffsetY)

            if (!prefs.fpsMonitorEnabled) {
                stopSelf()
            }
        }.launchIn(serviceScope)
    }

    /**
     * ROG: gravity default kiri-atas + offset yang pengguna geser sendiri (persist).
     * Classic: SELALU gravity kiri-bawah dengan margin tetap, tidak pernah dipengaruhi
     * offset tersimpan — ini yang membuat posisinya benar-benar "tetap".
     */
    private fun applyGravityAndPosition(
        params: WindowManager.LayoutParams,
        style: FpsMonitorStyle,
        offsetX: Int,
        offsetY: Int,
    ) {
        val density = resources.displayMetrics.density
        val fixedMargin = (16 * density).toInt()

        val (gravity, x, y) = when (style) {
            FpsMonitorStyle.ROG -> Triple(Gravity.TOP or Gravity.START, offsetX, offsetY)
            FpsMonitorStyle.CLASSIC -> Triple(Gravity.BOTTOM or Gravity.START, fixedMargin, fixedMargin)
        }

        if (params.gravity != gravity || params.x != x || params.y != y) {
            params.gravity = gravity
            params.x = x
            params.y = y
            runCatching { windowManager.updateViewLayout(monitorView, params) }
        }
    }

    override fun onDestroy() {
        fpsReader = null
        fpsReaderPackage = null
        statsLoopStarted = false
        monitorView?.let { runCatching { windowManager.removeView(it) } }
        monitorView = null
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
