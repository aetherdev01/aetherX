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
import androidx.compose.ui.unit.sp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing

@Composable
fun AetherXInfoCard(
    modifier: Modifier = Modifier,
) {
    Card(
        // fillMaxWidth tanpa padding horizontal ekstra untuk meminimalkan ruang kosong di layar
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        // Menggunakan radius medium (22.dp) untuk panel data statis
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Padding disamakan dengan card lain (Spacing.lg) — sebelumnya
                // vertikalnya xxl (24dp) sehingga kartu ini terasa jauh lebih
                // "lega" dan tidak sebaris dengan tinggi card lain di Dashboard.
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
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_app_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.dashboard_hero_kicker),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
            .width(88.dp) // Lebar kartu game diperbesar
            .clip(MaterialTheme.shapes.large) // Radius interaktif (28.dp)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(72.dp), // Area ikon diperbesar
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
    SectionCard(
        title = "Status Perangkat",
        modifier = modifier.fillMaxWidth(),
        watermarkIcon = Icons.Outlined.PhoneAndroid,
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
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Perangkat terdeteksi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${info.manufacturer} ${info.model}".trim(),
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
                    text = "READY",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DeviceStatusItem(
                icon = Icons.Outlined.Android,
                label = "Android",
                value = "${info.androidVersion} • API ${info.sdkInt}",
            )
            DeviceStatusItem(
                icon = Icons.Outlined.DeveloperBoard,
                label = "Akses",
                value = if (rootGranted) "Root aktif" else "No Root",
            )
            DeviceStatusItem(
                icon = Icons.Outlined.Memory,
                label = "RAM",
                value = "${(info.availableRamBytes / (1024.0 * 1024.0 * 1024.0)).let { String.format(java.util.Locale.US, "%.1f", it) }} GB tersedia",
            )
            DeviceStatusItem(
                icon = Icons.Outlined.SdStorage,
                label = "Storage",
                value = "${((info.totalStorageBytes - info.availableStorageBytes) / (1024.0 * 1024.0 * 1024.0)).let { String.format(java.util.Locale.US, "%.1f", it) }} GB terpakai",
            )
        }
    }
}

@Composable
private fun DeviceStatusItem(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(66.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = AetherMonoFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DeviceInfoSection(info: DeviceInfoSnapshot?, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.dashboard_section_device_info),
        modifier = modifier.fillMaxWidth(),
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

        // Sebelumnya tiap grup punya background abu-abu + padding sendiri di
        // DALAM SectionCard yang sudah punya padding (20dp) — hasilnya "kotak
        // dalam kotak" dengan spacing dobel dan terasa sempit/berantakan.
        // Sekarang grup cukup dipisah lewat label kecil, tanpa background
        // tambahan, spacing antar baris lebih lega (lg, bukan md) supaya
        // enak dibaca.
        DeviceInfoGroupLabel(stringResource(R.string.dashboard_device_info_group_identity))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
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
            modifier = Modifier.padding(vertical = Spacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        DeviceInfoGroupLabel(stringResource(R.string.dashboard_device_info_group_usage))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
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
private fun DeviceInfoGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
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
        clampedProgress < 0.9f -> MaterialTheme.colorScheme.tertiary // AetherAmber
        else -> MaterialTheme.colorScheme.error // AetherRed
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.dashboard_device_usage_format, usedLabel, totalLabel),
                // Sebelumnya titleMedium (16sp) + Bold — di font monospace,
                // ukuran+weight sebesar itu bikin angka terasa "kejedug"/berat
                // dan kurang jelas dibaca (lihat feedback dari screenshot).
                // Diturunkan ke bodyMedium (14sp) + SemiBold, disamakan skalanya
                // dengan baris identitas perangkat di atasnya (DeviceInfoRow),
                // plus sedikit letterSpacing supaya digit tidak berdesakan.
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.3.sp),
                fontFamily = AetherMonoFamily, // Font khusus pembacaan nilai numerik
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LinearProgressIndicator(
            progress = { clampedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp) // Sebelumnya 10dp — sedikit lebih ramping biar tidak dominan
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
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = AetherMonoFamily, // Font khusus pembacaan nilai teknis
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
