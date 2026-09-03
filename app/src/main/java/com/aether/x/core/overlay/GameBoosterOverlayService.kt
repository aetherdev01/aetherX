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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.apps.GameLauncher
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.booster.GameBoosterActionHandler
import com.aether.x.core.booster.GameBoosterMonitor
import com.aether.x.core.booster.GameBoosterSession
import com.aether.x.core.booster.GameBoosterSessionHolder
import com.aether.x.core.booster.RecentAppEntry
import com.aether.x.core.monitor.RecentTasksReader
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameMode
import com.aether.x.ui.booster.GameBoosterActions
import com.aether.x.ui.booster.GameBoosterPanelContent
import com.aether.x.ui.components.showAetherToast
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

class GameBoosterOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.aether.x.action.START_GAME_BOOSTER"
        const val ACTION_STOP = "com.aether.x.action.STOP_GAME_BOOSTER"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_GAME_LABEL = "extra_game_label"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_game_booster_overlay"
        private const val NOTIFICATION_ID = 4103

        private const val EDGE_TRIGGER_WIDTH_DP = 24
        private const val PANEL_WIDTH_DP = 430
        private const val SWIPE_OPEN_THRESHOLD_PX = 60f

        private const val MAX_RAIL_APPS = 8

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
    private val recentTasksReader = RecentTasksReader()

    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: ComposeOverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var panelVisibleState: androidx.compose.runtime.MutableState<Boolean>? = null

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

        if (currentPackageName != null && currentPackageName != packageName) {
            removeOverlay()
        }
        currentPackageName = packageName

        serviceScope.launch {
            val prefs = preferences.preferences
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

            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor != null) {
                actionHandler.applyMode(executor, initial.gameBoosterMode)
                if (initial.gameBoosterDndEnabled) actionHandler.applyDnd(executor, true)
                if (initial.gameBoosterRotationLocked) actionHandler.applyRotationLock(executor, true)
                if (initial.gameBoosterTouchBoostEnabled) actionHandler.applyTouchBoost(executor, true)
            }

            showOverlay()
            startMonitor(packageName)

            val icon = GameProfileCatalog.loadIconForPackage(applicationContext, packageName)
            if (icon != null) {
                GameBoosterSessionHolder.update { it.copy(icon = icon) }
            }

            refreshRecentApps()
        }
    }

    private fun startMonitor(packageName: String) {
        monitorJob?.cancel()
        val monitor = GameBoosterMonitor(packageName)
        monitorJob = monitor.metricsFlow(applicationContext)
            .onEach { metrics -> GameBoosterSessionHolder.update { it.copy(metrics = metrics) } }
            .launchIn(serviceScope)
    }

    private fun refreshRecentApps() {
        serviceScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch

            val ownPackageName = packageName
            val packages = recentTasksReader.listRecentPackages(executor, excludingPackage = ownPackageName) ?: return@launch
            val entries = packages.take(MAX_RAIL_APPS).map { pkg ->
                RecentAppEntry(
                    packageName = pkg,
                    label = pkg.substringAfterLast('.'),
                    icon = GameProfileCatalog.loadIconForPackage(applicationContext, pkg),
                )
            }
            GameBoosterSessionHolder.update { it.copy(recentApps = entries) }
        }
    }

    private fun endSession() {
        serviceScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor != null) {
                actionHandler.applyMode(executor, GameMode.MID)
                actionHandler.applyDnd(executor, false)
            }
        }
        monitorJob?.cancel()
        GameBoosterSessionHolder.clear()
        currentPackageName = null
        removeOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val owner = ComposeOverlayLifecycleOwner()
        overlayLifecycleOwner = owner

        val displayMetrics = resources.displayMetrics
        val panelWidthPx = (PANEL_WIDTH_DP * displayMetrics.density).roundToInt()
        val edgeTriggerWidthPx = (EDGE_TRIGGER_WIDTH_DP * displayMetrics.density).roundToInt()

        val params = WindowManager.LayoutParams(
            edgeTriggerWidthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {

            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setContent {
                AetherXTheme {
                    val panelVisible = remember { mutableStateOf(false) }
                    panelVisibleState = panelVisible

                    Box(modifier = Modifier.fillMaxSize()) {

                        AnimatedVisibility(
                            visible = panelVisible.value,
                            enter = slideInHorizontally(animationSpec = tween(260)) { fullWidth -> -fullWidth } +
                                fadeIn(animationSpec = tween(200)),
                            exit = slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth } +
                                fadeOut(animationSpec = tween(160)),
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            val session by GameBoosterSessionHolder.session.collectAsState()
                            session?.let { activeSession ->
                                GameBoosterPanelContent(
                                    session = activeSession,
                                    actions = buildActions(),
                                    modifier = Modifier.padding(top = 96.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        installEdgeSwipeGesture(view, params)

        owner.attachToDecorView(view)
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        runCatching { windowManager.addView(view, params) }
        overlayView = view
    }

    private fun installEdgeSwipeGesture(view: ComposeView, params: WindowManager.LayoutParams) {
        var startX = 0f
        var opened = false
        view.setOnTouchListener { _, event ->
            if (panelVisibleState?.value == true) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    opened = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!opened && event.x - startX >= SWIPE_OPEN_THRESHOLD_PX) {
                        opened = true
                        showPanelExpanded()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun buildActions(): GameBoosterActions = GameBoosterActions(
        onModeChange = ::onModeChange,
        onDndToggle = ::onDndToggle,
        onFpsOverlayToggle = ::onFpsOverlayToggle,
        onScreenshot = ::onScreenshot,
        onEndSession = {
            endSession()
            stopSelf()
        },
        onLaunchGame = {
            currentPackageName?.let { GameLauncher.launch(applicationContext, it) }
        },
        onMinimize = { collapsePanel() },
        onOpenApp = { packageName -> GameLauncher.launch(applicationContext, packageName) },
        onRotationLockToggle = ::onRotationLockToggle,
        onMoreTools = {

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            runCatching { startActivity(intent) }
        },
    )

    private fun showPanelExpanded() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val panelWidthPx = (PANEL_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        params.width = panelWidthPx
        params.flags = baseWindowFlags()
        runCatching { windowManager.updateViewLayout(view, params) }
        panelVisibleState?.value = true
        refreshRecentApps()
    }

    private fun collapsePanel() {
        panelVisibleState?.value = false
        val params = layoutParams ?: return
        val view = overlayView ?: return
        serviceScope.launch {
            kotlinx.coroutines.delay(240)
            val edgeTriggerWidthPx = (EDGE_TRIGGER_WIDTH_DP * resources.displayMetrics.density).roundToInt()
            params.width = edgeTriggerWidthPx
            params.flags = baseWindowFlags()
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayLifecycleOwner?.onDestroy()
        overlayView = null
        overlayLifecycleOwner = null
        layoutParams = null
        panelVisibleState = null
    }

    private fun baseWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun onModeChange(mode: GameMode) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(mode = mode) }
            preferences.setGameBoosterMode(mode)
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            actionHandler.applyMode(executor, mode)
        }
    }

    private fun onDndToggle(enabled: Boolean) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(dndEnabled = enabled) }
            preferences.setGameBoosterDndEnabled(enabled)
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            actionHandler.applyDnd(executor, enabled)
        }
    }

    private fun onRotationLockToggle(locked: Boolean) {
        serviceScope.launch {
            GameBoosterSessionHolder.update { it.copy(rotationLocked = locked) }
            preferences.setGameBoosterRotationLocked(locked)
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            actionHandler.applyRotationLock(executor, locked)
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
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor == null) {
                showAetherToast(getString(R.string.game_booster_screenshot_needs_privilege))
                return@launch
            }
            val path = actionHandler.takeScreenshot(executor)
            if (path != null) {
                showAetherToast(getString(R.string.game_booster_screenshot_success))
            } else {
                showAetherToast(getString(R.string.game_booster_screenshot_failed))
            }
        }
    }

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
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, GameBoosterOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(getString(R.string.game_booster_notification_title_format, gameLabel))
            .setContentText(getString(R.string.game_booster_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.overlay_notification_action_stop), stopIntent)
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
        removeOverlay()
        monitorJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
