package com.aether.x.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.shizuku.WirelessDebuggingMonitor
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.dashboard.WirelessDebuggingQuickCard

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onManageAccess: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    var overlayGranted by remember { mutableStateOf(viewModel.canDrawOverlays()) }

    val context = LocalContext.current
    val activity = context as? Activity
    val wirelessDebuggingEnabled by WirelessDebuggingMonitor.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = viewModel.canDrawOverlays()
                WirelessDebuggingMonitor.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        WirelessDebuggingQuickCard(
            activeBackend = privilegeStatus.activeBackend,
            wirelessDebuggingEnabled = wirelessDebuggingEnabled,
            onOpenWirelessDebugging = { PrivilegeManager.openWirelessDebuggingSettings(context) },
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        CrosshairSettingsSection(
            enabled = prefs.crosshairEnabled,
            style = prefs.crosshairStyle,
            colorArgb = prefs.crosshairColor,
            sizeDp = prefs.crosshairSize,
            rotationDegrees = prefs.crosshairRotationDegrees,
            offsetX = prefs.crosshairOffsetX,
            offsetY = prefs.crosshairOffsetY,
            overlayPermissionGranted = overlayGranted,

            onEnabledChange = { enabled -> viewModel.setCrosshairEnabled(enabled, activity) },
            onRequestOverlayPermission = viewModel::openOverlayPermissionSettings,
            onStyleChange = viewModel::setCrosshairStyle,
            onColorChange = viewModel::setCrosshairColor,
            onSizeChange = viewModel::setCrosshairSize,
            onRotationChange = viewModel::setCrosshairRotation,
            onNudgePosition = viewModel::nudgeCrosshairPosition,
            modifier = Modifier.cardEnterAnimation(index = 1),
        )

        SectionCard(
            title = stringResource(R.string.settings_section_fps_monitor),
            watermarkIcon = Icons.Outlined.Speed,
            modifier = Modifier.cardEnterAnimation(index = 2),
        ) {
            FpsMonitorSettingsSection(
                enabled = prefs.fpsMonitorEnabled,
                style = prefs.fpsMonitorStyle,
                overlayPermissionGranted = overlayGranted,
                hasShellAccess = privilegeStatus.hasAccess,
                onEnabledChange = { enabled -> viewModel.setFpsMonitorEnabled(enabled, activity) },
                onRequestOverlayPermission = viewModel::openOverlayPermissionSettings,
                onStyleChange = viewModel::setFpsMonitorStyle,
            )
        }

    }
}
