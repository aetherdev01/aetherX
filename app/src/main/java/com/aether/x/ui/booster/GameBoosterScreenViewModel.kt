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

    /**
     * FITUR BARU (lihat perintah rework foto referensi 1, menu "Kunci
     * Rotasi") — pola identik [onDndToggle]: update session (UI langsung
     * reflect), simpan preference (persist antar sesi), terapkan efek
     * shell nyata lewat [actionHandler].
     */
    fun onRotationLockToggle(locked: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(rotationLocked = locked) }
            preferences.setGameBoosterRotationLocked(locked)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyRotationLock(executor, locked)
        }
    }

    /** FITUR BARU (lihat perintah rework foto referensi 1, menu "Akselerasi Sentuhan") — pola identik [onRotationLockToggle]. */
    fun onTouchBoostToggle(enabled: Boolean) {
        viewModelScope.launch {
            GameBoosterSessionHolder.update { it.copy(touchBoostEnabled = enabled) }
            preferences.setGameBoosterTouchBoostEnabled(enabled)
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.applyTouchBoost(executor, enabled)
        }
    }

    /**
     * FITUR BARU (lihat perintah rework foto referensi 1, menu
     * "WhatsApp"): membuka WhatsApp lewat launch intent standar
     * [android.content.pm.PackageManager.getLaunchIntentForPackage] —
     * SAMA seperti menekan ikon WhatsApp di home screen, BUKAN deep-link
     * ke percakapan tertentu (tidak ada konteks kontak/chat spesifik di
     * sini). Tidak melakukan apa pun kalau WhatsApp tidak terpasang
     * (intent null) — sengaja tidak menampilkan pesan error, karena
     * tombol ini murni kenyamanan opsional, bukan fitur inti.
     */
    fun onWhatsAppLaunch() {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE_NAME)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    /**
     * FITUR BARU (lihat perintah rework foto referensi 1, menu "Refresh
     * Rate"): fire-and-forget quick-action, BUKAN toggle status seperti
     * onRotationLockToggle/onTouchBoostToggle di atas — foto referensi 1
     * tidak menunjukkan indikator on/off berbeda untuk menu ini (beda
     * dari Kunci Rotasi yang jelas dua ikon berbeda), jadi tap = langsung
     * terapkan refresh rate MAKSIMUM yang didukung layar perangkat sekali
     * jalan. Reuse [actionHandler] (lihat
     * [com.aether.x.core.booster.GameBoosterActionHandler.applyMaxRefreshRate])
     * + [com.aether.x.core.display.DisplayInfoProvider] — SAMA PERSIS
     * dengan toggle "Paksa Refresh Rate Maksimum" di layar Tweak biasa
     * (lihat TweakViewModel.onForceRefreshChange), supaya nilai Hz yang
     * diterapkan konsisten satu sumber. Kalau ingin MEMATIKAN paksaan ini
     * lagi, pengguna tetap bisa lewat toggle yang sama di Tweak — menu
     * ini sengaja tidak menduplikasi kontrol on/off yang sudah ada di
     * sana.
     */
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
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            actionHandler.takeScreenshot(executor)
        }
    }

    fun onEndSession() {
        GameBoosterOverlayService.stop(getApplication())
    }

    private companion object {
        const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
    }
}
