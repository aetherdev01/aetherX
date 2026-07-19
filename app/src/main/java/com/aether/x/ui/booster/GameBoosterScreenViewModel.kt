package com.aether.x.ui.booster

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.apps.GameLaunchTracker
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.booster.GameBoosterActionHandler
import com.aether.x.core.booster.GameBoosterSession
import com.aether.x.core.booster.GameBoosterSessionHolder
import com.aether.x.core.overlay.GameBoosterOverlayService
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.GameMode
import com.aether.x.R
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameBoosterScreenState(
    val loading: Boolean = true,
    val games: List<InstalledGameEntry> = emptyList(),
    val selectedPackage: String? = null,

    val pendingSession: GameBoosterSession? = null,
)

class GameBoosterScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)
    private val actionHandler = GameBoosterActionHandler()

    private val _state = MutableStateFlow(GameBoosterScreenState())
    val state: StateFlow<GameBoosterScreenState> = _state.asStateFlow()

    val activeSession: StateFlow<GameBoosterSession?> = GameBoosterSessionHolder.session

    init {
        loadGames()
    }

    private fun loadGames() {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) {
                GameProfileCatalog.loadInstalledGames(getApplication())
            }
            _state.update { it.copy(games = games, loading = false) }
        }
    }

    fun onGameSelected(entry: InstalledGameEntry) {
        _state.update { it.copy(selectedPackage = entry.packageName) }
        GameBoosterSplashActivity.launch(getApplication(), entry.packageName, entry.label)
    }

    fun onModeChange(mode: GameMode) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(mode = mode) }
            preferences.setGameBoosterMode(mode)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyMode(executor, mode)
        }
    }

    fun onDndToggle(enabled: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(dndEnabled = enabled) }
            preferences.setGameBoosterDndEnabled(enabled)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyDnd(executor, enabled)
        }
    }

    fun onFpsOverlayToggle(enabled: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(fpsOverlayEnabled = enabled) }
            preferences.setGameBoosterFpsOverlayEnabled(enabled)
        }
    }

    fun onRotationLockToggle(locked: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(rotationLocked = locked) }
            preferences.setGameBoosterRotationLocked(locked)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyRotationLock(executor, locked)
        }
    }

    fun onTouchBoostToggle(enabled: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(touchBoostEnabled = enabled) }
            preferences.setGameBoosterTouchBoostEnabled(enabled)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyTouchBoost(executor, enabled)
        }
    }

    fun onWhatsAppLaunch() {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE_NAME)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    fun onForceMaxRefreshRate() {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val displayInfo = withContext(Dispatchers.IO) {
                com.aether.x.core.display.DisplayInfoProvider.read(getApplication())
            }
            actionHandler.applyMaxRefreshRate(executor, displayInfo.maxRefreshRate)
        }
    }

    fun onScreenshot() {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            val context = getApplication<Application>()
            if (executor == null) {
                context.showAetherToast(context.getString(R.string.game_booster_screenshot_needs_privilege))
                return@launch
            }
            val path = actionHandler.takeScreenshot(executor)
            if (path != null) {
                context.showAetherToast(context.getString(R.string.game_booster_screenshot_success))
            } else {
                context.showAetherToast(context.getString(R.string.game_booster_screenshot_failed))
            }
        }
    }

    fun onEndSession() {
        GameBoosterOverlayService.stop(getApplication())
    }

    private companion object {
        const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
    }
}
