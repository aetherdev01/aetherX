package com.aether.x.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.TemperatureUnit
import com.aether.x.ui.components.SectionCard

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onManageAccess: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    var dragModeActive by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(viewModel.canDrawOverlays()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = viewModel.canDrawOverlays()
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
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Section "Tampilan" (pemilih tema) dihapus total — aplikasi hanya
        // mendukung Mode Gelap (lihat AetherXPreferences.preferences yang
        // selalu mengembalikan DarkModePref.DARK), jadi tidak ada lagi
        // pengaturan tema yang perlu ditampilkan ke pengguna sama sekali.

        SectionCard(title = stringResource(R.string.settings_section_general), watermarkIcon = Icons.Outlined.Settings) {
            Column {
                Text(
                    text = stringResource(R.string.settings_temperature_unit_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val options = listOf(
                        TemperatureUnit.CELSIUS to stringResource(R.string.settings_temperature_unit_celsius),
                        TemperatureUnit.FAHRENHEIT to stringResource(R.string.settings_temperature_unit_fahrenheit),
                    )
                    options.forEachIndexed { index, (unit, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            selected = prefs.temperatureUnit == unit,
                            onClick = { viewModel.setTemperatureUnit(unit) },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        // REWORK TOTAL (lihat perintah rework terbaru — "Samakan Section
        // Crosshair Persis seperti foto ke dua dari UI"): CrosshairSettingsSection
        // SEKARANG menggambar kartu besarnya SENDIRI (watermark logo,
        // toggle besar di pojok kanan atas, dst. — lihat KDoc di
        // CrosshairSettingsSection.kt), BUKAN lagi konten polos di dalam
        // SectionCard generik seperti section lain di layar ini.
        CrosshairSettingsSection(
            enabled = prefs.crosshairEnabled,
            style = prefs.crosshairStyle,
            colorArgb = prefs.crosshairColor,
            sizeDp = prefs.crosshairSize,
            thicknessDp = prefs.crosshairThickness,
            opacityPercent = prefs.crosshairOpacity,
            offsetX = prefs.crosshairOffsetX,
            offsetY = prefs.crosshairOffsetY,
            overlayPermissionGranted = overlayGranted,
            dragModeActive = dragModeActive,
            onEnabledChange = viewModel::setCrosshairEnabled,
            onRequestOverlayPermission = viewModel::openOverlayPermissionSettings,
            onStyleChange = viewModel::setCrosshairStyle,
            onColorChange = viewModel::setCrosshairColor,
            onSizeChange = viewModel::setCrosshairSize,
            onThicknessChange = viewModel::setCrosshairThickness,
            onOpacityChange = viewModel::setCrosshairOpacity,
            onToggleDragMode = { active ->
                dragModeActive = active
                viewModel.setDragMode(active)
            },
            onResetPosition = viewModel::resetCrosshairPosition,
            onOffsetChange = viewModel::setCrosshairOffset,
        )

        SectionCard(title = stringResource(R.string.settings_section_fps_monitor), watermarkIcon = Icons.Outlined.Speed) {
            FpsMonitorSettingsSection(
                enabled = prefs.fpsMonitorEnabled,
                style = prefs.fpsMonitorStyle,
                overlayPermissionGranted = overlayGranted,
                hasShellAccess = privilegeStatus.hasAccess,
                onEnabledChange = viewModel::setFpsMonitorEnabled,
                onRequestOverlayPermission = viewModel::openOverlayPermissionSettings,
                onStyleChange = viewModel::setFpsMonitorStyle,
            )
        }

        // Section "Tentang" (identitas app + tautan komunitas) dipindah TOTAL
        // ke tab About tersendiri (lihat AboutScreen) — lihat perintah
        // rework. Tidak ada lagi apa pun soal "Tentang" di tab Settings.
    }
}
