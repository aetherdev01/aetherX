package com.aether.x.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.overlay.CrosshairOverlayService
import com.aether.x.core.overlay.FpsMonitorOverlayService
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppPreferences
import com.aether.x.data.CrosshairStyle
import com.aether.x.data.FpsMonitorStyle
import com.aether.x.data.TemperatureUnit
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

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { preferences.setTemperatureUnit(unit) }
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
            } else {
                CrosshairOverlayService.stop(app)
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

    fun setFpsMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setFpsMonitorEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled && canDrawOverlays()) {
                FpsMonitorOverlayService.start(app)
            } else {
                FpsMonitorOverlayService.stop(app)
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
