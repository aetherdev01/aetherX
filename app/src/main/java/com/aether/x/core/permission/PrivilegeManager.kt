package com.aether.x.core.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.aether.x.core.adb.AdbConnectionManager
import com.aether.x.core.adb.AdbConnectionState
import com.aether.x.core.adb.AdbFailureReason
import com.aether.x.core.notification.AdbPairingNotifier
import com.aether.x.core.shell.EmbeddedShellExecutor
import com.aether.x.core.shell.RootShellExecutor
import com.aether.x.core.shell.ShellExecutor
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
        AdbConnectionManager.init(appContext)

        AdbConnectionManager.state.onEach { adbState ->
            val wasRequesting = _status.value.adbRequestState == RequestState.REQUESTING
            _status.update {
                it.copy(
                    adbState = adbState,

                    adbRequestState = when (adbState) {
                        is AdbConnectionState.Pairing,
                        is AdbConnectionState.Connecting,
                        is AdbConnectionState.SearchingForPairing,
                        is AdbConnectionState.PairingFound,
                        -> RequestState.REQUESTING
                        else -> RequestState.IDLE
                    },
                )
            }
            if (wasRequesting) {
                when (adbState) {
                    is AdbConnectionState.Connected -> _events.tryEmit(RequestFeedback.Granted(PrivilegeBackend.ADB))
                    is AdbConnectionState.Failed -> _events.tryEmit(
                        RequestFeedback.Failed(PrivilegeBackend.ADB, adbState.reason.toRequestFailureReason()),
                    )
                    else -> Unit
                }
            }

            when (adbState) {
                is AdbConnectionState.SearchingForPairing -> AdbPairingNotifier.showSearching(appContext)
                is AdbConnectionState.PairingFound -> AdbPairingNotifier.showCodeInput(appContext)
                is AdbConnectionState.Pairing -> AdbPairingNotifier.showBusy(appContext)
                is AdbConnectionState.Connecting -> {
                    if (wasRequesting) AdbPairingNotifier.showBusy(appContext)
                }
                is AdbConnectionState.Failed -> {

                    if (wasRequesting) {
                        val message = adbState.reason.toOverlayErrorMessage(appContext)
                        AdbPairingNotifier.showError(appContext, message)
                    }
                }
                is AdbConnectionState.Connected, AdbConnectionState.NotPaired, AdbConnectionState.PairedNotConnected ->
                    AdbPairingNotifier.stop(appContext)
            }
        }.launchIn(scope)

        checkRootSilently()

        val preferences = AetherXPreferences(appContext)
        scope.launch {
            val saved = preferences.getPreferredPrivilegeBackend()
            val backend = when (saved) {

                "SHIZUKU", "ADB" -> PrivilegeBackend.ADB
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

    private fun AdbFailureReason.toRequestFailureReason(): RequestFailureReason = when (this) {
        AdbFailureReason.WIRELESS_DEBUGGING_OFF -> RequestFailureReason.ADB_WIRELESS_DEBUGGING_OFF
        AdbFailureReason.AUTO_DISCOVERY_TIMEOUT -> RequestFailureReason.ADB_AUTO_DISCOVERY_TIMEOUT
        AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED -> RequestFailureReason.ADB_PAIRING_CODE_INVALID_OR_EXPIRED
        AdbFailureReason.HOST_UNREACHABLE -> RequestFailureReason.ADB_HOST_UNREACHABLE
        AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED -> RequestFailureReason.ADB_CONNECT_AFTER_PAIRING_FAILED
        AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR -> RequestFailureReason.ADB_SHELL_REJECTED_NEEDS_REPAIR
        AdbFailureReason.UNKNOWN -> RequestFailureReason.ADB_UNKNOWN
    }

    private fun AdbFailureReason.toOverlayErrorMessage(context: Context): String = context.getString(
        when (this) {
            AdbFailureReason.WIRELESS_DEBUGGING_OFF -> com.aether.x.R.string.permission_feedback_adb_wireless_debugging_off
            AdbFailureReason.AUTO_DISCOVERY_TIMEOUT -> com.aether.x.R.string.permission_feedback_adb_auto_discovery_timeout
            AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED -> com.aether.x.R.string.permission_feedback_adb_pairing_invalid
            AdbFailureReason.HOST_UNREACHABLE -> com.aether.x.R.string.permission_feedback_adb_host_unreachable
            AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED -> com.aether.x.R.string.permission_feedback_adb_connect_after_pairing_failed
            AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR -> com.aether.x.R.string.permission_feedback_adb_shell_rejected
            AdbFailureReason.UNKNOWN -> com.aether.x.R.string.permission_feedback_adb_unknown
        },
    )

    fun adoptExistingGrantIfNoPreference(context: Context) {
        scope.launch {
            adoptMutex.withLock {
                if (_status.value.preferredBackend != PrivilegeBackend.NONE) return@withLock

                val backend = when {
                    _status.value.adbGranted -> PrivilegeBackend.ADB
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
            preferences.setPreferredPrivilegeBackend(if (backend == PrivilegeBackend.ADB) "ADB" else "ROOT")
        }
    }

    fun clearBackendPreference(context: Context) {
        _status.update { it.copy(preferredBackend = PrivilegeBackend.NONE) }
        val preferences = AetherXPreferences(context.applicationContext)
        scope.launch { preferences.clearPreferredPrivilegeBackend() }
    }

    fun pairAdb(
        context: Context,
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int,
    ) {
        if (_status.value.adbRequestState == RequestState.REQUESTING) {
            _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.ADB, RequestFailureReason.ADB_ALREADY_IN_PROGRESS))
            return
        }
        selectBackend(context, PrivilegeBackend.ADB)
        scope.launch {
            AdbConnectionManager.pair(
                pairingHost = pairingHost,
                pairingPort = pairingPort,
                pairingCode = pairingCode,
                connectPort = connectPort,
            )
        }
    }

    fun reconnectAdb(context: Context) {
        if (_status.value.adbRequestState == RequestState.REQUESTING) {
            _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.ADB, RequestFailureReason.ADB_ALREADY_IN_PROGRESS))
            return
        }
        selectBackend(context, PrivilegeBackend.ADB)
        AdbConnectionManager.autoReconnect()
    }

    fun startAutoPairAdb(context: Context) {
        if (_status.value.adbRequestState == RequestState.REQUESTING) {
            _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.ADB, RequestFailureReason.ADB_ALREADY_IN_PROGRESS))
            return
        }
        selectBackend(context, PrivilegeBackend.ADB)
        AdbConnectionManager.startAutoPairing(context)
        openWirelessDebuggingSettings(context)
    }

    fun cancelAutoPairAdb() {
        AdbConnectionManager.cancelAutoPairing()
    }

    fun confirmAutoPairAdbCode(context: Context, pairingCode: String) {
        scope.launch {
            AdbConnectionManager.confirmAutoPairingCode(context, pairingCode)
        }
    }

    fun forgetAdbPairing() {
        AdbConnectionManager.forgetPairing()
    }

    fun refreshAll() {
        AdbConnectionManager.autoReconnect()
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
        PrivilegeBackend.ADB -> EmbeddedShellExecutor()
        PrivilegeBackend.ROOT -> RootShellExecutor()
        PrivilegeBackend.NONE -> null
    }

    /**
     * FIX BUG: "Sambungkan ADB tertanam atau Root dulu di layar Izin
     * Akses" muncul terus padahal server ADB SUDAH terhubung (atau baru
     * saja putus sesaat dan sebenarnya bisa pulih sendiri).
     *
     * Akar masalah: [getExecutor] (non-suspend) HANYA membaca [status]
     * StateFlow apa adanya saat dipanggil — kalau [PrivilegeStatus.activeBackend]
     * kebetulan masih [PrivilegeBackend.NONE] di momen itu (mis. tepat
     * setelah `onResume` ketika [AdbConnectionManager.autoReconnect]
     * fire-and-forget masih berjalan di background, atau state baru saja
     * turun ke [AdbConnectionState.PairedNotConnected] lewat
     * [AdbConnectionManager.markStreamFailureAndReconnect] sesaat sebelum
     * reconnect-nya sendiri selesai), caller (mis. TweakViewModel)
     * langsung menyerah dan menampilkan toast gagal SEBELUM sempat
     * mencoba tersambung sama sekali.
     *
     * Versi `suspend` ini dipakai sebagai GANTI [getExecutor] biasa oleh
     * semua pemanggil tweak: kalau preferensi backend adalah ADB tapi
     * belum [AdbConnectionState.Connected] persis saat ini, AKTIF
     * menunggu satu putaran reconnect sungguhan lewat
     * [AdbConnectionManager.awaitReconnect] sebelum benar-benar menyerah
     * — sama seperti pola retry yang sudah terbukti di
     * [EmbeddedShellExecutor], hanya dipindah lebih awal supaya toast
     * "belum tersambung" tidak muncul palsu saat server ADB sebenarnya
     * tersambung atau baru butuh sedikit waktu lagi. Preferensi ROOT
     * tidak butuh ini (root tidak kenal reconnect async seperti ini).
     */
    suspend fun getExecutorAwaitingConnection(): ShellExecutor? {
        val current = status.value
        return when (current.preferredBackend) {
            PrivilegeBackend.ADB -> {
                if (current.adbGranted) return EmbeddedShellExecutor()
                val reconnected = AdbConnectionManager.awaitReconnect()
                if (reconnected is AdbConnectionState.Connected) EmbeddedShellExecutor() else null
            }
            PrivilegeBackend.ROOT -> if (current.rootGranted) RootShellExecutor() else null
            PrivilegeBackend.NONE -> when {
                current.adbGranted -> EmbeddedShellExecutor()
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

    fun openWirelessDebuggingSettings(context: Context) {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(fallback) }
    }
}
