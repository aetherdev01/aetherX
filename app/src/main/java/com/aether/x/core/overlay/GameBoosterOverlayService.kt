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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.booster.GameBoosterActionHandler
import com.aether.x.core.booster.GameBoosterMonitor
import com.aether.x.core.booster.GameBoosterSession
import com.aether.x.core.booster.GameBoosterSessionHolder
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameMode
import com.aether.x.ui.booster.GameBoosterActions
import com.aether.x.ui.booster.GameBoosterSidebarContent
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AetherXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service untuk floating sidebar Game Booster (lihat perintah
 * rework: "lalu saat buka game game booster jadi side bar/floating dan
 * gampang diakses serta smooth") — MENIRU pola foreground overlay yang
 * sudah ada ([CrosshairOverlayService], [FpsMonitorOverlayService]), tapi
 * dengan DUA state visual alih-alih satu:
 *
 * 1. **Collapsed (bubble)**: bulatan kecil draggable (seperti chat head
 *    Messenger) yang mengambang di atas game — SELALU jadi state AWAL saat
 *    sesi boost dimulai, supaya tidak menghalangi layar permainan.
 * 2. **Expanded (sidebar)**: panel penuh [GameBoosterSidebarContent] — 
 *    muncul saat bubble di-tap, berisi semua menu (FPS/DND/screenshot/mode)
 *    + grafik monitoring. Tap di luar sidebar (atau tombol tutup) kembali
 *    ke state bubble.
 *
 * Kedua state dirender lewat [ComposeView] yang di-attach manual ke
 * [WindowManager] — lihat [ComposeOverlayLifecycleOwner] untuk alasan
 * kenapa ini butuh lifecycle owner manual (service ini bukan Activity).
 */
class GameBoosterOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.aether.x.action.START_GAME_BOOSTER"
        const val ACTION_STOP = "com.aether.x.action.STOP_GAME_BOOSTER"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_GAME_LABEL = "extra_game_label"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_game_booster_overlay"
        private const val NOTIFICATION_ID = 4103
        private const val BUBBLE_SIZE_DP = 56
        // FITUR BARU (lihat perintah rework floating booster foto 2):
        // diperbesar dari 260 -> 320 karena layout baru GameBoosterSidebarContent
        // sekarang berbentuk card lebar horizontal (suhu CPU kiri — card
        // game tengah — ping kanan), bukan lagi panel sempit vertikal.
        private const val SIDEBAR_WIDTH_DP = 320
        private const val DRAG_THRESHOLD_PX_SQUARED = 400f

        fun start(context: Context, packageName: String, gameLabel: String) {
            val intent = Intent(context, GameBoosterOverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_GAME_LABEL, gameLabel)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GameBoosterOverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: AetherXPreferences
    private val actionHandler = GameBoosterActionHandler()

    private var bubbleView: ComposeView? = null
    private var sidebarView: ComposeView? = null

    private var bubbleLifecycleOwner: ComposeOverlayLifecycleOwner? = null
    private var sidebarLifecycleOwner: ComposeOverlayLifecycleOwner? = null

    private var monitorJob: Job? = null
    private var currentPackageName: String? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferences = AetherXPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                endSession()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                val gameLabel = intent.getStringExtra(EXTRA_GAME_LABEL) ?: packageName
                startForeground(NOTIFICATION_ID, buildNotification(gameLabel))
                startSession(packageName, gameLabel)
            }
        }
        return START_STICKY
    }

    private fun startSession(packageName: String, gameLabel: String) {
        // Sesi baru menimpa sesi lama kalau ada (mis. pengguna berpindah
        // game tanpa menutup sidebar dulu) — bersihkan view lama dulu.
        if (currentPackageName != null && currentPackageName != packageName) {
            removeAllViews()
        }
        currentPackageName = packageName

        serviceScope.launch {
            val prefs = preferences.preferences
            // Baca SEKALI snapshot preferences untuk nilai awal sesi (mode,
            // toggle FPS overlay) — perubahan SELANJUTNYA dari sidebar UI
            // langsung mengubah GameBoosterSessionHolder + preferences
            // bersamaan, tidak perlu terus mengamati Flow preferences ini.
            val initial = prefs.first()
            GameBoosterSessionHolder.start(
                GameBoosterSession(
                    packageName = packageName,
                    gameLabel = gameLabel,
                    mode = initial.gameBoosterMode,
                    dndEnabled = initial.gameBoosterDndEnabled,
                    fpsOverlayEnabled = initial.gameBoosterFpsOverlayEnabled,
                    rotationLocked = initial.gameBoosterRotationLocked,
                    touchBoostEnabled = initial.gameBoosterTouchBoostEnabled,
                ),
            )

            val executor = PrivilegeManager.getExecutor()
            if (executor != null) {
                actionHandler.applyMode(executor, initial.gameBoosterMode)
                if (initial.gameBoosterDndEnabled) actionHandler.applyDnd(executor, true)
                if (initial.gameBoosterRotationLocked) actionHandler.applyRotationLock(executor, true)
                if (initial.gameBoosterTouchBoostEnabled) actionHandler.applyTouchBoost(executor, true)
            }

            showBubble()
            startMonitor(packageName)

            // FITUR BARU (lihat perintah rework floating booster foto 2 —
            // card game menampilkan ikon ASLI): dimuat SETELAH session
            // awal di-start (bukan sebelum) supaya bubble/sidebar tampil
            // secepatnya tanpa menunggu I/O baca PackageManager; begitu
            // icon siap, GameBoosterSessionHolder.update menyisipkannya ke
            // session yang sudah berjalan — GameBoosterSidebarContent yang
            // mengamati StateFlow ini otomatis recompose menampilkannya.
            val icon = com.aether.x.core.apps.GameProfileCatalog.loadIconForPackage(applicationContext, packageName)
            if (icon != null) {
                GameBoosterSessionHolder.update { it.copy(icon = icon) }
            }
        }
    }

    private fun startMonitor(packageName: String) {
        monitorJob?.cancel()
        val monitor = GameBoosterMonitor(packageName)
        monitorJob = monitor.metricsFlow(applicationContext)
            .onEach { metrics -> GameBoosterSessionHolder.update { it.copy(metrics = metrics) } }
            .launchIn(serviceScope)
    }

    private fun endSession() {
        serviceScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor != null) {
                // Kembalikan mode ke MID (normal) & matikan DND saat sesi
                // boost diakhiri — TIDAK memakai resetRootTweaksOnly di sini
                // karena itu untuk tweak Game Profile per-game yang berbeda
                // domain dari preset Game Booster ini.
                actionHandler.applyMode(executor, GameMode.MID)
                actionHandler.applyDnd(executor, false)
            }
        }
        monitorJob?.cancel()
        GameBoosterSessionHolder.clear()
        currentPackageName = null
        removeAllViews()
    }

    private fun removeAllViews() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        sidebarView?.let { runCatching { windowManager.removeView(it) } }
        bubbleLifecycleOwner?.onDestroy()
        sidebarLifecycleOwner?.onDestroy()
        bubbleView = null
        sidebarView = null
        bubbleLifecycleOwner = null
        sidebarLifecycleOwner = null
    }

    // ============================ Bubble (collapsed) ============================

    private fun showBubble() {
        if (bubbleView != null) return

        val owner = ComposeOverlayLifecycleOwner()
        bubbleLifecycleOwner = owner

        // WindowManager.LayoutParams WAJIB nilai piksel asli, BUKAN dp
        // mentah — konversi manual lewat displayMetrics.density (bukan
        // BUBBLE_SIZE_DP.dp.value yang hanya mengembalikan angka dp itu
        // sendiri, tanpa dikalikan densitas layar sama sekali).
        val bubbleSizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).roundToInt()

        val params = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        var dragStartRawX = 0f
        var dragStartRawY = 0f
        var dragStartX = 0
        var dragStartY = 0
        var isDragging = false

        val view = ComposeView(this).apply {
            setContent {
                AetherXTheme {
                    Box(
                        modifier = Modifier
                            .size(BUBBLE_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(AccentBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = stringResource(R.string.game_booster_bubble_content_desc),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartRawX = event.rawX
                        dragStartRawY = event.rawY
                        dragStartX = params.x
                        dragStartY = params.y
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - dragStartRawX)
                        val dy = (event.rawY - dragStartRawY)
                        if (dx * dx + dy * dy > DRAG_THRESHOLD_PX_SQUARED) isDragging = true
                        if (isDragging) {
                            params.x = dragStartX + dx.roundToInt()
                            params.y = dragStartY + dy.roundToInt()
                            runCatching { windowManager.updateViewLayout(this, params) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            expandSidebar()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        owner.attachToDecorView(view)
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        runCatching { windowManager.addView(view, params) }
        bubbleView = view
    }

    private fun hideBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleLifecycleOwner?.onDestroy()
        bubbleView = null
        bubbleLifecycleOwner = null
    }

    // ============================ Sidebar (expanded) ============================

    private fun expandSidebar() {
        if (sidebarView != null) return
        hideBubble()

        val owner = ComposeOverlayLifecycleOwner()
        sidebarLifecycleOwner = owner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // FITUR BARU (lihat perintah rework floating booster foto 2):
            // gravity diubah dari TOP|END (cocok untuk panel sempit
            // mepet-kanan sebelumnya) ke TOP|CENTER_HORIZONTAL, karena
            // layout baru berbentuk card lebar yang di referensi tampil
            // rata-tengah horizontal, bukan menempel di tepi kanan layar.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 120
        }

        val view = ComposeView(this).apply {
            setContent {
                AetherXTheme {
                    val session by GameBoosterSessionHolder.session.collectAsState()
                    session?.let { activeSession ->
                        GameBoosterSidebarContent(
                            session = activeSession,
                            actions = GameBoosterActions(
                                onModeChange = { mode -> onModeChange(mode) },
                                onDndToggle = { enabled -> onDndToggle(enabled) },
                                onFpsOverlayToggle = { enabled -> onFpsOverlayToggle(enabled) },
                                onScreenshot = { onScreenshot() },
                                onEndSession = {
                                    endSession()
                                    stopSelf()
                                },
                                onClose = { collapseSidebar() },
                                // FITUR BARU (lihat perintah rework floating
                                // booster foto 2 — tombol "Luncurkan" di
                                // card game): reuse GameLauncher.launch yang
                                // sudah menangani FLAG_ACTIVITY_NEW_TASK
                                // (wajib karena dipanggil dari Service,
                                // bukan Activity) — bukan menulis logic
                                // Intent baru yang terpisah.
                                onLaunchGame = {
                                    com.aether.x.core.apps.GameLauncher.launch(applicationContext, activeSession.packageName)
                                },
                            ),
                            modifier = Modifier.width(SIDEBAR_WIDTH_DP.dp),
                        )
                    }
                }
            }
        }
        owner.attachToDecorView(view)
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        runCatching { windowManager.addView(view, params) }
        sidebarView = view
    }

    private fun collapseSidebar() {
        sidebarView?.let { runCatching { windowManager.removeView(it) } }
        sidebarLifecycleOwner?.onDestroy()
        sidebarView = null
        sidebarLifecycleOwner = null
        showBubble()
    }

    // ============================ Aksi menu ============================

    private fun onModeChange(mode: GameMode) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(mode = mode) }
            preferences.setGameBoosterMode(mode)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyMode(executor, mode)
        }
    }

    private fun onDndToggle(enabled: Boolean) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(dndEnabled = enabled) }
            preferences.setGameBoosterDndEnabled(enabled)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyDnd(executor, enabled)
        }
    }

    private fun onFpsOverlayToggle(enabled: Boolean) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(fpsOverlayEnabled = enabled) }
            preferences.setGameBoosterFpsOverlayEnabled(enabled)
        }
    }

    private fun onScreenshot() {
        serviceScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.takeScreenshot(executor)
        }
    }

    // ============================ Notifikasi foreground ============================

    private fun buildNotification(gameLabel: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.game_booster_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager?.createNotificationChannel(channel)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aetherx_mark)
            .setContentTitle(getString(R.string.game_booster_notification_title_format, gameLabel))
            .setContentText(getString(R.string.game_booster_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViews()
        monitorJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
