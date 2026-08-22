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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v3.1 PURE ROOT: AetherX sekarang HANYA mendukung Root (Magisk/KernelSU/
 * APatch). Shizuku dan seluruh mode non-root (termasuk ADB tertanam yang
 * sebelumnya juga sudah pernah dihapus) DIHAPUS TOTAL dari aplikasi — tidak
 * ada lagi ShizukuManager, ShizukuShellExecutor, WirelessDebuggingMonitor,
 * atau preferensi "backend" apa pun. Satu-satunya sumber privilege adalah
 * pengecekan root lewat libsu ([Shell.getShell]).
 */
object PrivilegeManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<RequestFeedback>(extraBufferCapacity = 1)
    val events: SharedFlow<RequestFeedback> = _events.asSharedFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        checkRootSilently()
    }

    fun toOverlayErrorMessage(context: Context, reason: RequestFailureReason): String = context.getString(
        when (reason) {
            RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE -> com.aether.x.R.string.permission_feedback_root_denied
            RequestFailureReason.ROOT_ALREADY_IN_PROGRESS -> com.aether.x.R.string.permission_feedback_root_in_progress
        },
    )

    fun refreshAll() {
        checkRootSilently()
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
            _events.tryEmit(RequestFeedback.Failed(RequestFailureReason.ROOT_ALREADY_IN_PROGRESS))
            return
        }

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
                    RequestFeedback.Granted
                } else {
                    RequestFeedback.Failed(RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE)
                },
            )
        }
    }

    fun getExecutor(): ShellExecutor? = if (status.value.rootGranted) RootShellExecutor() else null

    /**
     * Versi `suspend` dipakai oleh semua pemanggil tweak (TweakViewModel,
     * AppManagerViewModel, dkk) — dipertahankan sebagai fungsi terpisah
     * supaya signature caller tetap sama seperti sebelumnya. Root tidak
     * butuh proses "menunggu koneksi" apa pun (beda dari Shizuku/ADB yang
     * sudah dihapus), jadi versi suspend ini murni membaca status root
     * yang sudah ada tanpa delay atau retry tambahan.
     */
    suspend fun getExecutorAwaitingConnection(): ShellExecutor? =
        if (status.value.rootGranted) RootShellExecutor() else null

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
