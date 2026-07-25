package com.aether.x.core.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.aether.x.core.shell.RootShellExecutor
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShizukuShellExecutor
import com.aether.x.core.shizuku.ShizukuConnectionState
import com.aether.x.core.shizuku.ShizukuManager
import com.aether.x.data.AetherXPreferences
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ROLLBACK TOTAL (lihat perintah — "jadikan sistem adb kembali ke shizuku
 * pure... hapus semua yang bersangkutan dengan adb tertanam"): seluruh
 * logic ADB tertanam (pairing, host/port, AdbConnectionManager) DIHAPUS.
 * Backend non-root sekarang murni [ShizukuManager] — lihat KDoc di sana
 * untuk konteks lengkap kenapa rollback ini dilakukan.
 */
object PrivilegeManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<RequestFeedback>(extraBufferCapacity = 1)
    val events: SharedFlow<RequestFeedback> = _events.asSharedFlow()

    private val adoptMutex = Mutex()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        ShizukuManager.init(appContext)

        ShizukuManager.state.onEach { shizukuState ->
            val wasConnected = _status.value.shizukuGranted
            _status.update { it.copy(shizukuState = shizukuState) }

            if (shizukuState == ShizukuConnectionState.Connected && !wasConnected) {
                _events.tryEmit(RequestFeedback.Granted(PrivilegeBackend.SHIZUKU))
            } else if (shizukuState != ShizukuConnectionState.Connected && wasConnected) {
                // Service Shizuku baru saja mati/binder putus setelah
                // sebelumnya tersambung — bukan kegagalan permintaan aktif
                // (tidak ada REQUESTING di Shizuku, semua state berubah
                // instan lewat listener), jadi tidak perlu emit Failed di
                // sini, cukup status yang diupdate untuk UI.
            } else if (shizukuState == ShizukuConnectionState.PermissionDenied) {
                _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.SHIZUKU, RequestFailureReason.SHIZUKU_PERMISSION_DENIED))
            }
        }.launchIn(scope)

        checkRootSilently()

        val preferences = AetherXPreferences(appContext)
        scope.launch {
            val saved = preferences.getPreferredPrivilegeBackend()
            val backend = when (saved) {
                "SHIZUKU", "ADB" -> PrivilegeBackend.SHIZUKU
                "ROOT" -> PrivilegeBackend.ROOT
                else -> PrivilegeBackend.NONE
            }
            _status.update { it.copy(preferredBackend = backend) }

            if (backend == PrivilegeBackend.NONE) {
                delay(1500)
                adoptExistingGrantIfNoPreference(appContext)
            }
        }
    }

    fun toOverlayErrorMessage(context: Context, reason: RequestFailureReason): String = context.getString(
        when (reason) {
            RequestFailureReason.SHIZUKU_NOT_INSTALLED -> com.aether.x.R.string.permission_feedback_shizuku_not_installed
            RequestFailureReason.SHIZUKU_SERVICE_NOT_RUNNING -> com.aether.x.R.string.permission_feedback_shizuku_service_not_running
            RequestFailureReason.SHIZUKU_PERMISSION_DENIED -> com.aether.x.R.string.permission_feedback_shizuku_permission_denied
            RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE -> com.aether.x.R.string.permission_feedback_root_denied
            RequestFailureReason.ROOT_ALREADY_IN_PROGRESS -> com.aether.x.R.string.permission_feedback_root_in_progress
        },
    )

    fun adoptExistingGrantIfNoPreference(context: Context) {
        scope.launch {
            adoptMutex.withLock {
                if (_status.value.preferredBackend != PrivilegeBackend.NONE) return@withLock

                val backend = when {
                    _status.value.shizukuGranted -> PrivilegeBackend.SHIZUKU
                    _status.value.rootGranted -> PrivilegeBackend.ROOT
                    else -> null
                } ?: return@withLock

                selectBackend(context, backend)
            }
        }
    }

    fun selectBackend(context: Context, backend: PrivilegeBackend) {
        require(backend != PrivilegeBackend.NONE) { "Gunakan clearBackendPreference() untuk mereset pilihan." }
        _status.update { it.copy(preferredBackend = backend) }
        val preferences = AetherXPreferences(context.applicationContext)
        scope.launch {
            preferences.setPreferredPrivilegeBackend(if (backend == PrivilegeBackend.SHIZUKU) "SHIZUKU" else "ROOT")
        }
    }

    fun clearBackendPreference(context: Context) {
        _status.update { it.copy(preferredBackend = PrivilegeBackend.NONE) }
        val preferences = AetherXPreferences(context.applicationContext)
        scope.launch { preferences.clearPreferredPrivilegeBackend() }
    }

    /** Refresh status Shizuku manual — dipanggil dari layar Izin Akses
     *  saat ON_RESUME (mis. pengguna baru kembali dari app Shizuku Manager
     *  setelah start service/pairing di sana) dan dari tombol refresh
     *  eksplisit di [com.aether.x.ui.components.ShizukuCard]. */
    fun refreshShizuku() {
        ShizukuManager.refresh()
    }

    fun openShizukuManager(context: Context) {
        selectBackend(context, PrivilegeBackend.SHIZUKU)
        ShizukuManager.openShizukuManager(context)
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission()
    }

    fun refreshAll() {
        ShizukuManager.refresh()
        checkRootSilently()
    }

    /** Dipakai oleh `WirelessDebuggingQuickCard` (pengingat prasyarat umum
     *  — banyak metode start Shizuku yang paling mudah, mis. lewat ADB
     *  wireless sekali jalan, butuh Wireless debugging aktif dulu di Opsi
     *  Developer). TIDAK terkait sistem pairing ADB tertanam yang sudah
     *  dihapus — murni shortcut ke pengaturan sistem Android biasa. */
    fun openWirelessDebuggingSettings(context: Context) {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(fallback) }
    }

    fun checkRootSilently() {
        scope.launch {
            _status.update { it.copy(checkingRoot = true) }
            val granted = withContext(Dispatchers.IO) {
                try {
                    Shell.getShell().isRoot
                } catch (t: Throwable) {
                    false
                }
            }
            _status.update { it.copy(rootAvailable = granted, rootGranted = granted, checkingRoot = false) }
        }
    }

    fun requestRoot(context: Context? = null) {
        if (_status.value.rootRequestState == RequestState.REQUESTING) {
            _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.ROOT, RequestFailureReason.ROOT_ALREADY_IN_PROGRESS))
            return
        }

        context?.let { selectBackend(it, PrivilegeBackend.ROOT) }
        scope.launch {
            _status.update { it.copy(checkingRoot = true, rootRequestState = RequestState.REQUESTING) }
            val granted = withContext(Dispatchers.IO) {
                try {
                    Shell.getShell().isRoot
                } catch (t: Throwable) {
                    false
                }
            }
            _status.update {
                it.copy(
                    rootAvailable = granted,
                    rootGranted = granted,
                    checkingRoot = false,
                    rootRequestState = RequestState.IDLE,
                )
            }
            _events.tryEmit(
                if (granted) {
                    RequestFeedback.Granted(PrivilegeBackend.ROOT)
                } else {
                    RequestFeedback.Failed(PrivilegeBackend.ROOT, RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE)
                },
            )
        }
    }

    fun getExecutor(): ShellExecutor? = when (status.value.activeBackend) {
        PrivilegeBackend.SHIZUKU -> ShizukuShellExecutor()
        PrivilegeBackend.ROOT -> RootShellExecutor()
        PrivilegeBackend.NONE -> null
    }

    /**
     * Versi `suspend` dipakai oleh semua pemanggil tweak (TweakViewModel,
     * dkk) — beda dari [getExecutor] biasa, dipertahankan sebagai fungsi
     * terpisah supaya signature caller tetap sama seperti sebelumnya
     * (tidak perlu ubah seluruh ViewModel tweak). Untuk Shizuku TIDAK ada
     * proses "menunggu reconnect" seperti ADB tertanam dulu — binder
     * Shizuku begitu hidup langsung siap dipakai instan, jadi versi
     * suspend ini cukup baca [ShizukuManager] sinkron tanpa perlu delay
     * atau retry apa pun.
     */
    suspend fun getExecutorAwaitingConnection(): ShellExecutor? {
        val current = status.value
        return when (current.preferredBackend) {
            PrivilegeBackend.SHIZUKU -> {
                ShizukuManager.refresh()
                if (ShizukuManager.isServiceRunning() && ShizukuManager.hasPermission()) {
                    ShizukuShellExecutor()
                } else {
                    null
                }
            }
            PrivilegeBackend.ROOT -> if (current.rootGranted) RootShellExecutor() else null
            PrivilegeBackend.NONE -> when {
                current.shizukuGranted -> ShizukuShellExecutor()
                current.rootGranted -> RootShellExecutor()
                else -> null
            }
        }
    }

    fun refreshSupportingPermissions(context: Context) {
        val writeSettings = Settings.System.canWrite(context)
        val overlay = Settings.canDrawOverlays(context)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _status.update {
            it.copy(
                writeSettingsGranted = writeSettings,
                overlayGranted = overlay,
                notificationsGranted = notifications,
            )
        }
    }

    fun requestWriteSettings(context: Context) {
        if (Settings.System.canWrite(context)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun requestOverlayPermission(context: Context) {
        if (Settings.canDrawOverlays(context)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations(context)) return
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(fallback) }
    }
}
