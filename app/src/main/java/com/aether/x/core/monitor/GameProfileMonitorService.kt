package com.aether.x.core.monitor

import android.app.ActivityManager
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
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameProfile
import android.content.pm.ApplicationInfo
import com.aether.x.data.TweakRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.aether.x.core.locale.LocaleManager
import com.aether.x.core.locale.str

class GameProfileMonitorService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }


    companion object {
        const val ACTION_START = "com.aether.x.action.START_GAME_PROFILE_MONITOR"
        const val ACTION_STOP = "com.aether.x.action.STOP_GAME_PROFILE_MONITOR"

        // Use a new channel id so users upgrading from the old IMPORTANCE_MIN
        // channel receive a visible (silent) ongoing monitor notification.
        private const val NOTIFICATION_CHANNEL_ID = "aetherx_game_profile_monitor_v2"
        private const val FPS_ALERT_CHANNEL_ID = "aetherx_game_fps_alerts"
        private const val NOTIFICATION_ID = 4104
        private const val MONITOR_EVENT_NOTIFICATION_ID = 4105
        private const val FPS_EVENT_NOTIFICATION_ID = 4106
        private const val POLL_INTERVAL_MS = 2500L
        private const val IDLE_POLL_INTERVAL_MS = 4000L
        private const val METRIC_SAMPLE_INTERVAL_MS = 5000L
        private const val GPU_SAMPLE_INTERVAL_MS = 15000L
        private const val MISSED_GAME_POLLS_TO_FINISH = 2

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
    private val systemStatsProvider = SystemStatsProvider()
    // /proc/stat can be restricted to the app UID on some ROMs. Use the same
    // already-rooted fallback as the Root Monitor when direct sampling fails.
    private val rootCpuFallback = RootProcCpuReader()

    private var activeProfilePackage: String? = null
    private var hasConfiguredProfiles = false
    private var activeRecordedGamePackage: String? = null
    private var gameMissPolls = 0
    private var lastMetricSampleAtMs = 0L
    private var lastGpuSampleAtMs = 0L
    private var cachedGpuLoadPercent: Int? = null
    private var cachedGpuFreqMhz: Float? = null
    private var activeFpsReader: GfxInfoFpsReader? = null
    private var lastFpsWasLow: Boolean? = null

    private var pollLoopStarted = false

    override fun onCreate() {
        super.onCreate()
        preferences = AetherXPreferences(applicationContext)
        GameSessionRepository.initialize(applicationContext)
        activeRecordedGamePackage = GameSessionRepository.activeSession?.packageName
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    resetActiveProfileIfAny()
                    GameSessionRepository.finishActiveSession()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                postEventNotification(
                    title = str(R.string.game_profile_monitor_service_monitor_game_aetherx_aktif),
                    message = str(R.string.game_profile_monitor_service_aetherx_siap_merekam_otomatis_saat),
                    notificationId = MONITOR_EVENT_NOTIFICATION_ID,
                )
                startPollLoop()
                return START_STICKY
            }
        }
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.game_profile_monitor_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = str(R.string.game_profile_monitor_service_notifikasi_aktif_saat_aetherx_memantau)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FPS_ALERT_CHANNEL_ID,
                str(R.string.game_profile_monitor_service_peringatan_fps_game),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = str(R.string.game_profile_monitor_service_peringatan_saat_fps_game_terlalu)
            },
        )
    }

    private fun monitorContentIntent(requestCode: Int = NOTIFICATION_ID): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(gameName: String? = null): Notification {
        ensureNotificationChannels()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(getString(R.string.game_profile_monitor_notification_title))
            .setContentText(
                gameName?.let { str(R.string.game_profile_monitor_service_sedang_merekam, it) }
                    ?: str(R.string.game_profile_monitor_service_siap_merekam_otomatis_saat_game),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(monitorContentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateOngoingNotification(gameName: String?) {
        ensureNotificationChannels()
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(gameName))
    }

    private fun postEventNotification(title: String, message: String, notificationId: Int) {
        ensureNotificationChannels()
        val notification = NotificationCompat.Builder(this, FPS_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(monitorContentIntent(notificationId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    private fun reportFpsStatus(gameName: String, fps: Int) {
        when {
            fps < 30 && lastFpsWasLow != true -> {
                lastFpsWasLow = true
                postEventNotification(
                    title = str(R.string.game_profile_monitor_service_fps_terlalu_rendah),
                    message = str(R.string.game_profile_monitor_service_berjalan_pada_fps_performa_sedang, gameName, fps),
                    notificationId = FPS_EVENT_NOTIFICATION_ID,
                )
            }
            fps >= 45 && lastFpsWasLow == true -> {
                lastFpsWasLow = false
                postEventNotification(
                    title = str(R.string.game_profile_monitor_service_fps_kembali_stabil),
                    message = str(R.string.game_profile_monitor_service_sekarang_mencapai_fps, gameName, fps),
                    notificationId = FPS_EVENT_NOTIFICATION_ID,
                )
            }
            lastFpsWasLow == null && fps >= 30 -> lastFpsWasLow = false
        }
    }

    private fun startPollLoop() {
        if (pollLoopStarted) return
        pollLoopStarted = true

        serviceScope.launch {

            activeProfilePackage = preferences.getActiveGameProfilePackage()
            hasConfiguredProfiles = preferences.getGameProfiles().isNotEmpty()

            while (true) {
                pollOnce()
                val isBusy = hasConfiguredProfiles || GameSessionRepository.activeSession != null
                delay(if (isBusy) POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce() {

        if (!PrivilegeManager.status.value.rootGranted) {
            resetActiveProfileIfAny()
            GameSessionRepository.finishActiveSession()
            stopSelf()
            return
        }

        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return

        val foregroundPackage = foregroundAppReader.readForegroundPackage(executor)
        trackGameSession(executor, foregroundPackage)

        val profiles = preferences.getGameProfiles()
        hasConfiguredProfiles = profiles.isNotEmpty()
        if (profiles.isEmpty()) {
            // Perekaman riwayat tetap berjalan walau user belum membuat profil tweak.
            val current = activeProfilePackage
            if (current != null) {
                repository.resetRootTweaksOnly(executor, packageName = current)
                activeProfilePackage = null
                preferences.setActiveGameProfilePackage(null)
            }
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

    /**
     * Rekam sesi game saat paket katalog berada di foreground. FPS gfxinfo dicoba
     * tiap 5 detik, sedangkan pembacaan GPU lewat root dibatasi tiap 15 detik.
     * Saat idle dan tanpa profil tweak, deteksi foreground dijalankan lebih jarang.
     */
    private suspend fun trackGameSession(executor: ShellExecutor, foregroundPackage: String?) {
        val candidate = foregroundPackage?.takeIf { it != packageName }
        val isGame = candidate != null && runCatching {
            GameProfileCatalog.isKnownGamePackage(applicationContext, candidate)
        }.getOrDefault(false)

        if (!isGame || candidate == null) {
            if (GameSessionRepository.activeSession != null) {
                gameMissPolls++
                if (gameMissPolls >= MISSED_GAME_POLLS_TO_FINISH) {
                    GameSessionRepository.finishActiveSession()
                    activeRecordedGamePackage = null
                    activeFpsReader = null
                    lastFpsWasLow = null
                    updateOngoingNotification(null)
                    gameMissPolls = 0
                    lastMetricSampleAtMs = 0L
                }
            }
            return
        }

        gameMissPolls = 0
        if (activeRecordedGamePackage != candidate || GameSessionRepository.activeSession == null) {
            val gameName = runCatching {
                val info: ApplicationInfo = packageManager.getApplicationInfo(candidate, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(candidate)
            GameSessionRepository.startSession(applicationContext, candidate, gameName)
            activeRecordedGamePackage = candidate
            activeFpsReader = GfxInfoFpsReader(candidate)
            lastFpsWasLow = null
            lastMetricSampleAtMs = 0L
            cachedGpuLoadPercent = null
            cachedGpuFreqMhz = null
            lastGpuSampleAtMs = 0L
            rootCpuFallback.reset()
            updateOngoingNotification(gameName)
            postEventNotification(
                title = str(R.string.game_profile_monitor_service_mulai_merekam_game),
                message = str(R.string.game_profile_monitor_service_aetherx_mulai_merekam_sesi_secara, gameName),
                notificationId = MONITOR_EVENT_NOTIFICATION_ID,
            )
        }

        val now = System.currentTimeMillis()
        if (now - lastMetricSampleAtMs < METRIC_SAMPLE_INTERVAL_MS) return
        lastMetricSampleAtMs = now

        val fps = runCatching { activeFpsReader?.readFps(executor) }.getOrNull()
        val gameName = GameSessionRepository.activeSession?.gameName ?: candidate
        if (fps != null) reportFpsStatus(gameName, fps)
        val directCpu = systemStatsProvider.readCpuLoadPercent()
        val cpu = directCpu ?: runCatching {
            rootCpuFallback.read()?.aggregatePercent
                ?.takeIf { it >= 0f }
                ?.roundToInt()
        }.getOrNull()
        val temperature = systemStatsProvider.readTemperatureCelsius(this)
        val gpuReadableDirectly = systemStatsProvider.readGpuLoadPercent()

        if (now - lastGpuSampleAtMs >= GPU_SAMPLE_INTERVAL_MS) {
            lastGpuSampleAtMs = now
            runCatching { RootSystemMonitor.readGpuSnapshotViaRoot() }.getOrNull()?.let { snapshot ->
                snapshot.loadPercent?.roundToInt()?.takeIf { it in 0..100 }?.let { cachedGpuLoadPercent = it }
                snapshot.freqMhz?.let { cachedGpuFreqMhz = it }
            }
        }
        val gpuLoad = gpuReadableDirectly ?: cachedGpuLoadPercent
        val memoryInfo = ActivityManager.MemoryInfo()
        val ramUsedMb = runCatching {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)
            ((memoryInfo.totalMem - memoryInfo.availMem).coerceAtLeast(0L) / (1024L * 1024L))
        }.getOrNull()

        GameSessionRepository.recordSample(
            GameSessionSample(
                timestampMs = now,
                fps = fps,
                cpuPercent = cpu,
                gpuPercent = gpuLoad,
                gpuFreqMhz = cachedGpuFreqMhz,
                temperatureC = temperature,
                ramUsedMb = ramUsedMb,
            ),
        )
    }

    private suspend fun resetActiveProfileIfAny() {
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
