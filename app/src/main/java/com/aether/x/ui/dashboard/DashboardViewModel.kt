package com.aether.x.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.device.DeviceInfoProvider
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.data.AetherXPreferences
import com.aether.x.core.apps.GameLaunchTracker
import com.aether.x.core.monitor.GameSessionRepository
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
    val recentPlayedGames: List<InstalledGameEntry> = emptyList(),
    val recentGamePackages: List<String> = emptyList(),
    val loadingGames: Boolean = true,
    val lastPlayedPackage: String? = null,
    val cardOrder: List<String> = DEFAULT_CARD_ORDER,
)

private val DEFAULT_CARD_ORDER = listOf(
    "live_monitor", "temperature", "battery_temperature", "battery", "charging", "ram",
    "root_access", "storage", "system_details", "android", "hardware", "recent_games", "game_monitor",
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    // True while the user has a local reorder that has not yet been persisted.
    // DataStore can emit the previous value during that short window; applying
    // it would make the Dashboard visually jump back to the old order.
    @Volatile
    private var cardOrderDirty = false

    init {
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(application)) }
        GameSessionRepository.initialize(application.applicationContext)
        loadGames()
        observeLastPlayed()
        observeRecentSessions()
    }

    private fun loadGames() {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) {
                GameProfileCatalog.loadInstalledGames(getApplication())
            }
            _state.update { it.copy(installedGames = reorderByLastPlayed(games, it.lastPlayedPackage), loadingGames = false) }
            refreshRecentGames()
        }
    }

    private fun observeLastPlayed() {
        preferences.preferences.onEach { prefs ->
            _state.update {
                it.copy(
                    lastPlayedPackage = prefs.lastPlayedGamePackage,
                    recentGamePackages = prefs.recentGamePackages,
                    installedGames = reorderByLastPlayed(it.installedGames, prefs.lastPlayedGamePackage),
                    cardOrder = if (cardOrderDirty) {
                        this@DashboardViewModel._state.value.cardOrder
                    } else {
                        normalizeCardOrder(prefs.dashboardCardOrder)
                    },
                )
            }
            refreshRecentGames()
        }.launchIn(viewModelScope)
    }

    private fun observeRecentSessions() {
        GameSessionRepository.sessions.onEach {
            refreshRecentGames()
        }.launchIn(viewModelScope)
    }

    private fun refreshRecentGames() {
        val current = _state.value
        val sessionPackages = GameSessionRepository.sessions.value
            .sortedByDescending { it.startedAtMs }
            .map { it.packageName }
        val packageOrder = (
            current.recentGamePackages + sessionPackages + listOfNotNull(current.lastPlayedPackage)
        ).filter { it.isNotBlank() }.distinct().take(8)
        val installedByPackage = current.installedGames.associateBy { it.packageName }
        val recent = packageOrder.mapNotNull { installedByPackage[it] }.take(6)
        _state.update { it.copy(recentPlayedGames = recent) }
    }

    private fun normalizeCardOrder(saved: List<String>): List<String> {
        val defaults = DEFAULT_CARD_ORDER
        val result = saved.filter { it in defaults }.distinct().toMutableList()
        // Kartu baru disisipkan di posisi default-nya, bukan
        // ditaruh paling bawah, untuk user yang sudah punya urutan tersimpan.
        defaults.forEachIndexed { defaultIndex, id ->
            if (id !in result) result.add(defaultIndex.coerceAtMost(result.size), id)
        }
        return result
    }

    /**
     * Menukar posisi dua card secara real-time saat digeser (long-press + drag)
     * di [com.aether.x.ui.tweak.TweakScreen]. Hanya update state lokal — belum
     * ditulis ke DataStore supaya tidak spam I/O selama animasi drag berjalan.
     */
    fun swapCards(fromIndex: Int, toIndex: Int) {
        val current = _state.value.cardOrder.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val movedItem = current.removeAt(fromIndex)
        current.add(toIndex, movedItem)
        cardOrderDirty = true
        _state.update { it.copy(cardOrder = current) }
    }

    /**
     * Menyimpan urutan ke DataStore ketika pengguna melepaskan jari (drag end).
     */
    fun saveCardOrder() {
        val orderToPersist = _state.value.cardOrder
        viewModelScope.launch {
            runCatching {
                preferences.setDashboardCardOrder(orderToPersist)
            }.onSuccess {
                // Only clear the dirty flag after the write has completed.
                // This prevents an in-flight stale DataStore emission from
                // restoring the previous layout while the write is running.
                cardOrderDirty = false
            }
        }
    }

    private fun reorderByLastPlayed(games: List<InstalledGameEntry>, lastPlayed: String?): List<InstalledGameEntry> {
        if (lastPlayed == null) return games
        val (last, rest) = games.partition { it.packageName == lastPlayed }
        return last + rest
    }

    fun onGameClick(packageName: String) {
        viewModelScope.launch {
            GameLaunchTracker.launchAndTrack(getApplication(), packageName)
        }
    }

    fun refreshDeviceInfo() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { DeviceInfoProvider.read(app) }
            _state.update { it.copy(deviceInfo = info) }
        }
    }
}
