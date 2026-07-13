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

/**
 * Sumber tunggal kebenaran untuk status akses privilese (ADB tertanam & root).
 * Diinisialisasi sekali dari [com.aether.x.AetherXApp] dan dipakai di seluruh layar.
 *
 * REWORK TOTAL PERMISSION (lihat perintah rework — "buatkan sistem
 * seperti shizuku langsung tertanam dalam aplikasinya... hapus semua
 * yang bersangkutan dengan shizuku"): SELURUH ketergantungan pada
 * dev.rikka.shizuku (binder listener, requestPermission dialog, dst)
 * DIHAPUS. Backend non-root sekarang sepenuhnya ditangani
 * [AdbConnectionManager] (pairing wireless ADB Android 11+, koneksi shell
 * ADB murni-Kotlin) — PrivilegeManager di file ini menjadi lapisan tipis
 * yang MENERJEMAHKAN [AdbConnectionState] milik AdbConnectionManager
 * menjadi [PrivilegeStatus]/[RequestFeedback] yang sudah dipahami seluruh
 * UI yang ada (PermissionSetupScreen, PermissionMethodCard, dst).
 */
object PrivilegeManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    // SharedFlow event sekali-jalan untuk hasil aksi permintaan izin.
    // extraBufferCapacity=1 supaya event yang terjadi SEBELUM UI sempat
    // mulai collect tidak hilang begitu saja.
    private val _events = MutableSharedFlow<RequestFeedback>(extraBufferCapacity = 1)
    val events: SharedFlow<RequestFeedback> = _events.asSharedFlow()

    // Menyerialkan adoptExistingGrantIfNoPreference() — dipanggil dari
    // beberapa tempat independen yang bisa saling tabrakan tanpa mutex ini.
    private val adoptMutex = Mutex()

    private var initialized = false

    /** Panggil sekali saat aplikasi dibuat. Aman dipanggil berkali-kali.
     *
     * [context] dipakai untuk memuat preferensi backend yang pernah
     * dipilih pengguna (ADB/Root) dari DataStore, dan menginisialisasi
     * [AdbConnectionManager] (yang otomatis mencoba auto-reconnect diam-
     * diam kalau sudah pernah ada pairing tersimpan sebelumnya). */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        AdbConnectionManager.init(appContext)

        // Terjemahkan setiap perubahan AdbConnectionState -> PrivilegeStatus
        // secara reaktif, termasuk memancarkan RequestFeedback yang sesuai
        // (Granted/Failed) supaya UI tidak perlu tahu detail AdbConnectionManager
        // sama sekali — hanya PrivilegeStatus & events seperti sebelumnya.
        AdbConnectionManager.state.onEach { adbState ->
            val wasRequesting = _status.value.adbRequestState == RequestState.REQUESTING
            _status.update {
                it.copy(
                    adbState = adbState,
                    adbRequestState = when (adbState) {
                        is AdbConnectionState.Pairing, is AdbConnectionState.Connecting -> RequestState.REQUESTING
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
        }.launchIn(scope)

        checkRootSilently()

        val preferences = AetherXPreferences(appContext)
        scope.launch {
            val saved = preferences.getPreferredPrivilegeBackend()
            val backend = when (saved) {
                // Migrasi otomatis dari nama lama "SHIZUKU" (tersimpan
                // sebelum rework ini) ke "ADB" — pengguna lama yang sudah
                // pernah memilih Shizuku sebelumnya tidak perlu memilih
                // ulang metode privilesenya setelah update, cukup pairing
                // ADB sekali lagi (izin lama Shizuku memang tidak relevan
                // lagi karena backend-nya sudah berbeda total).
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
        AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED -> RequestFailureReason.ADB_PAIRING_CODE_INVALID_OR_EXPIRED
        AdbFailureReason.HOST_UNREACHABLE -> RequestFailureReason.ADB_HOST_UNREACHABLE
        AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR -> RequestFailureReason.ADB_SHELL_REJECTED_NEEDS_REPAIR
        AdbFailureReason.UNKNOWN -> RequestFailureReason.ADB_UNKNOWN
    }

    /**
     * Kalau pengguna belum pernah memilih backend secara eksplisit TAPI
     * salah satu (atau bahkan keduanya) sudah granted secara sistem —
     * adopsi otomatis salah satunya sebagai preferensi (diutamakan ADB,
     * konsisten dengan urutan fallback lama) supaya kartu yang lain langsung
     * terkunci di layar Izin Akses tanpa aksi tambahan dari pengguna.
     */
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

    /**
     * Menetapkan backend privilese yang SENGAJA dipilih pengguna di layar
     * Izin Akses, lalu menyimpannya secara permanen. Sejak dipanggil,
     * backend yang tidak dipilih tidak akan pernah dipakai untuk menjalankan
     * tweak (lihat [PrivilegeStatus.activeBackend]) — walaupun izinnya masih
     * granted secara sistem — supaya ADB dan Root tidak pernah aktif
     * berbarengan dan saling konflik.
     */
    fun selectBackend(context: Context, backend: PrivilegeBackend) {
        require(backend != PrivilegeBackend.NONE) { "Gunakan clearBackendPreference() untuk mereset pilihan." }
        _status.update { it.copy(preferredBackend = backend) }
        val preferences = AetherXPreferences(context.applicationContext)
        scope.launch {
            preferences.setPreferredPrivilegeBackend(if (backend == PrivilegeBackend.ADB) "ADB" else "ROOT")
        }
    }

    /**
     * Menghapus pilihan backend privilese ("Ganti metode" di layar Izin
     * Akses) supaya pengguna bisa beralih ADB <-> Root. Tidak mencabut
     * izin yang sudah granted secara sistem (mis. koneksi ADB tetap
     * tersambung) — murni mereset preferensi supaya kartu yang lain tidak
     * lagi terkunci.
     */
    fun clearBackendPreference(context: Context) {
        _status.update { it.copy(preferredBackend = PrivilegeBackend.NONE) }
        val preferences = AetherXPreferences(context.applicationContext)
        scope.launch { preferences.clearPreferredPrivilegeBackend() }
    }

    // ---------------------------------------------------------------------
    // ADB tertanam (menggantikan Shizuku)
    // ---------------------------------------------------------------------

    /**
     * Tahap 1 — "Mulai Penyandingan" (persis alur AxManager/Shizuku):
     * pengguna membuka Wireless debugging > "Pasangkan perangkat dengan
     * kode pairing", lalu memasukkan host, port PAIRING, dan kode 6-digit
     * yang tampil di dialog itu, PLUS port KONEKSI (dari layar utama
     * Wireless debugging, beda dari port pairing) yang akan dipakai untuk
     * sesi shell setelah pairing sukses.
     */
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
                connectHost = pairingHost,
                connectPort = connectPort,
            )
        }
    }

    /** Coba sambungkan ulang ke sesi ADB yang sudah pernah di-pair
     * sebelumnya (host:port tersimpan) — TANPA kode pairing lagi. */
    fun reconnectAdb(context: Context) {
        if (_status.value.adbRequestState == RequestState.REQUESTING) {
            _events.tryEmit(RequestFeedback.Failed(PrivilegeBackend.ADB, RequestFailureReason.ADB_ALREADY_IN_PROGRESS))
            return
        }
        selectBackend(context, PrivilegeBackend.ADB)
        AdbConnectionManager.autoReconnect()
    }

    /** "Lupakan perangkat ini" — reset total pairing ADB (keypair + host:port tersimpan). */
    fun forgetAdbPairing() {
        AdbConnectionManager.forgetPairing()
    }

    /**
     * Cek ulang SEMUA backend privilese (ADB + root) sekaligus, tanpa
     * memunculkan dialog/pairing apapun. Dipanggil tiap kali app kembali ke
     * foreground (lihat [com.aether.x.MainActivity]) supaya status akses
     * tidak pernah "basi".
     */
    fun refreshAll() {
        AdbConnectionManager.autoReconnect()
        checkRootSilently()
    }

    /**
     * Cek cepat & non-intrusif: apakah root sudah pernah disetujui sebelumnya.
     *
     * PENTING: sengaja memakai `Shell.getShell().isRoot` (bukan
     * `Shell.isAppGrantedRoot()`) karena yang terakhir hanya membaca status
     * shell yang SUDAH ada di proses ini — begitu proses app baru dimulai,
     * belum ada shell sama sekali sehingga selalu balik `false`/`null` walau
     * root sebenarnya masih diizinkan. `Shell.getShell()` benar-benar
     * mencoba membangun shell root; kalau sudah pernah disetujui
     * sebelumnya, Magisk/KernelSU/APatch akan meloloskannya tanpa dialog
     * apapun — jadi tetap "silent" di mata pengguna, tapi hasilnya akurat.
     */
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

    /**
     * Memicu prompt superuser (su) dari Magisk/KernelSU/APatch.
     *
     * [context] wajib diisi kalau pemanggilan ini merupakan PILIHAN SADAR
     * pengguna (mis. menekan tombol "Izinkan" di kartu Root pada layar Izin
     * Akses) — akan langsung menetapkan Root sebagai
     * [PrivilegeStatus.preferredBackend] via [selectBackend], sehingga
     * ADB otomatis dianggap tidak aktif oleh app meski masih tersambung
     * secara sistem. Kalau null (mis. dipanggil dari alur otomatis/internal),
     * preferensi tidak diubah.
     */
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

    /** Executor aktif sesuai backend yang sedang punya akses, atau null jika belum ada. */
    fun getExecutor(): ShellExecutor? = when (status.value.activeBackend) {
        PrivilegeBackend.ADB -> EmbeddedShellExecutor()
        PrivilegeBackend.ROOT -> RootShellExecutor()
        PrivilegeBackend.NONE -> null
    }

    // ---------------------------------------------------------------------
    // Izin pendukung: "Ubah Pengaturan Sistem" (WRITE_SETTINGS), overlay,
    // dan notifikasi. Bukan pengganti ADB/root, tapi membantu beberapa
    // tweak (mis. baca/tulis Settings.System langsung dari proses AetherX,
    // overlay crosshair/FPS, dan notifikasi foreground service) berjalan
    // lebih stabil di berbagai ROM. Semua dicek ulang tiap kali splash
    // screen tampil dan tiap kali app kembali ke foreground.
    // ---------------------------------------------------------------------

    /** Cek ulang ketiga izin pendukung sekaligus tanpa memunculkan dialog apapun. */
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

    /** Buka halaman sistem untuk memberi izin "Ubah Pengaturan Sistem" (WRITE_SETTINGS). */
    fun requestWriteSettings(context: Context) {
        if (Settings.System.canWrite(context)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Buka halaman sistem untuk memberi izin "Tampil di atas aplikasi lain" (overlay). */
    fun requestOverlayPermission(context: Context) {
        if (Settings.canDrawOverlays(context)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Buka halaman Pengaturan > Opsi Developer > Wireless debugging, supaya
     * pengguna bisa mengaktifkannya / membuka dialog pairing tanpa mencari manual. */
    fun openWirelessDebuggingSettings(context: Context) {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(fallback) }
    }
}
