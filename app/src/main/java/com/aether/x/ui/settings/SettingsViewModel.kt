package com.aether.x.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.core.overlay.CrosshairOverlayService
import com.aether.x.core.overlay.FpsMonitorOverlayService
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppPreferences
import com.aether.x.data.CrosshairStyle
import com.aether.x.data.FpsMonitorStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    val state: StateFlow<AppPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences(),
    )

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(getApplication())

    fun openOverlayPermissionSettings() {
        val app = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${app.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    fun setCrosshairEnabled(enabled: Boolean, activity: Activity? = null) {
        viewModelScope.launch {
            preferences.setCrosshairEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled && canDrawOverlays()) {
                CrosshairOverlayService.start(app)

                AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_crosshair), enabled = true)
            } else {
                CrosshairOverlayService.stop(app)
                if (!enabled) {
                    AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_crosshair), enabled = false)
                }
            }
            maybeShowAd(activity)
        }
    }

    fun setCrosshairStyle(style: CrosshairStyle) = updateCrosshair { it.copy(crosshairStyle = style) }

    fun setCrosshairColor(color: Long) = updateCrosshair { it.copy(crosshairColor = color) }

    fun setCrosshairSize(size: Int) = updateCrosshair { it.copy(crosshairSize = size) }

    fun setCrosshairRotation(degrees: Int) = updateCrosshair { it.copy(crosshairRotationDegrees = degrees) }

    fun setFpsMonitorEnabled(enabled: Boolean, activity: Activity? = null) {
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
            maybeShowAd(activity)
        }
    }

    private suspend fun maybeShowAd(activity: Activity?) {
        if (activity == null) return
        val isMember = preferences.preferences.first().isMembershipActive
        AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
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
                rotationDegrees = updated.crosshairRotationDegrees,
                offsetX = updated.crosshairOffsetX,
                offsetY = updated.crosshairOffsetY,
            )
        }
    }
}
