package com.aether.x.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.TemperatureUnit
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakSwitch

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

        SectionCard(title = stringResource(R.string.settings_section_general)) {
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

        SectionCard(title = stringResource(R.string.settings_section_crosshair)) {
            CrosshairSettingsSection(
                enabled = prefs.crosshairEnabled,
                style = prefs.crosshairStyle,
                colorArgb = prefs.crosshairColor,
                sizeDp = prefs.crosshairSize,
                thicknessDp = prefs.crosshairThickness,
                opacityPercent = prefs.crosshairOpacity,
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
            )
        }

        SectionCard(title = stringResource(R.string.settings_section_fps_monitor)) {
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

        SectionCard(title = stringResource(R.string.settings_section_about)) {
            AboutSection(versionName = BuildConfig.VERSION_NAME)
        }
    }
}

/**
 * Kartu "Tentang" yang lebih profesional: identitas app (nama + versi) sebagai
 * baris utama dengan lencana versi, lalu info maintainer di baris kedua yang
 * lebih ringkas — bukan lagi tumpukan Text kaku baris demi baris.
 */
@Composable
private fun AboutSection(versionName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = stringResource(R.string.settings_maintainer_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column {
                Text(
                    text = stringResource(R.string.settings_maintainer_name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_maintainer_handle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SocialLinkRow(
                iconRes = R.drawable.ic_social_github,
                // Logo GitHub (Octocat) cuma satu warna solid di vector-nya, jadi
                // aman di-tint mengikuti tema: putih di mode gelap (supaya tidak
                // "hilang" di atas background gelap), warna resmi #181717 (hampir
                // hitam) di mode terang seperti brand guideline GitHub. Ikon
                // lain (Telegram) TIDAK dikasih tint karena logonya multi-warna.
                iconTint = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                    Color.White
                } else {
                    Color(0xFF181717)
                },
                label = stringResource(R.string.settings_social_github),
                handle = stringResource(R.string.settings_social_github_handle),
                url = stringResource(R.string.settings_social_github_url),
            )
            SocialLinkRow(
                iconRes = R.drawable.ic_social_telegram,
                iconTint = null,
                label = stringResource(R.string.settings_social_telegram),
                handle = stringResource(R.string.settings_social_telegram_handle),
                url = stringResource(R.string.settings_social_telegram_url),
            )
        }
    }
}

/**
 * Baris tautan sosial (GitHub / Telegram) dengan ikon berwarna resmi masing-
 * masing platform. Ketuk baris untuk membuka tautan di browser/aplikasi.
 */
@Composable
private fun SocialLinkRow(
    iconRes: Int,
    label: String,
    handle: String,
    url: String,
    // null = tampilkan ikon apa adanya (warna asli multi-warna, mis. Telegram).
    // Non-null = ikon di-tint satu warna ini (dipakai utamanya untuk logo
    // GitHub yang aslinya solid #181717, supaya bisa "dibalik" jadi putih di
    // mode tema gelap dan tetap kebaca di atas background gelap).
    iconTint: Color? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // Tidak ada aplikasi/browser yang bisa menangani intent ini — abaikan
                    // dengan aman daripada membuat aplikasi crash.
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            colorFilter = iconTint?.let { ColorFilter.tint(it) },
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
