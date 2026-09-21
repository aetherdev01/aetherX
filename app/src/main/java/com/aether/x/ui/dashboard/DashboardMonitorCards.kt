package com.aether.x.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Android
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.Spacing

@Composable
fun AetherXInfoCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_aetherx_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_app_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.dashboard_hero_kicker),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun GameActivitySection(
    games: List<InstalledGameEntry>,
    loading: Boolean,
    lastPlayedPackage: String?,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.dashboard_section_game_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .padding(top = Spacing.md),
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
                modifier = Modifier.padding(top = Spacing.md),
            )
            else -> LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm)
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
            .width(80.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Frame seragam untuk ikon game agar terlihat rapi
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp
        ) {
            Image(
                bitmap = entry.icon,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.sm))
        
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isLastPlayed) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isLastPlayed) {
            Text(
                text = stringResource(R.string.dashboard_game_activity_last_used),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DeviceInfoSection(info: DeviceInfoSnapshot?, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.dashboard_section_device_info), 
        modifier = modifier, 
        watermarkIcon = Icons.Outlined.PhoneAndroid
    ) {
        if (info == null) {
            Text(
                text = stringResource(R.string.dashboard_device_info_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Grup Info Identitas Perangkat
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.dashboard_device_info_group_identity),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            DeviceInfoRow(
                icon = Icons.Outlined.PhoneAndroid,
                label = stringResource(R.string.dashboard_device_model),
                value = "${info.manufacturer} ${info.model}".trim(),
            )
            DeviceInfoRow(
                icon = Icons.Outlined.DeveloperBoard,
                label = stringResource(R.string.dashboard_device_chipset),
                value = info.board.ifBlank { "-" },
            )
            DeviceInfoRow(
                icon = Icons.Outlined.Android,
                label = stringResource(R.string.dashboard_device_android_version),
                value = stringResource(R.string.dashboard_device_android_version_format, info.androidVersion, info.sdkInt),
            )
            DeviceInfoRow(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.dashboard_device_cpu_abi),
                value = info.cpuAbi.ifBlank { "-" },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Grup Info Penggunaan (Usage)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.dashboard_device_info_group_usage),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            val usedRam = info.totalRamBytes - info.availableRamBytes
            UsageBarRow(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.dashboard_device_ram),
                usedLabel = usedRam.toGbLabel(),
                totalLabel = info.totalRamBytes.toGbLabel(),
                progress = if (info.totalRamBytes > 0) usedRam.toFloat() / info.totalRamBytes.toFloat() else 0f,
            )
            val usedStorage = info.totalStorageBytes - info.availableStorageBytes
            UsageBarRow(
                icon = Icons.Outlined.SdStorage,
                label = stringResource(R.string.dashboard_device_storage),
                usedLabel = usedStorage.toGbLabel(),
                totalLabel = info.totalStorageBytes.toGbLabel(),
                progress = if (info.totalStorageBytes > 0) usedStorage.toFloat() / info.totalStorageBytes.toFloat() else 0f,
            )
        }
    }
}

@Composable
private fun UsageBarRow(
    icon: ImageVector,
    label: String,
    usedLabel: String,
    totalLabel: String,
    progress: Float,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val barColor = when {
        clampedProgress < 0.7f -> MaterialTheme.colorScheme.primary
        clampedProgress < 0.9f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.dashboard_device_usage_format, usedLabel, totalLabel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { clampedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DeviceInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
