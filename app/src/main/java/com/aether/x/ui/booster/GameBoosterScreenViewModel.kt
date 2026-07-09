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
    // Sesi "draft" yang ditampilkan di sidebar SEBELUM game benar-benar
    // dibuka — di implementasi ini pendingSession tidak pernah diisi
    // (tetap null selalu) karena onGameSelected langsung memulai sesi
    // sungguhan lewat GameBoosterOverlayService; field ini disediakan agar
    // GameBoosterScreen punya tempat menampilkan draft kalau perilaku ini
    // diperluas nanti tanpa perlu ubah signature UI lagi.
    val pendingSession: GameBoosterSession? = null,
)

/**
 * ViewModel [GameBoosterScreen] — memuat daftar game (reuse
 * [GameProfileCatalog], sama seperti Dashboard "Aktivitas Game"), dan
 * menyambungkan aksi menu sidebar ke [GameBoosterActionHandler] /
 * [GameBoosterOverlayService].
 */
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

    /**
     * Dipilih dari list kiri — MENAMPILKAN SPLASH dulu (lihat perintah
     * rework: "saat buka gamenya ada animasi splash dari game
     * boosternya") lewat [GameBoosterSplashActivity], yang lalu OTOMATIS
     * membuka game & memulai [GameBoosterOverlayService] setelah animasi
     * selesai — BUKAN lagi langsung [GameLaunchTracker]+[GameBoosterOverlayService]
     * terpisah tanpa splash.
     */
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

    fun onScreenshot() {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.takeScreenshot(executor)
        }
    }

    fun onEndSession() {
        GameBoosterOverlayService.stop(getApplication())
    }
}
