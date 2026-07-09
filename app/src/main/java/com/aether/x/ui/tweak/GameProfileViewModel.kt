package com.aether.x.ui.tweak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Mengelola daftar game (dari [GameProfileCatalog], khusus yang terpasang)
 * dan CRUD [GameProfile] per game untuk layar Game Profile (sidebar di tab
 * Tweak). TIDAK bertanggung jawab menerapkan/mereset tweak secara real-time
 * ke sistem — itu sepenuhnya tugas [com.aether.x.core.monitor.GameProfileMonitorService]
 * yang berjalan independen di background begitu profil disimpan di sini.
 */
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
        // Terus ikuti perubahan (mis. GameProfileMonitorService menandai
        // game lain sedang aktif) supaya badge "Sedang Diterapkan" di
        // sidebar selalu akurat walau layar ini sedang terbuka.
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

    private fun updateSelectedProfile(transform: (GameProfile) -> GameProfile) {
        val pkg = _state.value.selectedPackage ?: return
        val current = _state.value.profiles[pkg] ?: GameProfile.default(pkg)
        val updated = transform(current)
        _state.update { it.copy(profiles = it.profiles + (pkg to updated)) }
        viewModelScope.launch { preferences.saveGameProfile(updated) }
    }

    /**
     * Terapkan preset Game Mode (FITUR BARU — lihat perintah rework:
     * "tambahkan fitur baru Game Mode : Low, Mid, Boost"). Mengganti ke-6
     * toggle sekaligus sesuai kombinasi [mode] (lihat KDoc
     * [GameProfile.withGameMode]) — pengguna tetap bisa mengubah toggle
     * manual setelahnya, memilih mode lain lagi akan menimpa ulang semua
     * toggle sesuai kombinasi mode yang baru dipilih.
     */
    fun onGameModeChange(mode: GameMode) =
        updateSelectedProfile { GameProfile.withGameMode(it, mode) }

    fun onCpuPerformanceModeChange(checked: Boolean) =
        updateSelectedProfile { it.copy(cpuPerformanceMode = checked) }

    fun onRamPriorityModeChange(checked: Boolean) =
        updateSelectedProfile { it.copy(ramPriorityMode = checked) }

    fun onThermalThrottleOverrideChange(checked: Boolean) =
        updateSelectedProfile { it.copy(thermalThrottleOverride = checked) }

    fun onGpuPerformanceModeChange(checked: Boolean) =
        updateSelectedProfile { it.copy(gpuPerformanceMode = checked) }

    fun onIoSchedulerBoostChange(checked: Boolean) =
        updateSelectedProfile { it.copy(ioSchedulerBoost = checked) }

    fun onVmHeapBoostChange(checked: Boolean) =
        updateSelectedProfile { it.copy(vmHeapBoost = checked) }

    /** FITUR BARU — tweak ke-7: GPU Rendering Priority (SurfaceFlinger). */
    fun onGpuRenderingPriorityChange(checked: Boolean) =
        updateSelectedProfile { it.copy(gpuRenderingPriority = checked) }

    /**
     * Menghapus seluruh tweak profil game yang sedang dipilih. Kalau game
     * ini kebetulan sedang jadi profil AKTIF (dipantau
     * GameProfileMonitorService), TIDAK langsung mereset sistem dari sini —
     * cukup hapus data tersimpannya; poll berikutnya dari service akan
     * mendeteksi profilnya sudah kosong lewat siklus normal saat game
     * ditutup. Kalau ingin efek instan saat game masih terbuka, pengguna
     * cukup mematikan toggle satu-satu (yang langsung tersimpan & akan
     * disinkronkan poll berikutnya kalau game itu game yang sedang aktif).
     */
    fun resetSelectedProfile() {
        val pkg = _state.value.selectedPackage ?: return
        _state.update { it.copy(profiles = it.profiles - pkg) }
        viewModelScope.launch {
            preferences.deleteGameProfile(pkg)
            _state.update { it.copy(message = appString(R.string.game_profile_reset_toast)) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /** FITUR BARU (lihat perintah rework — "tambahkan Toast di semua Fitur"): lihat KDoc appString di TweakViewModel. */
    private fun appString(resId: Int): String {
        val text = getApplication<Application>().getString(resId)
        getApplication<Application>().showAetherToast(text)
        return text
    }
}
