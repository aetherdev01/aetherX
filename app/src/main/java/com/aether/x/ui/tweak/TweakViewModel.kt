package com.aether.x.ui.tweak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.apps.DetectedGame
import com.aether.x.core.apps.GameLauncher
import com.aether.x.core.display.DisplayInfo
import com.aether.x.core.display.DisplayInfoProvider
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.CpuGovernor
import com.aether.x.data.DeviceId
import com.aether.x.data.TweakRepository
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TweakUiState(
    val displayInfo: DisplayInfo = DisplayInfo(1080, 2400, 420, listOf(60f)),
    val pointerSpeed: Int = 0,
    val touchBoost: Boolean = false,
    val forceMaxRefreshRate: Boolean = false,
    val gameModeEnabled: Boolean = false,
    val cpuGovernor: CpuGovernor = CpuGovernor.UNIVERSAL,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val killBackgroundApps: Boolean = false,
    val vmHeapBoost: Boolean = false,
    val message: String? = null,
    val detectedGames: List<DetectedGame> = emptyList(),
    val userId: Int? = null,
)

/**
 * Semua tweak di layar ini sekarang aktif LANGSUNG saat diubah (slider dilepas /
 * switch ditoggle) — tidak ada lagi tombol "Terapkan" terpisah. Setiap perubahan
 * langsung dieksekusi lewat [ShellExecutor] yang aktif (Shizuku/Root) dan disimpan
 * ke preferences supaya tetap tersimpan walau aplikasi ditutup.
 */
class TweakViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TweakRepository()
    private val preferences = AetherXPreferences(application)
    private val userIdRepository = UserIdRepository(preferences, DeviceId.read(application))

    private val _state = MutableStateFlow(TweakUiState())
    val state: StateFlow<TweakUiState> = _state.asStateFlow()

    init {
        val displayInfo = DisplayInfoProvider.read(application)
        _state.update {
            it.copy(
                displayInfo = displayInfo,
                detectedGames = GameLauncher.detectInstalled(application),
            )
        }

        viewModelScope.launch {
            val saved = preferences.preferences.first()
            _state.update { current ->
                current.copy(
                    pointerSpeed = saved.pointerSpeed,
                    touchBoost = saved.touchBoostEnabled,
                    forceMaxRefreshRate = saved.forceMaxRefreshRate,
                    gameModeEnabled = saved.gameModeEnabled,
                    cpuGovernor = saved.cpuGovernor,
                    ramPriorityMode = saved.ramPriorityMode,
                    thermalThrottleOverride = saved.thermalThrottleOverride,
                    gpuPerformanceMode = saved.gpuPerformanceMode,
                    ioSchedulerBoost = saved.ioSchedulerBoost,
                    killBackgroundApps = saved.killBackgroundApps,
                    vmHeapBoost = saved.vmHeapBoost,
                )
            }
        }

        // ID lokal pengguna (mis. "ID-67128") ditampilkan di header tab Tweak,
        // menggantikan pill status Shizuku/Root sebelumnya. Angkanya sekarang
        // dialokasikan dari counter Firestore supaya benar-benar berurutan —
        // TIDAK ADA fallback acak; kalau alokasi gagal total (lihat
        // UserIdRepository), `id` adalah null dan pill ID pengguna di header
        // otomatis tidak ditampilkan (lihat TweakHeader) sampai berhasil di
        // percobaan berikutnya.
        //
        // Pendataan perangkat ke koleksi `devices` (deviceId, firstLoginAt,
        // lastLoginAt, userId) sudah dilakukan SEKALIGUS di dalam transaksi
        // atomik yang sama oleh UserIdRepository.resolveUserId() — tidak
        // perlu panggilan terpisah lagi di sini (sebelumnya ini dua langkah
        // terpisah yang berisiko: kalau langkah kedua gagal, counter global
        // sudah kadung naik tapi device tidak pernah tercatat).
        resolveAndRecordUserId()
    }

    private fun resolveAndRecordUserId() {
        // Kalau ID sudah ada di state (mis. percobaan sebelumnya berhasil),
        // tidak perlu ke jaringan lagi.
        if (_state.value.userId != null) return

        viewModelScope.launch {
            val id = userIdRepository.resolveUserId()
            _state.update { it.copy(userId = id) }
        }
    }

    /**
     * Dipanggil ulang setiap kali layar Tweak kembali aktif (ON_RESUME) DAN
     * saat pengguna mengetuk pill ID secara manual. Sebelumnya alokasi ID
     * hanya dicoba sekali saat ViewModel pertama dibuat — kalau percobaan
     * itu gagal total (mis. dibuka pertama kali saat jaringan belum siap),
     * badge "ID-…" akan tetap kosong selamanya sampai app di-restart penuh
     * (ViewModel baru). Dengan retry di resume/tap, badge akan otomatis
     * terisi begitu jaringan tersedia, tanpa perlu restart aplikasi.
     */
    fun retryResolveUserIdIfMissing() {
        resolveAndRecordUserId()
    }

    /** Dipanggil ulang saat layar kembali aktif, untuk menangkap kalau game baru dipasang/dihapus. */
    fun refreshDetectedGames() {
        val app = getApplication<Application>()
        _state.update { it.copy(detectedGames = GameLauncher.detectInstalled(app)) }
    }

    fun launchGame(packageName: String) {
        val app = getApplication<Application>()
        val launched = GameLauncher.launch(app, packageName)
        if (!launched) {
            _state.update { it.copy(message = appString(R.string.tweak_game_launch_failed)) }
        }
    }

    /** Slider hanya update tampilan sambil digeser; eksekusi shell dipicu saat dilepas
     *  lewat [onPointerSpeedChangeFinished] supaya tidak spam perintah shell tiap piksel. */
    fun onPointerSpeedChange(value: Float) {
        _state.update { it.copy(pointerSpeed = value.toInt()) }
    }

    fun onPointerSpeedChangeFinished() {
        val speed = _state.value.pointerSpeed
        applyAndPersist { executor -> repository.applyPointerSpeed(executor, speed) }
    }

    fun onTouchBoostChange(checked: Boolean) {
        _state.update { it.copy(touchBoost = checked) }
        applyAndPersist { executor -> repository.applyTouchBoost(executor, checked) }
    }

    fun onForceRefreshChange(checked: Boolean) {
        _state.update { it.copy(forceMaxRefreshRate = checked) }
        applyAndPersist { executor ->
            repository.applyRefreshRate(executor, checked, _state.value.displayInfo.maxRefreshRate)
        }
    }

    /** Mode Game: mengaktifkan Do Not Disturb sistem supaya notifikasi tidak
     *  mengganggu saat bermain. Dinonaktifkan lagi lewat switch yang sama. */
    fun onGameModeChange(checked: Boolean) {
        _state.update { it.copy(gameModeEnabled = checked) }
        applyAndPersist { executor -> repository.applyGameMode(executor, checked) }
    }

    /** Khusus root: terapkan governor CPU yang dipilih pengguna dari dropdown. */
    fun onCpuGovernorChange(governor: CpuGovernor) {
        _state.update { it.copy(cpuGovernor = governor) }
        applyAndPersist { executor -> repository.applyCpuGovernor(executor, governor) }
    }

    /** Khusus root: turunkan swappiness kernel supaya game tetap di RAM. */
    fun onRamPriorityModeChange(checked: Boolean) {
        _state.update { it.copy(ramPriorityMode = checked) }
        applyAndPersist { executor -> repository.applyRamPriority(executor, checked) }
    }

    /** Khusus root: naikkan batas suhu throttle supaya CPU/GPU tidak buru-buru diturunkan clock-nya. */
    fun onThermalThrottleOverrideChange(checked: Boolean) {
        _state.update { it.copy(thermalThrottleOverride = checked) }
        applyAndPersist { executor -> repository.applyThermalThrottleOverride(executor, checked) }
    }

    /** Khusus root: kunci frekuensi GPU ke governor performance selama bermain. */
    fun onGpuPerformanceModeChange(checked: Boolean) {
        _state.update { it.copy(gpuPerformanceMode = checked) }
        applyAndPersist { executor -> repository.applyGpuPerformanceMode(executor, checked) }
    }

    /** Khusus root: ganti I/O scheduler storage ke mode latensi rendah untuk baca aset game. */
    fun onIoSchedulerBoostChange(checked: Boolean) {
        _state.update { it.copy(ioSchedulerBoost = checked) }
        applyAndPersist { executor -> repository.applyIoSchedulerBoost(executor, checked) }
    }

    /**
     * Khusus root: aksi sekali jalan (bukan kondisi permanen) — hentikan
     * proses aplikasi pihak ketiga yang berjalan di background lalu switch
     * otomatis kembali OFF sesaat kemudian supaya jelas ini bukan status
     * yang "menyala terus", melainkan tombol pembersih RAM instan.
     */
    fun onKillBackgroundAppsChange(checked: Boolean) {
        if (!checked) return
        _state.update { it.copy(killBackgroundApps = true) }
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(message = appString(R.string.tweak_no_access_toast)) }
            } else {
                val result = repository.applyKillBackgroundApps(executor, true)
                _state.update {
                    it.copy(
                        message = if (result.success) {
                            appString(R.string.tweak_kill_background_toast)
                        } else {
                            appString(R.string.tweak_command_failed_toast)
                        },
                    )
                }
            }
            _state.update { it.copy(killBackgroundApps = false) }
        }
    }

    /** Khusus root: perbesar heap Dalvik/ART supaya GC lebih jarang jeda saat bermain. */
    fun onVmHeapBoostChange(checked: Boolean) {
        _state.update { it.copy(vmHeapBoost = checked) }
        applyAndPersist { executor -> repository.applyVmHeapBoost(executor, checked) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun resetTweaks() {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor != null) {
                repository.applyPointerSpeed(executor, 0)
                repository.applyTouchBoost(executor, false)
                repository.applyRefreshRate(executor, enabled = false, maxHz = 60f)
                repository.applyGameMode(executor, false)
                repository.applyCpuGovernor(executor, CpuGovernor.UNIVERSAL)
                repository.applyRamPriority(executor, false)
                repository.applyThermalThrottleOverride(executor, false)
                repository.applyGpuPerformanceMode(executor, false)
                repository.applyIoSchedulerBoost(executor, false)
                repository.applyVmHeapBoost(executor, false)
            }
            preferences.clearTweakState()
            _state.update {
                it.copy(
                    pointerSpeed = 0,
                    touchBoost = false,
                    forceMaxRefreshRate = false,
                    gameModeEnabled = false,
                    cpuGovernor = CpuGovernor.UNIVERSAL,
                    ramPriorityMode = false,
                    thermalThrottleOverride = false,
                    gpuPerformanceMode = false,
                    ioSchedulerBoost = false,
                    killBackgroundApps = false,
                    vmHeapBoost = false,
                    message = appString(R.string.tweak_reset_toast),
                )
            }
        }
    }

    /** Menjalankan satu perintah tweak lewat executor aktif lalu langsung menyimpan
     *  state tweak saat ini ke preferences. Kalau belum ada akses (Shizuku/Root),
     *  perubahan tetap tersimpan di UI/preferences tapi menampilkan toast peringatan.
     *  Kalau perintah shell-nya sendiri gagal (mis. `cmd notification set_dnd` ditolak
     *  perangkat), itu juga ditampilkan sebagai toast alih-alih diam-diam diabaikan —
     *  sebelumnya hasil [ShellResult] tidak pernah dicek sama sekali di sini. */
    private fun applyAndPersist(action: suspend (ShellExecutor) -> ShellResult) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(message = appString(R.string.tweak_no_access_toast)) }
            } else {
                val result = action(executor)
                if (!result.success) {
                    _state.update { it.copy(message = appString(R.string.tweak_command_failed_toast)) }
                }
            }
            val s = _state.value
            preferences.saveTweakState(
                pointerSpeed = s.pointerSpeed,
                touchBoostEnabled = s.touchBoost,
                forceMaxRefreshRate = s.forceMaxRefreshRate,
                gameModeEnabled = s.gameModeEnabled,
                cpuGovernor = s.cpuGovernor,
                ramPriorityMode = s.ramPriorityMode,
                thermalThrottleOverride = s.thermalThrottleOverride,
                gpuPerformanceMode = s.gpuPerformanceMode,
                ioSchedulerBoost = s.ioSchedulerBoost,
                killBackgroundApps = s.killBackgroundApps,
                vmHeapBoost = s.vmHeapBoost,
            )
        }
    }

    private fun appString(resId: Int): String = getApplication<Application>().getString(resId)
}
