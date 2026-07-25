package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameMode
import com.aether.x.ui.components.showAetherToast
import com.aether.x.data.GameProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameProfileUiState(
    val loadingGames: Boolean = true,
    val installedGames: List<InstalledGameEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedPackage: String? = null,
    val profiles: Map<String, GameProfile> = emptyMap(),
    val activeGameProfilePackage: String? = null,
    val message: String? = null,
) {
    val filteredGames: List<InstalledGameEntry>
        get() = if (searchQuery.isBlank()) {
            installedGames
        } else {
            installedGames.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }

    val selectedProfile: GameProfile?
        get() = selectedPackage?.let { pkg -> profiles[pkg] ?: GameProfile.default(pkg) }
}

class GameProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(GameProfileUiState())
    val state: StateFlow<GameProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val games = GameProfileCatalog.loadInstalledGames(application)
            _state.update { it.copy(installedGames = games, loadingGames = false) }
        }
        viewModelScope.launch {
            val saved = preferences.preferences.first()
            _state.update {
                it.copy(
                    profiles = saved.gameProfiles,
                    activeGameProfilePackage = saved.activeGameProfilePackage,
                )
            }
        }

        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        profiles = prefs.gameProfiles,
                        activeGameProfilePackage = prefs.activeGameProfilePackage,
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun selectGame(packageName: String) {
        _state.update { it.copy(selectedPackage = packageName) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPackage = null) }
    }

    private fun updateSelectedProfile(activity: Activity? = null, transform: (GameProfile) -> GameProfile) {
        val pkg = _state.value.selectedPackage ?: return
        val current = _state.value.profiles[pkg] ?: GameProfile.default(pkg)
        val updated = transform(current)
        _state.update { it.copy(profiles = it.profiles + (pkg to updated)) }
        viewModelScope.launch {
            preferences.saveGameProfile(updated)
            maybeShowAd(activity)
        }
    }

    private suspend fun maybeShowAd(activity: Activity?) {
        if (activity == null) return
        val isMember = preferences.preferences.first().isMembershipActive
        AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
    }

    fun onGameModeChange(mode: GameMode, activity: Activity? = null) =
        updateSelectedProfile(activity) { GameProfile.withGameMode(it, mode) }

    fun onCpuPerformanceModeChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(cpuPerformanceMode = checked) }

    fun onRamPriorityModeChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(ramPriorityMode = checked) }

    fun onThermalThrottleOverrideChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(thermalThrottleOverride = checked) }

    fun onGpuPerformanceModeChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(gpuPerformanceMode = checked) }

    fun onIoSchedulerBoostChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(ioSchedulerBoost = checked) }

    fun onVmHeapBoostChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(vmHeapBoost = checked) }

    fun onGpuRenderingPriorityChange(checked: Boolean, activity: Activity? = null) =
        updateSelectedProfile(activity) { it.copy(gpuRenderingPriority = checked) }

    fun resetSelectedProfile(activity: Activity? = null) {
        val pkg = _state.value.selectedPackage ?: return
        _state.update { it.copy(profiles = it.profiles - pkg) }
        viewModelScope.launch {
            preferences.deleteGameProfile(pkg)
            _state.update { it.copy(message = appString(R.string.game_profile_reset_toast)) }
            maybeShowAd(activity)
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun appString(resId: Int): String {
        val text = getApplication<Application>().getString(resId)
        getApplication<Application>().showAetherToast(text)
        return text
    }
}
