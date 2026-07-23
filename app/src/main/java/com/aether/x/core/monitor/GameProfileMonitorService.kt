package com.aether.x.core.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.booster.GameBoosterFeatureFlag
import com.aether.x.core.overlay.GameBoosterOverlayService
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameProfile
import com.aether.x.data.TweakRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameProfileMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.aether.x.action.START_GAME_PROFILE_MONITOR"
        const val ACTION_STOP = "com.aether.x.action.STOP_GAME_PROFILE_MONITOR"

        private const val NOTIFICATION_CHANNEL_ID = "aetherx_game_profile_monitor"
        private const val NOTIFICATION_ID = 4104
        private const val POLL_INTERVAL_MS = 2500L

        fun start(context: Context) {
            val intent = Intent(context, GameProfileMonitorService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GameProfileMonitorService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private lateinit var preferences: AetherXPreferences
    private val repository = TweakRepository()
    private val foregroundAppReader = ForegroundAppReader()
    private val recentTasksReader = RecentTasksReader()

    private var activeProfilePackage: String? = null

    private var activeBoosterPackage: String? = null
    private var pollLoopStarted = false

    override fun onCreate() {
        super.onCreate()
        preferences = AetherXPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {

                serviceScope.launch {
                    resetActiveProfileIfAny()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startPollLoop()
                return START_STICKY
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.game_profile_monitor_notification_channel),
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
            .setContentTitle(getString(R.string.game_profile_monitor_notification_title))
            .setContentText(getString(R.string.game_profile_monitor_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun startPollLoop() {
        if (pollLoopStarted) return
        pollLoopStarted = true

        serviceScope.launch {

            activeProfilePackage = preferences.getActiveGameProfilePackage()

            while (true) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce() {

        if (PrivilegeManager.status.value.activeBackend != PrivilegeBackend.ROOT) {
            resetActiveProfileIfAny()
            stopSelf()
            return
        }

        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return

        val foregroundPackage = foregroundAppReader.readForegroundPackage(executor)

        handleGameBoosterAutoTrigger(foregroundPackage)

        val profiles = preferences.getGameProfiles()
        if (profiles.isEmpty()) {

            return
        }

        val current = activeProfilePackage

        when {

            foregroundPackage != null && profiles.containsKey(foregroundPackage) && foregroundPackage != current -> {

                if (current != null) {
                    repository.resetRootTweaksOnly(executor, packageName = current)
                }
                profiles[foregroundPackage]?.let { profile ->
                    repository.applyGameProfile(executor, profile)
                }
                activeProfilePackage = foregroundPackage
                preferences.setActiveGameProfilePackage(foregroundPackage)
            }

            current != null && foregroundPackage != current -> {
                val stillInRecents = recentTasksReader.isPackageInRecentTasks(executor, current)
                if (stillInRecents == false) {
                    repository.resetRootTweaksOnly(executor, packageName = current)
                    activeProfilePackage = null
                    preferences.setActiveGameProfilePackage(null)
                }

            }
        }
    }

    private suspend fun handleGameBoosterAutoTrigger(foregroundPackage: String?) {

        if (!GameBoosterFeatureFlag.autoTriggerOnGameOpenEnabled) return

        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return
        val current = activeBoosterPackage

        when {
            foregroundPackage != null &&
                foregroundPackage != current &&
                GameProfileCatalog.isKnownGamePackage(applicationContext, foregroundPackage) -> {
                val label = runCatching {
                    applicationContext.packageManager
                        .getApplicationLabel(applicationContext.packageManager.getApplicationInfo(foregroundPackage, 0))
                        .toString()
                }.getOrDefault(foregroundPackage)
                GameBoosterOverlayService.start(applicationContext, foregroundPackage, label)
                activeBoosterPackage = foregroundPackage
            }

            current != null && foregroundPackage != current -> {
                val stillInRecents = recentTasksReader.isPackageInRecentTasks(executor, current)
                if (stillInRecents == false) {
                    GameBoosterOverlayService.stop(applicationContext)
                    activeBoosterPackage = null
                }
            }
        }
    }

    private suspend fun resetActiveProfileIfAny() {
        if (activeBoosterPackage != null) {
            GameBoosterOverlayService.stop(applicationContext)
            activeBoosterPackage = null
        }
        val current = activeProfilePackage ?: preferences.getActiveGameProfilePackage() ?: return
        val executor: ShellExecutor = PrivilegeManager.getExecutorAwaitingConnection() ?: return
        repository.resetRootTweaksOnly(executor, packageName = current)
        activeProfilePackage = null
        preferences.setActiveGameProfilePackage(null)

    }

    override fun onDestroy() {
        pollLoopStarted = false
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
