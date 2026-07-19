package com.aether.x.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.device.DeviceInfoProvider
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.booster.GameBoosterSplashActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val deviceInfo: DeviceInfoSnapshot? = null,

    val installedGames: List<InstalledGameEntry> = emptyList(),
    val loadingGames: Boolean = true,
    val lastPlayedPackage: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(application)) }
        loadGames()
        observeLastPlayed()
    }

    private fun loadGames() {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) {
                GameProfileCatalog.loadInstalledGames(getApplication())
            }
            _state.update { it.copy(installedGames = reorderByLastPlayed(games, it.lastPlayedPackage), loadingGames = false) }
        }
    }

    private fun observeLastPlayed() {
        preferences.preferences.onEach { prefs ->
            _state.update {
                it.copy(
                    lastPlayedPackage = prefs.lastPlayedGamePackage,
                    installedGames = reorderByLastPlayed(it.installedGames, prefs.lastPlayedGamePackage),
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun reorderByLastPlayed(games: List<InstalledGameEntry>, lastPlayed: String?): List<InstalledGameEntry> {
        if (lastPlayed == null) return games
        val (last, rest) = games.partition { it.packageName == lastPlayed }
        return last + rest
    }

    fun onGameClick(packageName: String) {
        val label = state.value.installedGames.firstOrNull { it.packageName == packageName }?.label ?: packageName
        GameBoosterSplashActivity.launch(getApplication(), packageName, label)
    }

    fun refreshDeviceInfo() {
        val app = getApplication<Application>()
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(app)) }
    }
}
