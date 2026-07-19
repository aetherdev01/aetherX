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
    val dozeDisabled: Boolean = false,
    val message: String? = null,
    val detectedGames: List<DetectedGame> = emptyList(),
    val userId: String? = null,
    val isMembershipActive: Boolean = false,
)

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

    fun onGameModeChange(checked: Boolean) {
        _state.update { it.copy(gameModeEnabled = checked) }
        applyAndPersist { executor -> repository.applyGameMode(executor, checked) }
    }

    fun onCpuGovernorChange(governor: CpuGovernor) {
        _state.update { it.copy(cpuGovernor = governor) }
        applyAndPersist { executor -> repository.applyCpuGovernor(executor, governor) }
    }

    fun onRamPriorityModeChange(checked: Boolean) {
        _state.update { it.copy(ramPriorityMode = checked) }
        applyAndPersist { executor -> repository.applyRamPriority(executor, checked) }
    }

    fun onThermalThrottleOverrideChange(checked: Boolean) {
        _state.update { it.copy(thermalThrottleOverride = checked) }
        applyAndPersist { executor -> repository.applyThermalThrottleOverride(executor, checked) }
    }

    fun onGpuPerformanceModeChange(checked: Boolean) {
        _state.update { it.copy(gpuPerformanceMode = checked) }
        applyAndPersist { executor -> repository.applyGpuPerformanceMode(executor, checked) }
    }

    fun onIoSchedulerBoostChange(checked: Boolean) {
        _state.update { it.copy(ioSchedulerBoost = checked) }
        applyAndPersist { executor -> repository.applyIoSchedulerBoost(executor, checked) }
    }

    fun onKillBackgroundAppsChange(checked: Boolean, activity: Activity? = null) {
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

            if (activity != null) {
                val isMember = preferences.preferences.first().isMembershipActive
                AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
            }
        }
    }

    fun onVmHeapBoostChange(checked: Boolean) {
        _state.update { it.copy(vmHeapBoost = checked) }
        applyAndPersist { executor -> repository.applyVmHeapBoost(executor, checked) }
    }

    fun onDozeDisabledChange(checked: Boolean) {
        _state.update { it.copy(dozeDisabled = checked) }
        applyAndPersist { executor -> repository.applyDozeDisable(executor, checked) }
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
                repository.applyDozeDisable(executor, false)
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
                    dozeDisabled = false,
                    message = appString(R.string.tweak_reset_toast),
                )
            }
        }
    }

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
                dozeDisabled = s.dozeDisabled,
            )
        }
    }

    private fun appString(resId: Int): String {
        val text = getApplication<Application>().getString(resId)
        return text
    }
}
