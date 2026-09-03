package com.aether.x.core.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aether.x.MainActivity
import com.aether.x.R
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.core.overlay.FpsMonitorOverlayService
import com.aether.x.data.AetherXPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Tile Quick Settings untuk toggle Monitor FPS tanpa buka app — lihat
 * KDoc [CrosshairTileService], pola & alasan desainnya identik, cuma
 * beda target overlay service + DataStore key.
 */
class FpsMonitorTileService : TileService() {

    private var listenScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val preferences = AetherXPreferences(applicationContext)
        val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
        listenScope = scope
        preferences.preferences
            .onEach { prefs -> updateTileState(prefs.fpsMonitorEnabled) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        super.onStopListening()
        listenScope?.cancel()
        listenScope = null
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext

        if (!Settings.canDrawOverlays(app)) {
            val intent = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    app,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return
        }

        CoroutineScope(Dispatchers.Main.immediate).launch {
            val preferences = AetherXPreferences(app)
            val current = preferences.preferences.first().fpsMonitorEnabled
            val newValue = !current
            preferences.setFpsMonitorEnabled(newValue)
            if (newValue) {
                FpsMonitorOverlayService.start(app)
                AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_fps_monitor), enabled = true)
            } else {
                FpsMonitorOverlayService.stop(app)
                AetherXNotifier.notifyFeatureToggled(app, app.getString(R.string.feature_name_fps_monitor), enabled = false)
            }
            updateTileState(newValue)
        }
    }

    private fun updateTileState(enabled: Boolean) {
        val tile: Tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.feature_name_fps_monitor)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_mark)
        tile.updateTile()
    }
}
