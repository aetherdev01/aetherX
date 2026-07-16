package com.aether.x.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.core.overlay.CrosshairOverlayService
import com.aether.x.core.overlay.FpsMonitorOverlayService
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppLanguage
import com.aether.x.data.AppPreferences
import com.aether.x.data.CrosshairStyle
import com.aether.x.data.FpsMonitorStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    val state: StateFlow<AppPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences(),
    )

    /**
     * OPSI BARU (permintaan "tambahkan beberapa fitur baru di Settings",
     * MENGGANTIKAN setTemperatureUnit yang dihapus bersamaan dengan opsi
     * Satuan Suhu — permintaan "hapus opsi suhu"): ganti bahasa aplikasi.
     * HANYA persist ke DataStore lewat [AetherXPreferences.setAppLanguage]
     * — TIDAK langsung menerapkan locale ke UI (ViewModel ini cuma punya
     * [Application], bukan [android.app.Activity], jadi tidak bisa memanggil
     * `Activity.recreate()`). Penerapan locale seketika dilakukan oleh
     * caller di [com.aether.x.ui.settings.SettingsScreen] lewat
     * [com.aether.x.data.AppLanguage.applyToRunningActivity] setelah
     * fungsi ini dipanggil — lihat wiring di sana.
     */
    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { preferences.setAppLanguage(language) }
    }

    /**
     * OPSI BARU (permintaan "tambahkan beberapa fitur baru di Settings"):
     * kembalikan preferensi Bahasa, Crosshair, dan Monitor FPS ke nilai
     * default pabrik lewat [AetherXPreferences.resetAll] (data lain seperti
     * lisensi/membership dan Game Profile SENGAJA tidak disentuh — lihat
     * KDoc resetAll()). Overlay crosshair/FPS monitor yang sedang aktif
     * dihentikan di sini (bukan di dalam resetAll() itu sendiri, supaya
     * modul `data` tidak perlu bergantung pada modul `core.overlay`) agar
     * tampilan overlay sungguhan langsung sinkron dengan
     * crosshairEnabled/fpsMonitorEnabled yang baru direset ke false.
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            preferences.resetAll()
            val app = getApplication<Application>()
            CrosshairOverlayService.stop(app)
            FpsMonitorOverlayService.stop(app)
        }
    }

    /** true kalau izin "Tampil di atas aplikasi lain" sudah diberikan. */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(getApplication())

    fun openOverlayPermissionSettings() {
        val app = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${app.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun setCrosshairEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setCrosshairEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled && canDrawOverlays()) {
                CrosshairOverlayService.start(app)
                // FITUR BARU (lihat perintah rework — "perbaiki notifikasi
                // ... setiap aktifkan fitur"): notifikasi heads-up HANYA
                // dikirim kalau service BENAR-BENAR jadi start (permission
                // overlay sudah ada) — mencegah notifikasi "diaktifkan" yang
                // menyesatkan padahal servicenya sendiri gagal jalan.
                AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_crosshair), enabled = true)
            } else {
                CrosshairOverlayService.stop(app)
                if (!enabled) {
                    AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_crosshair), enabled = false)
                }
            }
        }
    }

    fun setCrosshairStyle(style: CrosshairStyle) = updateCrosshair { it.copy(crosshairStyle = style) }

    fun setCrosshairColor(color: Long) = updateCrosshair { it.copy(crosshairColor = color) }

    fun setCrosshairSize(size: Int) = updateCrosshair { it.copy(crosshairSize = size) }

    fun setCrosshairThickness(thickness: Int) = updateCrosshair { it.copy(crosshairThickness = thickness) }

    fun setCrosshairOpacity(opacity: Int) = updateCrosshair { it.copy(crosshairOpacity = opacity) }

    fun setDragMode(enabled: Boolean) {
        CrosshairOverlayService.setDragMode(getApplication(), enabled)
    }

    fun resetCrosshairPosition() {
        viewModelScope.launch { preferences.setCrosshairOffset(0, 0) }
    }

    /**
     * OPSI BARU: kunci/buka kunci posisi crosshair. CATATAN: memanggil
     * preferences.setCrosshairPositionLocked(Boolean) — method ini perlu
     * ditambahkan ke AetherXPreferences (modul `data`), dengan pola persist
     * yang sama seperti setCrosshairOffset di atasnya (DataStore key baru,
     * mis. "crosshair_position_locked").
     */
    fun setCrosshairPositionLocked(locked: Boolean) {
        viewModelScope.launch { preferences.setCrosshairPositionLocked(locked) }
    }

    /**
     * FITUR BARU (lihat perintah rework — "Samakan Section Crosshair
     * Persis seperti foto ke dua dari UI"): dipanggil dari [PositionJoystick]
     * di CrosshairSettingsSection saat pengguna menyeret handle joystick —
     * update posisi X/Y secara langsung (bukan lewat drag-mode overlay di
     * layar lain seperti sebelumnya).
     */
    fun setCrosshairOffset(x: Int, y: Int) {
        viewModelScope.launch { preferences.setCrosshairOffset(x, y) }
    }

    fun setFpsMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFpsMonitorEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled && canDrawOverlays()) {
                FpsMonitorOverlayService.start(app)
                AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_fps_monitor), enabled = true)
            } else {
                FpsMonitorOverlayService.stop(app)
                if (!enabled) {
                    AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_fps_monitor), enabled = false)
                }
            }
        }
    }

    fun setFpsMonitorStyle(style: FpsMonitorStyle) {
        viewModelScope.launch { preferences.setFpsMonitorStyle(style) }
    }

    private fun updateCrosshair(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            val current = state.value
            val updated = transform(current)
            preferences.saveCrosshairConfig(
                style = updated.crosshairStyle,
                color = updated.crosshairColor,
                size = updated.crosshairSize,
                thickness = updated.crosshairThickness,
                opacity = updated.crosshairOpacity,
                offsetX = updated.crosshairOffsetX,
                offsetY = updated.crosshairOffsetY,
            )
        }
    }
}
