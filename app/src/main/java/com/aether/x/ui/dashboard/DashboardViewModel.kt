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
    // FITUR BARU — section "Aktivitas Game": daftar game terpasang (dari
    // katalog 500+ game yang sama dipakai GameProfileScreen — lihat
    // GameProfileCatalog), diurutkan dengan game TERAKHIR DIPAKAI di
    // posisi pertama (kalau ada & masih terpasang), sisanya alfabet.
    val installedGames: List<InstalledGameEntry> = emptyList(),
    val loadingGames: Boolean = true,
    val lastPlayedPackage: String? = null,
)

/**
 * ViewModel tab Dashboard.
 *
 * REWORK TOTAL (lihat perintah rework — "rework total tampilan Dashboard
 * hapus section CPU, GPU, SUHU"): monitor CPU/GPU/Suhu (polling shell/sysfs
 * tiap 2.5 detik lewat [com.aether.x.core.kernel.KernelInfoReader] /
 * [com.aether.x.core.monitor.SystemStatsProvider]) DIHAPUS TOTAL dari
 * Dashboard — monitoring performa real-time sekarang jadi domain KHUSUS
 * [com.aether.x.ui.booster.GameBoosterScreen] (Game Booster), yang memang
 * dipakai SELAMA sesi bermain, bukan di layar ringkasan Dashboard yang
 * dilihat sebentar-sebentar. Dashboard sekarang murni: identitas app (hero
 * card ramping) + Info Device (statis, tidak perlu polling) + daftar game
 * terpasang ("Aktivitas Game").
 */
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

    /**
     * Mengamati [AetherXPreferences.lastPlayedGamePackage] secara terus-menerus
     * (bukan sekali baca) supaya begitu pengguna membuka sebuah game lewat
     * [onGameClick] lalu kembali ke AetherX, urutan "Terakhir dipakai" di
     * daftar langsung ter-refresh tanpa perlu keluar-masuk tab Dashboard.
     */
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

    /**
     * Buka [packageName] — MENAMPILKAN SPLASH Game Booster dulu (lihat
     * perintah rework: "saat buka gamenya ada animasi splash dari game
     * boosternya"), yang lalu otomatis membuka game & memulai
     * [com.aether.x.core.overlay.GameBoosterOverlayService]. Konsisten
     * dengan [com.aether.x.ui.booster.GameBoosterScreenViewModel.onGameSelected] —
     * SATU alur "buka game" dipakai baik dari Dashboard maupun Game
     * Booster, bukan dua jalur berbeda.
     */
    fun onGameClick(packageName: String) {
        val label = state.value.installedGames.firstOrNull { it.packageName == packageName }?.label ?: packageName
        GameBoosterSplashActivity.launch(getApplication(), packageName, label)
    }

    /** Baca ulang info device (mis. setelah storage berubah signifikan). Dipanggil manual lewat tombol refresh. */
    fun refreshDeviceInfo() {
        val app = getApplication<Application>()
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(app)) }
    }
}
