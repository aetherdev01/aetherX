package com.aether.x.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aether.x.data.AppLanguage
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
    // OPSI BARU (permintaan "tambahkan beberapa fitur baru di Settings"):
    // reset semua preferensi tampilan (bahasa, crosshair, FPS monitor)
    // kembali ke default pabrik — lihat viewModel.resetAllSettings() &
    // AetherXPreferences.resetAll() (modul `data`, di luar zip ini).
    // Dialog konfirmasi dulu supaya tidak ke-tap tidak sengaja, karena
    // aksi ini tidak bisa di-undo.
    var showResetConfirmDialog by remember { mutableStateOf(false) }

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

        // OPSI BARU (permintaan "tambahkan beberapa fitur baru di
        // Settings"): pemilih Bahasa aplikasi (Indonesia/English),
        // MENGGANTIKAN opsi Satuan Suhu yang dihapus (permintaan "hapus
        // opsi suhu" — tidak ada lagi konsumer nilainya di layar mana pun
        // setelah monitor CPU/GPU/Suhu dipindah jadi domain Game Booster,
        // lihat komentar dashboard_monitor_* di strings.xml).
        //
        // Menggunakan AppCompatDelegate.setApplicationLocales (Android
        // per-app language API, lihat AppLanguage.applyToApp() di modul
        // `data`) — TIDAK butuh restart Activity manual, sistem otomatis
        // me-recreate Activity yang aktif dengan locale baru.
        SectionCard(title = stringResource(R.string.settings_section_general), watermarkIcon = Icons.Outlined.Settings) {
            Column {
                Text(
                    text = stringResource(R.string.settings_language_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val options = listOf(
                        AppLanguage.INDONESIAN to stringResource(R.string.settings_language_indonesian),
                        AppLanguage.ENGLISH to stringResource(R.string.settings_language_english),
                    )
                    options.forEachIndexed { index, (language, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            selected = prefs.appLanguage == language,
                            onClick = { viewModel.setAppLanguage(language) },
                        ) {
                            Text(label)
                        }
                    }
                }

                // OPSI BARU: reset semua pengaturan ke default — lihat KDoc
                // showResetConfirmDialog di atas.
                Text(
                    text = stringResource(R.string.settings_reset_all_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = stringResource(R.string.settings_reset_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier.padding(top = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_all_button))
                }
            }
        }

        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text(stringResource(R.string.settings_reset_all_confirm_title)) },
                text = { Text(stringResource(R.string.settings_reset_all_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetAllSettings()
                        showResetConfirmDialog = false
                    }) {
                        Text(
                            text = stringResource(R.string.settings_reset_all_confirm_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text(stringResource(R.string.crosshair_custom_color_cancel))
                    }
                },
            )
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
            // OPSI BARU: kunci posisi crosshair — lihat KDoc parameter di
            // CrosshairSettingsSection.kt. prefs.crosshairPositionLocked
            // HARUS ditambahkan ke data class AppPreferences (modul `data`),
            // begitu juga setCrosshairPositionLocked(Boolean) di
            // AetherXPreferences.
            positionLocked = prefs.crosshairPositionLocked,
            onPositionLockedChange = viewModel::setCrosshairPositionLocked,
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
