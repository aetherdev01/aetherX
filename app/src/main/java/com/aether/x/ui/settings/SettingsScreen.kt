package com.aether.x.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.kernel.KernelDiagnostics
import com.aether.x.data.AppFontStyle
import com.aether.x.data.CardStylePref
import com.aether.x.data.DarkModePref
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.cardEnterAnimation

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
    val scope = rememberCoroutineScope()
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var commandResult by remember { mutableStateOf<String?>(null) }
    var commandBusy by remember { mutableStateOf(false) }

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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        SectionCard(
            title = "Tampilan & Glass",
            watermarkIcon = Icons.Outlined.TextFields,
            modifier = Modifier.cardEnterAnimation(index = 0),
        ) {
            Text("Tema aplikasi", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DarkModePref.LIGHT to "Terang", DarkModePref.DARK to "Gelap", DarkModePref.SYSTEM to "Sistem").forEach { (mode, label) ->
                    if (prefs.darkModePref == mode) Button(onClick = { viewModel.setDarkModePref(mode) }) { Text(label) }
                    else OutlinedButton(onClick = { viewModel.setDarkModePref(mode) }) { Text(label) }
                }
            }
            Text("Font", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AppFontStyle.DEFAULT to "Google Sans", AppFontStyle.SERIF to "Serif", AppFontStyle.MONO to "Monospace").forEach { (font, label) ->
                    if (prefs.appFontStyle == font) Button(onClick = { viewModel.setAppFontStyle(font) }) { Text(label) }
                    else OutlinedButton(onClick = { viewModel.setAppFontStyle(font) }) { Text(label) }
                }
            }
            Text("Gaya kartu", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CardStylePref.GLASS to "Glass", CardStylePref.SOFT to "Soft", CardStylePref.OUTLINED to "Outline").forEach { (style, label) ->
                    if (prefs.cardStylePref == style) Button(onClick = { viewModel.setCardStyle(style) }) { Text(label) }
                    else OutlinedButton(onClick = { viewModel.setCardStyle(style) }) { Text(label) }
                }
            }
            Text("Blur latar ${"%.0f".format(prefs.glassIntensity * 100)}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = prefs.glassIntensity, onValueChange = viewModel::setGlassIntensity, valueRange = 0f..1f)
            Text("Mengatur kekuatan blur latar yang ditangkap di navbar dan dialog, serta tingkat tint permukaannya. Pada perangkat lambat, turunkan nilai untuk menjaga kelancaran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(
            title = "Advanced · Sistem",
            watermarkIcon = Icons.Outlined.Tune,
            modifier = Modifier.cardEnterAnimation(index = 1),
        ) {
            Text("Perintah sistem membutuhkan akses root yang aktif. Simpan pekerjaan sebelum reboot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { pendingCommand = "reboot" }, modifier = Modifier.fillMaxWidth()) { Text("Reboot perangkat") }
            OutlinedButton(onClick = { pendingCommand = "reboot recovery" }, modifier = Modifier.fillMaxWidth()) { Text("Reboot ke Recovery") }
            OutlinedButton(onClick = { pendingCommand = "reboot bootloader" }, modifier = Modifier.fillMaxWidth()) { Text("Reboot ke Bootloader") }
            OutlinedButton(
                onClick = {
                    commandBusy = true
                    scope.launch {
                        val result = viewModel.runRootCommand("uname -a; getprop ro.build.version.release; getprop ro.product.cpu.abi; getprop ro.hardware; id")
                        commandResult = (if (result.first) "Pemeriksaan berhasil" else "Pemeriksaan gagal") + "\n" + result.second
                        commandBusy = false
                    }
                }, enabled = !commandBusy, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (commandBusy) "Memeriksa…" else "Periksa kernel, ABI & akses root") }
            OutlinedButton(
                onClick = {
                    commandBusy = true
                    scope.launch {
                        val result = viewModel.runRootCommand(KernelDiagnostics.readOnlyCommand)
                        commandResult = (if (result.first) "Diagnostik selesai" else "Diagnostik gagal") + "\n" + result.second
                        commandBusy = false
                    }
                }, enabled = !commandBusy, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (commandBusy) "Mengumpulkan diagnostik…" else "Diagnostik kernel (read-only)") }
            commandResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

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
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        SectionCard(
            title = stringResource(R.string.settings_section_fps_monitor),
            watermarkIcon = Icons.Outlined.Speed,
            modifier = Modifier.cardEnterAnimation(index = 1),
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

    pendingCommand?.let { command ->
        PopupDialog(
            onDismissRequest = { pendingCommand = null },
            title = "Konfirmasi tindakan sistem",
            message = "Jalankan '$command'? Perangkat akan memulai ulang dan aplikasi yang terbuka bisa kehilangan data yang belum disimpan.",
            confirmLabel = "Lanjutkan",
            onConfirm = {
                pendingCommand = null
                commandBusy = true
                scope.launch {
                    val result = viewModel.runRootCommand(command)
                    commandResult = (if (result.first) "Perintah diterima" else "Perintah gagal") + "\n" + result.second
                    commandBusy = false
                }
            },
            dismissLabel = "Batal",
        )
    }
}
