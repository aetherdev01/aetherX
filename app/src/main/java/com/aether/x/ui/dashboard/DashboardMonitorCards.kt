package com.aether.x.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.aether.x.core.device.toGbLabel
import com.aether.x.ui.theme.AetherAmber
import com.aether.x.ui.theme.AetherGreen
import com.aether.x.ui.theme.AetherRed
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GameActivitySection(
    games: List<InstalledGameEntry>,
    loading: Boolean,
    lastPlayedPackage: String?,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dibungkus SectionCard yang sama dengan card lain di Dashboard, supaya
    // radius, border, dan padding-nya konsisten (sebelumnya section ini
    // "telanjang" tanpa card sehingga terlihat menyatu/berantakan dengan
    // card di atas dan di bawahnya).
    SectionCard(
        title = stringResource(R.string.dashboard_section_game_activity),
        modifier = modifier.fillMaxWidth(),
        watermarkIcon = Icons.Outlined.SportsEsports,
    ) {
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            games.isEmpty() -> Text(
                text = stringResource(R.string.dashboard_game_activity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(vertical = Spacing.xs),
            ) {
                items(games, key = { it.packageName }) { game ->
                    GameActivityCard(
                        entry = game,
                        isLastPlayed = game.packageName == lastPlayedPackage,
                        onClick = { onGameClick(game.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameActivityCard(
    entry: InstalledGameEntry,
    isLastPlayed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp) // Compact game tile keeps the dashboard balanced
            .clip(MaterialTheme.shapes.large) // Radius interaktif (28.dp)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(60.dp), // Keep icons balanced with other dashboard cards
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Image(
                bitmap = entry.icon,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.sm))
        
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelLarge, // Font diperbesar 
            fontWeight = if (isLastPlayed) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isLastPlayed) {
            Text(
                text = stringResource(R.string.dashboard_game_activity_last_used),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, // AetherCyan
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
fun DeviceStatusSection(
    info: DeviceInfoSnapshot?,
    rootGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val deviceTitle = info?.let { "${it.manufacturer} ${it.model}".trim().ifBlank { "Perangkat Android" } }
        ?: "Mendeteksi perangkat…"
    SectionCard(
        title = deviceTitle,
        watermarkIcon = Icons.Outlined.PhoneAndroid,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (info == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Mendeteksi perangkat...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.line),
            ) {
                Text(
                    text = "Android ${info.androidVersion}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "API ${info.sdkInt} · ${info.cpuCores} core",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = if (rootGranted) "ROOT ON" else "ROOT OFF",
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (rootGranted) AetherGreen else AetherRed,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        // ── Penggunaan: bar berwarna sesuai beban (hijau -> amber -> merah) ──
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.item)) {
            UsageBar(
                icon = Icons.Outlined.Memory,
                label = "RAM",
                detail = "${info.usedRamBytes.toGbLabel()} / ${info.totalRamBytes.toGbLabel()}",
                fraction = info.ramUsedFraction,
            )
            UsageBar(
                icon = Icons.Outlined.SdStorage,
                label = "Storage",
                detail = "${info.usedStorageBytes.toGbLabel()} / ${info.totalStorageBytes.toGbLabel()}",
                fraction = info.storageUsedFraction,
            )
            if (info.batteryPercent >= 0) {
                val tempText = info.batteryTempC?.let { " • ${String.format(Locale.US, "%.1f", it)}°C" }.orEmpty()
                UsageBar(
                    icon = if (info.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                    label = "Baterai",
                    detail = "${info.batteryPercent}%${if (info.isCharging) " • mengisi" else ""}$tempText",
                    fraction = info.batteryPercent / 100f,
                    // Baterai: rendah = buruk (kebalikan RAM/Storage).
                    invert = true,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        // Spesifikasi inti dibuat dalam grid 2×2 supaya dashboard tidak memanjang.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                DeviceStatusTile(
                    icon = Icons.Outlined.Memory,
                    label = "CPU",
                    value = "${info.cpuCores} core · ${info.cpuAbi.substringBefore('-').ifBlank { "-" }}",
                    modifier = Modifier.weight(1f),
                )
                DeviceStatusTile(
                    icon = Icons.Outlined.Monitor,
                    label = "Refresh rate",
                    value = if (info.refreshRateHz > 0f) "${info.refreshRateHz.roundToInt()} Hz" else "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                DeviceStatusTile(
                    icon = Icons.Outlined.DeveloperBoard,
                    label = "Chipset",
                    value = info.socModel.ifBlank { info.hardware.ifBlank { info.board } }.ifBlank { "Tidak diketahui" },
                    modifier = Modifier.weight(1f),
                )
                DeviceStatusTile(
                    icon = Icons.Outlined.Security,
                    label = "Akses",
                    value = if (rootGranted) "Root aktif" else "No Root",
                    valueColor = if (rootGranted) AetherGreen else AetherRed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DeviceStatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Bar penggunaan berlabel; warna otomatis mengikuti beban. */
@Composable
private fun UsageBar(
    icon: ImageVector,
    label: String,
    detail: String,
    fraction: Float,
    invert: Boolean = false,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    // Beban tinggi = merah. Untuk baterai (invert) sisa rendah = merah.
    val load = if (invert) 1f - clamped else clamped
    val color = when {
        load < 0.60f -> AetherGreen
        load < 0.85f -> AetherAmber
        else -> AetherRed
    }
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 500),
        label = "usageBar",
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(66.dp),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = AetherMonoFamily,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

