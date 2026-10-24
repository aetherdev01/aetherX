package com.aether.x.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import com.aether.x.ui.components.withoutBottom
import com.aether.x.BuildConfig
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.update.UpdateViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.core.locale.str

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onManageAccess: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var overlayGranted by remember { mutableStateOf(viewModel.canDrawOverlays()) }

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val preferences = remember(context.applicationContext) { AetherXPreferences(context.applicationContext) }
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

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
            .padding(contentPadding.withoutBottom())
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = str(R.string.settings_atur_tampilan_kontrol_game_interaksi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroupLabel(stringResource(R.string.settings_group_language))
        LanguageSettingsSection(modifier = Modifier.cardEnterAnimation(index = 0))

        SettingsGroupLabel(str(R.string.settings_group_appearance))
        AppearanceSettingsSection(
            darkModePref = prefs.darkModePref,
            accentPreset = prefs.accentPreset,
            customAccentArgb = prefs.customAccentArgb,
            blurStrength = prefs.blurStrength,
            liquidGlassLevel = prefs.liquidGlassLevel,
            onDarkModeChange = viewModel::setDarkModePref,
            onAccentChange = viewModel::setAccentPreset,
            onCustomAccentChange = viewModel::setCustomAccentArgb,
            onBlurChange = viewModel::setBlurStrength,
            onLiquidGlassLevelChange = viewModel::setLiquidGlassLevel,
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        SettingsGroupLabel(str(R.string.settings_kontrol_overlay))
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

        SettingsGroupLabel(str(R.string.settings_group_interaction))
        SectionCard(
            title = str(R.string.settings_gerakan_ui_haptik),
            watermarkIcon = Icons.Outlined.Vibration,
            modifier = Modifier.cardEnterAnimation(index = 2),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(str(R.string.settings_getaran_sentuhan), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.settings_respons_haptik_pada_kontrol_yang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = { enabled -> scope.launch { preferences.setHapticsEnabled(enabled) } },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(str(R.string.settings_kurangi_animasi), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.settings_kurangi_pergerakan_transisi_dan_animasi), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.reduceMotionEnabled,
                    onCheckedChange = { enabled -> scope.launch { preferences.setReduceMotionEnabled(enabled) } },
                )
            }
            Text(str(R.string.settings_kekuatan_blur_diatur_pada_bagian), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsGroupLabel(str(R.string.settings_group_system))
        SectionCard(
            title = str(R.string.settings_pusat_pembaruan),
            watermarkIcon = Icons.Outlined.Refresh,
            modifier = Modifier.cardEnterAnimation(index = 3),
        ) {
            SettingsInfoRow(str(R.string.settings_versi_terpasang), BuildConfig.VERSION_NAME)
            val update = updateState.info
            when {
                update.latestVersionCode <= 0 -> Text(
                    str(R.string.settings_informasi_update_belum_tersedia_periksa),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                update.latestVersionCode > updateState.currentVersionCode -> {
                    SettingsInfoRow(str(R.string.settings_versi_tersedia), update.latestVersionName.ifBlank { str(R.string.settings_build, update.latestVersionCode) })
                    Button(
                        onClick = {
                            runCatching {
                                val uri = Uri.parse(update.downloadUrl)
                                require(uri.scheme == "https")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        },
                        enabled = update.downloadUrl.startsWith("https://"),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(str(R.string.settings_buka_unduhan_update)) }
                }
                else -> Text(
                    str(R.string.settings_aplikasi_sudah_memakai_versi_terbaru),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

    }
}


@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, start = 2.dp),
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
