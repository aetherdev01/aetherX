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

/**
 * Foreground service KHUSUS ROOT yang memantau aplikasi foreground secara
 * berkala dan otomatis menerapkan/mereset tweak [GameProfile] sesuai game
 * yang sedang dimainkan pengguna:
 *
 * - Game yang punya profil tersimpan TERDETEKSI DIBUKA (foreground) -> tweak
 *   root profil itu langsung diterapkan lewat [TweakRepository.applyGameProfile].
 * - Game yang profilnya sedang aktif DITUTUP DARI RECENT APPS (task-nya
 *   sudah tidak ada sama sekali di `dumpsys activity activities`, dicek
 *   lewat [RecentTasksReader]) -> tweak root direset ke default lewat
 *   [TweakRepository.resetRootTweaksOnly].
 * - Sekadar pindah ke Home/aplikasi lain SEMENTARA (game masih hidup di
 *   background, masih ada di recent apps) TIDAK mereset apa pun — supaya
 *   tweak tidak nyala-mati setiap kali pengguna melirik notifikasi/chat
 *   sebentar lalu kembali main.
 *
 * Berbeda dari [com.aether.x.core.overlay.FpsMonitorOverlayService] dkk.,
 * service ini TIDAK menggambar apa pun di layar — murni polling shell di
 * background, jadi tetap wajib berjalan sebagai foreground service (dengan
 * notifikasi persisten prioritas minimum) sesuai aturan Android modern
 * untuk proses yang perlu terus hidup di background.
 */
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

    /** Package yang profilnya sedang diterapkan saat ini, atau null kalau tidak ada. */
    private var activeProfilePackage: String? = null
    // FITUR BARU — dilacak TERPISAH dari activeProfilePackage, lihat KDoc
    // handleGameBoosterAutoTrigger soal kenapa keduanya independen.
    private var activeBoosterPackage: String? = null
    private var pollLoopStarted = false

    override fun onCreate() {
        super.onCreate()
        preferences = AetherXPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Kalau service dihentikan (mis. pengguna keluar dari Root,
                // atau app ditutup) sementara sebuah profil masih aktif,
                // reset dulu tweak root-nya supaya tidak "nyangkut" menyala
                // tanpa ada yang memantau untuk mematikannya lagi nanti.
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
            // Pulihkan status aktif dari preferences dulu — kalau proses
            // service sempat mati/di-restart sistem sementara sebuah game
            // masih terbuka, jangan sampai tweaknya "lupa" sedang aktif.
            activeProfilePackage = preferences.getActiveGameProfilePackage()

            while (true) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce() {
        // Game Profile murni fitur khusus Root (lihat perintah pengguna) —
        // kalau backend aktif bukan Root (mis. pengguna beralih ke Shizuku
        // atau belum ada akses sama sekali), hentikan diri sendiri daripada
        // terus polling tanpa guna, dan pastikan tweak profil yang mungkin
        // masih aktif direset dulu. Game Booster (yang JUGA di-trigger dari
        // sini, lihat di bawah) tetap bisa diakses MANUAL dari drawer tanpa
        // root — hanya AUTO-TRIGGER-nya yang ikut terhenti bersama service
        // khusus-root ini.
        if (PrivilegeManager.status.value.activeBackend != PrivilegeBackend.ROOT) {
            resetActiveProfileIfAny()
            stopSelf()
            return
        }

        val executor = PrivilegeManager.getExecutor() ?: return

        // REWORK (lihat perintah rework — "saat buka game game booster jadi
        // side bar/floating"): foreground package SEKARANG SELALU dibaca di
        // awal (bukan lagi di-skip kalau profiles.isEmpty()) karena
        // sekarang dipakai DUA keperluan independen: Game Profile (di bawah,
        // HANYA untuk game yang profilnya sudah dikustomisasi) dan Game
        // Booster auto-trigger (untuk SEMUA game yang dikenal gamelist.txt,
        // terlepas dari ada/tidaknya Game Profile).
        val foregroundPackage = foregroundAppReader.readForegroundPackage(executor)

        handleGameBoosterAutoTrigger(foregroundPackage)

        val profiles = preferences.getGameProfiles()
        if (profiles.isEmpty()) {
            // Tidak ada profil tersimpan sama sekali — tidak ada yang perlu
            // dipantau UNTUK GAME PROFILE (Game Booster di atas tetap jalan
            // terlepas dari ini). Biarkan service tetap hidup (murah secara
            // resource, cukup satu dumpsys tiap 2.5 detik) supaya begitu
            // pengguna menyimpan profil baru dari layar Game Profile,
            // deteksi langsung berjalan tanpa perlu restart service manual.
            return
        }

        val current = activeProfilePackage

        when {
            // Kasus 1: game BARU dengan profil tersimpan baru saja terdeteksi
            // di foreground, dan belum ada profil lain yang sedang aktif
            // (atau profil aktif sebelumnya BEDA package) -> terapkan.
            foregroundPackage != null && profiles.containsKey(foregroundPackage) && foregroundPackage != current -> {
                // Kalau sebelumnya ada profil game LAIN yang aktif (mis.
                // pengguna pindah dari Genshin ke HSR tanpa menutup dulu),
                // reset dulu profil lama sebelum menerapkan yang baru supaya
                // tweak tidak tercampur antar game.
                if (current != null) {
                    repository.resetRootTweaksOnly(executor, packageName = current)
                }
                profiles[foregroundPackage]?.let { profile ->
                    repository.applyGameProfile(executor, profile)
                }
                activeProfilePackage = foregroundPackage
                preferences.setActiveGameProfilePackage(foregroundPackage)
            }

            // Kasus 2: ada profil yang sedang aktif, tapi game itu tidak lagi
            // di foreground -> cek dulu apakah masih hidup di recent apps
            // (sekadar di-minimize) atau benar-benar sudah ditutup.
            current != null && foregroundPackage != current -> {
                val stillInRecents = recentTasksReader.isPackageInRecentTasks(executor, current)
                if (stillInRecents == false) {
                    repository.resetRootTweaksOnly(executor, packageName = current)
                    activeProfilePackage = null
                    preferences.setActiveGameProfilePackage(null)
                }
                // stillInRecents == true -> masih di-minimize, biarkan tweak
                // tetap aktif. stillInRecents == null -> perintah shell
                // gagal sementara, jangan simpulkan apa pun dulu, coba lagi
                // di siklus poll berikutnya.
            }
        }
    }

    /**
     * FITUR BARU — auto-trigger Game Booster (lihat perintah rework: "saat
     * buka game game booster jadi side bar/floating dan gampang diakses"):
     * dipanggil SETIAP siklus poll (independen dari logic Game Profile di
     * atas). Menyalakan [GameBoosterOverlayService] otomatis begitu SEMBARANG
     * game yang dikenal `gamelist.txt` ([GameProfileCatalog.isKnownGamePackage])
     * terdeteksi di foreground, dan menghentikannya begitu game itu benar-
     * benar ditutup dari recent apps (BUKAN sekadar di-minimize sebentar —
     * pola cek yang SAMA seperti Kasus 2 Game Profile di atas, lewat
     * [RecentTasksReader], supaya sidebar tidak nyala-mati tiap kali
     * pengguna melirik notifikasi sebentar).
     *
     * [activeBoosterPackage] sengaja dilacak TERPISAH dari [activeProfilePackage]
     * (variabel Game Profile) karena keduanya independen — sebuah game bisa
     * saja punya sesi Game Booster aktif TANPA punya Game Profile
     * tersimpan sama sekali, atau sebaliknya jarang terjadi tapi mestinya
     * tetap tidak saling mengganggu satu sama lain.
     */
    private suspend fun handleGameBoosterAutoTrigger(foregroundPackage: String?) {
        // NONAKTIF SEMENTARA (lihat perintah: "sekarang sementara jangan
        // tampilkan floating gamebooster nya saat buka game") — lihat KDoc
        // GameBoosterFeatureFlag.autoTriggerOnGameOpenEnabled untuk cakupan
        // persis apa yang dimatikan (HANYA jalur otomatis ini, bukan alur
        // manual dari drawer Game Booster AetherX). return awal di sini
        // membiarkan SELURUH logic start/stop di bawah tidak pernah
        // berjalan sama sekali selama flag ini false — tidak perlu
        // menghapus/mengomentari kode apa pun, tinggal balikkan flag ke
        // true nanti untuk mengaktifkan lagi.
        if (!GameBoosterFeatureFlag.autoTriggerOnGameOpenEnabled) return

        val executor = PrivilegeManager.getExecutor() ?: return
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

    /** Reset tweak profil yang sedang aktif (kalau ada) — dipakai saat service dihentikan. */
    private suspend fun resetActiveProfileIfAny() {
        if (activeBoosterPackage != null) {
            GameBoosterOverlayService.stop(applicationContext)
            activeBoosterPackage = null
        }
        val current = activeProfilePackage ?: preferences.getActiveGameProfilePackage() ?: return
        val executor: ShellExecutor = PrivilegeManager.getExecutor() ?: return
        repository.resetRootTweaksOnly(executor, packageName = current)
        activeProfilePackage = null
        preferences.setActiveGameProfilePackage(null)
        // current sengaja tidak dipakai lagi di sini selain sebagai penanda
        // "ada sesuatu untuk direset" — tidak perlu logging tambahan.
    }

    override fun onDestroy() {
        pollLoopStarted = false
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
