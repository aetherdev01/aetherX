package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
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
import com.aether.x.data.TweakHistoryRepository
import com.aether.x.data.UserIdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aether.x.core.locale.str

data class TweakUiState(
    val displayInfo: DisplayInfo = DisplayInfo(1080, 2400, 420, listOf(60f)),
    val pointerSpeed: Int = 0,
    val touchBoost: Boolean = false,
    val forceMaxRefreshRate: Boolean = false,
    val rotationLockEnabled: Boolean = false,
    val gameModeEnabled: Boolean = false,
    val cpuGovernor: CpuGovernor = CpuGovernor.UNIVERSAL,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val killBackgroundApps: Boolean = false,
    val vmHeapBoost: Boolean = false,
    val dozeDisabled: Boolean = false,
    val message: String? = null,
    val detectedGames: List<DetectedGame> = emptyList(),
    val userId: String? = null,
    val isMembershipActive: Boolean = false,
)

class TweakViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TweakRepository()
    private val preferences = AetherXPreferences(application)
    private val history = TweakHistoryRepository(application)
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
                    rotationLockEnabled = saved.rotationLockEnabled,
                    gameModeEnabled = saved.gameModeEnabled,
                    cpuGovernor = saved.cpuGovernor,
                    ramPriorityMode = saved.ramPriorityMode,
                    thermalThrottleOverride = saved.thermalThrottleOverride,
                    gpuPerformanceMode = saved.gpuPerformanceMode,
                    ioSchedulerBoost = saved.ioSchedulerBoost,
                    killBackgroundApps = saved.killBackgroundApps,
                    vmHeapBoost = saved.vmHeapBoost,
                    dozeDisabled = saved.dozeDisabled,
                )
            }
        }

        resolveAndRecordUserId()

        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                _state.update { it.copy(isMembershipActive = prefs.isMembershipActive) }
            }
        }
    }

    private fun resolveAndRecordUserId() {

        if (_state.value.userId != null) return

        viewModelScope.launch {
            val id = userIdRepository.resolveUserId()
            _state.update { it.copy(userId = id) }
        }
    }

    fun retryResolveUserIdIfMissing() {
        resolveAndRecordUserId()
    }

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

    fun onPointerSpeedChange(value: Float) {
        _state.update { it.copy(pointerSpeed = value.toInt()) }
    }

    fun onPointerSpeedChangeFinished(activity: Activity? = null) {
        val speed = _state.value.pointerSpeed
        applyAndPersist(activity, str(R.string.tweak_kecepatan_pointer, speed)) { executor -> repository.applyPointerSpeed(executor, speed) }
    }

    fun onTouchBoostChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(touchBoost = checked) }
        applyAndPersist(activity, str(R.string.tweak_touch_boost_2, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyTouchBoost(executor, checked) }
    }

    fun onForceRefreshChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(forceMaxRefreshRate = checked) }
        applyAndPersist(activity, str(R.string.tweak_refresh_rate_maksimal, str(if (checked) R.string.state_on else R.string.state_off))) { executor ->
            repository.applyRefreshRate(executor, checked, _state.value.displayInfo.maxRefreshRate)
        }
    }

    fun onRotationLockChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(rotationLockEnabled = checked) }
        applyAndPersist(activity, str(R.string.tweak_kunci_orientasi, str(if (checked) R.string.state_on else R.string.state_off))) { executor ->
            repository.applyRotationLock(executor, checked)
        }
    }

    fun onGameModeChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(gameModeEnabled = checked) }
        applyAndPersist(activity, str(R.string.tweak_game_mode_2, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyGameMode(executor, checked) }
    }

    fun onCpuGovernorChange(governor: CpuGovernor, activity: Activity? = null) {
        _state.update { it.copy(cpuGovernor = governor) }
        applyAndPersist(activity, str(R.string.tweak_cpu_governor_2, governor.name)) { executor -> repository.applyCpuGovernor(executor, governor) }
    }

    fun onRamPriorityModeChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(ramPriorityMode = checked) }
        applyAndPersist(activity, str(R.string.tweak_ram_priority_2, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyRamPriority(executor, checked) }
    }

    fun onThermalThrottleOverrideChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(thermalThrottleOverride = checked) }
        applyAndPersist(activity, str(R.string.tweak_thermal_override, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyThermalThrottleOverride(executor, checked) }
    }

    fun onGpuPerformanceModeChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(gpuPerformanceMode = checked) }
        applyAndPersist(activity, str(R.string.tweak_gpu_performance_2, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyGpuPerformanceMode(executor, checked) }
    }

    fun onIoSchedulerBoostChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(ioSchedulerBoost = checked) }
        applyAndPersist(activity, str(R.string.tweak_i_o_scheduler_boost, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyIoSchedulerBoost(executor, checked) }
    }

    fun onKillBackgroundAppsChange(checked: Boolean, activity: Activity? = null) {
        if (!checked) return
        _state.update { it.copy(killBackgroundApps = true) }
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor == null) {
                _state.update { it.copy(message = appString(R.string.tweak_no_access_toast)) }
            } else {
                val result = repository.applyKillBackgroundApps(executor, true)
                history.record(str(R.string.tweak_tweak_sistem), str(R.string.tweak_bersihkan_aplikasi_latar), result.success)
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

            maybeShowAd(activity)
        }
    }

    fun onVmHeapBoostChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(vmHeapBoost = checked) }
        applyAndPersist(activity, str(R.string.tweak_vm_heap_boost_2, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyVmHeapBoost(executor, checked) }
    }

    fun onDozeDisabledChange(checked: Boolean, activity: Activity? = null) {
        _state.update { it.copy(dozeDisabled = checked) }
        applyAndPersist(activity, str(R.string.tweak_doze_disable, str(if (checked) R.string.state_on else R.string.state_off))) { executor -> repository.applyDozeDisable(executor, checked) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun resetTweaks(activity: Activity? = null) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            var resetSucceeded = executor != null
            if (executor != null) {
                val resetResults = mutableListOf<ShellResult>()
                resetResults += repository.applyPointerSpeed(executor, 0)
                resetResults += repository.applyTouchBoost(executor, false)
                resetResults += repository.applyRefreshRate(executor, enabled = false, maxHz = 60f)
                resetResults += repository.applyRotationLock(executor, locked = false)
                resetResults += repository.applyGameMode(executor, false)
                resetResults += repository.applyCpuGovernor(executor, CpuGovernor.UNIVERSAL)
                resetResults += repository.applyRamPriority(executor, false)
                resetResults += repository.applyThermalThrottleOverride(executor, false)
                resetResults += repository.applyGpuPerformanceMode(executor, false)
                resetResults += repository.applyIoSchedulerBoost(executor, false)
                resetResults += repository.applyVmHeapBoost(executor, false)
                resetResults += repository.applyDozeDisable(executor, false)
                resetSucceeded = resetResults.all { it.success }
            }
            history.record(str(R.string.tweak_reset_tweak), str(R.string.tweak_mengembalikan_konfigurasi_utama), resetSucceeded)
            preferences.clearTweakState()
            _state.update {
                it.copy(
                    pointerSpeed = 0,
                    touchBoost = false,
                    forceMaxRefreshRate = false,
                    rotationLockEnabled = false,
                    gameModeEnabled = false,
                    cpuGovernor = CpuGovernor.UNIVERSAL,
                    ramPriorityMode = false,
                    thermalThrottleOverride = false,
                    gpuPerformanceMode = false,
                    ioSchedulerBoost = false,
                    killBackgroundApps = false,
                    vmHeapBoost = false,
                    dozeDisabled = false,
                    message = appString(R.string.tweak_reset_toast),
                )
            }
            maybeShowAd(activity)
        }
    }

    private fun applyAndPersist(
        activity: Activity? = null,
        actionLabel: String = str(R.string.tweak_konfigurasi_tweak),
        action: suspend (ShellExecutor) -> ShellResult,
    ) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            var succeeded = false
            if (executor == null) {
                _state.update { it.copy(message = appString(R.string.tweak_no_access_toast)) }
            } else {
                val result = action(executor)
                succeeded = result.success
                if (!result.success) {
                    _state.update { it.copy(message = appString(R.string.tweak_command_failed_toast)) }
                }
            }
            history.record(str(R.string.tweak_tweak_sistem), actionLabel, succeeded)
            val s = _state.value
            preferences.saveTweakState(
                pointerSpeed = s.pointerSpeed,
                touchBoostEnabled = s.touchBoost,
                forceMaxRefreshRate = s.forceMaxRefreshRate,
                rotationLockEnabled = s.rotationLockEnabled,
                gameModeEnabled = s.gameModeEnabled,
                cpuGovernor = s.cpuGovernor,
                ramPriorityMode = s.ramPriorityMode,
                thermalThrottleOverride = s.thermalThrottleOverride,
                gpuPerformanceMode = s.gpuPerformanceMode,
                ioSchedulerBoost = s.ioSchedulerBoost,
                killBackgroundApps = s.killBackgroundApps,
                vmHeapBoost = s.vmHeapBoost,
                dozeDisabled = s.dozeDisabled,
            )
            if (succeeded) {
                maybeShowAd(activity)
            }
        }
    }

    private suspend fun maybeShowAd(activity: Activity?) {
        if (activity == null) return
        val isMember = preferences.preferences.first().isMembershipActive
        AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
    }

    private fun appString(resId: Int): String {
        val text = getApplication<Application>().getString(resId)
        return text
    }
}
