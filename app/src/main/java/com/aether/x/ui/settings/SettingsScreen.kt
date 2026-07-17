package com.aether.x.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.adb.WirelessDebuggingMonitor
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.SectionCard
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
    var dragModeActive by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(viewModel.canDrawOverlays()) }

    // FITUR BARU — kartu pintasan "Aktifkan Wireless Debugging" (khusus No
    // Root/ADB, lihat KDoc WirelessDebuggingQuickCard di DashboardMonitorCards.kt).
    val context = LocalContext.current
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
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // FITUR BARU — lihat KDoc WirelessDebuggingQuickCard: kartu ini
        // sendiri yang memutuskan untuk tidak merender apa pun kalau backend
        // aktif Root atau toggle Wireless debugging sedang menyala.
        WirelessDebuggingQuickCard(
            activeBackend = privilegeStatus.activeBackend,
            wirelessDebuggingEnabled = wirelessDebuggingEnabled,
            onOpenWirelessDebugging = { PrivilegeManager.openWirelessDebuggingSettings(context) },
        )

        // Section "Tampilan" (pemilih tema) dihapus total — aplikasi hanya
        // mendukung Mode Gelap (lihat AetherXPreferences.preferences yang
        // selalu mengembalikan DarkModePref.DARK), jadi tidak ada lagi
        // pengaturan tema yang perlu ditampilkan ke pengguna sama sekali.

        // Section "Umum" (pemilih Bahasa + tombol Reset Semua Pengaturan)
        // DIHAPUS TOTAL atas permintaan pengguna ("kurang cocok"). Lihat
        // AppLanguage.kt yang juga dihapus, dan AetherXPreferences.kt /
        // SettingsViewModel.kt yang rollback ke state sebelum fitur ini
        // ditambahkan (appLanguage, setAppLanguage, resetAll dihapus).

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
