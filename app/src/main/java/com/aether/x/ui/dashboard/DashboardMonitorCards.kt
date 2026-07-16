package com.aether.x.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.Spacing

/**
 * REWORK TOTAL (lihat perintah rework — "rework total tampilan Dashboard...
 * karena text AetherX terlalu banyak"): kartu hero SEBELUMNYA menampilkan
 * kicker "AETHERX" + judul besar "AetherX" LAGI — redundan dengan
 * [com.aether.x.ui.tweak.TweakHeader] yang SUDAH menampilkan logo + "AetherX"
 * secara permanen tepat di atas kartu ini (untuk SEMUA sub-tab). Sekarang
 * hero card ini HANYA menampilkan logo bulat kecil + versi + pill mode akses
 * dalam SATU baris ramping — tidak lagi mengulang nama aplikasi sama sekali.
 *
 * Logo SEKARANG memakai [R.drawable.ic_aetherx_logo] (PNG logo resmi
 * lengkap, bulat) mengikuti permintaan eksplisit — BUKAN lagi
 * [R.drawable.ic_aetherx_mark] (vector mark "X" dekoratif transparan besar
 * yang dipakai versi sebelumnya).
 *
 * WARNA SEKARANG MENGIKUTI TEMA DEFAULT (lihat perintah rework — "warna
 * card ... default mengikuti warna tema bawaan"): gradient
 * DashboardHeroStart/DashboardHeroEnd (coklat-terracotta custom, terpisah
 * dari identitas warna app) DIHAPUS — kartu ini sekarang memakai
 * `MaterialTheme.colorScheme.surfaceVariant`/`primary` (biru [AccentBlue],
 * warna aksen utama app di seluruh layar lain), konsisten dengan kartu
 * Membership/Game Profile/dll yang semuanya sudah biru.
 */
@Composable
fun AetherXInfoCard(
    activeBackend: PrivilegeBackend,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_aetherx_logo),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.dashboard_app_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.dashboard_hero_kicker),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        PrivilegeBackendPill(activeBackend)
    }
}

/**
 * Pill status mode akses — SEKARANG memakai `MaterialTheme.colorScheme.primary`
 * (biru tema default) untuk Root, bukan lagi DashboardPillBrown custom —
 * lihat KDoc [AetherXInfoCard] soal alasan warna mengikuti tema bawaan.
 */
@Composable
private fun PrivilegeBackendPill(backend: PrivilegeBackend) {
    val (label, bgColor) = when (backend) {
        PrivilegeBackend.ROOT -> stringResource(R.string.dashboard_privilege_root) to MaterialTheme.colorScheme.primary
        PrivilegeBackend.ADB -> stringResource(R.string.dashboard_privilege_adb) to MaterialTheme.colorScheme.surface
        PrivilegeBackend.NONE -> stringResource(R.string.dashboard_privilege_none) to MaterialTheme.colorScheme.surface
    }
    val textColor = if (backend == PrivilegeBackend.ROOT) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

/**
 * FITUR BARU — section "Aktivitas Game" di Dashboard (lihat perintah rework):
 * daftar game terpasang sebagai kartu vertikal (ikon besar + nama) yang
 * bisa di-scroll horizontal kiri-kanan ([LazyRow]). Game yang TERAKHIR
 * dipakai ditampilkan PALING KIRI dengan chip "Terakhir dipakai" di bawah
 * ikonnya (lihat [DashboardViewModel.reorderByLastPlayed]) — sisanya
 * alfabet seperti urutan asli [com.aether.x.core.apps.GameProfileCatalog].
 * Mengetuk kartu manapun langsung membuka game itu lewat
 * [com.aether.x.core.apps.GameLaunchTracker].
 */
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.dashboard_section_game_activity),
                style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().height(112.dp).padding(top = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            games.isEmpty() -> Text(
                text = stringResource(R.string.dashboard_game_activity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            else -> LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
            .width(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm).wrapContentWidth(),
        )
        if (isLastPlayed) {
            Text(
                text = stringResource(R.string.dashboard_game_activity_last_used),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * Section "Info Device": model, chipset/board, versi Android, RAM, dan
 * penyimpanan — SEMUA tersedia tanpa Shizuku/Root, jadi section ini selalu
 * tampil terlepas dari backend akses yang aktif. Tidak berubah dari versi
 * sebelumnya (statis, tidak butuh polling — lihat KDoc [DashboardViewModel]
 * soal kenapa monitor CPU/GPU/Suhu dihapus dari Dashboard, bukan section ini).
 */
@Composable
fun DeviceInfoSection(info: DeviceInfoSnapshot?, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.dashboard_section_device_info), modifier = modifier, watermarkIcon = Icons.Outlined.PhoneAndroid) {
        if (info == null) {
            Text(
                text = stringResource(R.string.dashboard_device_info_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(R.string.dashboard_device_info_group_identity),
                style = MaterialTheme.typography.labelLarge,
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

        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(R.string.dashboard_device_info_group_usage),
                style = MaterialTheme.typography.labelLarge,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
            Text(
                text = stringResource(R.string.dashboard_device_usage_format, usedLabel, totalLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { clampedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.16f),
        )
    }
}

@Composable
private fun DeviceInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
