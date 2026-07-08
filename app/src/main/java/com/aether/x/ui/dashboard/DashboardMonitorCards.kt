package com.aether.x.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.DashboardAccentOrange
import com.aether.x.ui.theme.DashboardHeroEnd
import com.aether.x.ui.theme.DashboardHeroStart
import com.aether.x.ui.theme.DashboardPillBrown
import kotlin.math.roundToInt

/**
 * REWORK TOTAL (lihat perintah rework terbaru — "Samakan UI Dashboard
 * Seperti Foto ke 1 dari Gaya, Dll"): kartu hero besar di puncak tab
 * Dashboard, meniru gaya referensi persis — gradient coklat gelap ke
 * hampir-hitam, judul besar bold, versi kecil di bawahnya, dan ikon
 * dekoratif AetherX transparan besar di sisi kanan (bukan lagi kartu datar
 * kecil dengan ikon lingkaran seperti versi sebelumnya).
 *
 * Status Root/Shizuku/No Root SEKARANG jadi pill solid warna
 * [DashboardPillBrown] di kartu KEDUA (lihat [DashboardStatusRow]),
 * meniru pill "Game Space" di referensi — bukan lagi badge kecil di ujung
 * kanan kartu hero.
 */
@Composable
fun AetherXInfoCard(
    activeBackend: PrivilegeBackend,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DashboardHeroStart, DashboardHeroEnd),
                ),
            ),
    ) {
        // Ikon dekoratif besar transparan di sisi kanan, meniru siluet
        // logo besar di referensi — sengaja diperbesar melebihi tinggi
        // kartu dan di-offset supaya sebagian terpotong tepi (efek "logo
        // besar mengintip"), sama seperti referensi.
        Icon(
            painter = painterResource(id = com.aether.x.R.drawable.ic_aetherx_mark),
            contentDescription = null,
            tint = DashboardAccentOrange.copy(alpha = 0.22f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 16.dp)
                .size(140.dp),
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_hero_kicker),
                style = MaterialTheme.typography.labelLarge,
                color = DashboardAccentOrange.copy(alpha = 0.8f),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.dashboard_app_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * FITUR BARU: baris kartu kedua di bawah hero card — meniru kartu metric
 * ("Batas Koleksi | Energi" + pill "Game Space") di referensi. Di sini
 * berisi status mode akses aktif (Root/Shizuku/No Root) sebagai pill solid
 * di kanan, dengan label deskriptif di kiri.
 */
@Composable
fun DashboardStatusRow(
    activeBackend: PrivilegeBackend,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dashboard_privilege_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrivilegeBackendPill(activeBackend)
    }
}

/**
 * Pill status solid — meniru pill "Game Space" (warna solid coklat-oranye
 * + ikon di kanan) di referensi, MENGGANTIKAN badge transparan kecil
 * ([PrivilegeBackendBadge] versi lama, sekarang dihapus). Warna pill tetap
 * berbeda per status supaya tetap mudah dibedakan sekilas: coklat-oranye
 * solid untuk Root (privilese penuh), abu gelap untuk Shizuku/No Root
 * (privilese terbatas/tidak ada).
 */
@Composable
private fun PrivilegeBackendPill(backend: PrivilegeBackend) {
    val (label, bgColor, icon) = when (backend) {
        PrivilegeBackend.ROOT -> Triple(stringResource(R.string.dashboard_privilege_root), DashboardPillBrown, Icons.Outlined.Shield)
        PrivilegeBackend.SHIZUKU -> Triple(stringResource(R.string.dashboard_privilege_shizuku), MaterialTheme.colorScheme.surfaceVariant, Icons.Outlined.Shield)
        PrivilegeBackend.NONE -> Triple(stringResource(R.string.dashboard_privilege_none), MaterialTheme.colorScheme.surfaceVariant, Icons.Outlined.Shield)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
    }
}

/**
 * Baris tiga monitor ringkas (CPU, GPU, Suhu) sebagai gauge lingkaran kecil
 * berdampingan — ringkasan cepat "sekilas lihat" di puncak tab Dashboard,
 * SEBELUM detail Kernel Manager (khusus Root) yang lebih rinci di bawahnya.
 * Nilai null pada CPU/Suhu ditampilkan sebagai "-" (belum sempat terbaca).
 * Untuk GPU, [gpuUnsupported] membedakan dua kondisi null yang PENYEBABNYA
 * beda (lihat KDoc [DashboardViewModel]): "-" polos kalau belum sempat
 * terbaca (akan terisi di polling berikutnya), vs label
 * [R.string.dashboard_gpu_unsupported_hint] kalau chipset perangkat ini memang
 * tidak punya node persentase GPU (Mali/PowerVR umumnya) — supaya
 * pengguna tidak mengira ini bug yang harus dilaporkan berulang-ulang.
 */
@Composable
fun DashboardMonitorRow(
    cpuLoadPercent: Int?,
    gpuLoadPercent: Int?,
    temperatureCelsius: Float?,
    gpuUnsupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonitorGaugeCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.dashboard_monitor_cpu),
                valueText = cpuLoadPercent?.let { "$it%" } ?: "-",
                progress = (cpuLoadPercent ?: 0) / 100f,
                icon = Icons.Outlined.Memory,
                gaugeColor = MaterialTheme.colorScheme.primary,
            )
            MonitorGaugeCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.dashboard_monitor_gpu),
                valueText = when {
                    gpuLoadPercent != null -> "$gpuLoadPercent%"
                    gpuUnsupported -> stringResource(R.string.dashboard_gpu_unsupported_short)
                    else -> "-"
                },
                progress = (gpuLoadPercent ?: 0) / 100f,
                icon = Icons.Outlined.DeveloperBoard,
                gaugeColor = MaterialTheme.colorScheme.tertiary,
            )
            MonitorGaugeCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.dashboard_monitor_temp),
                valueText = temperatureCelsius?.let { "${it.roundToInt()}°" } ?: "-",
                progress = ((temperatureCelsius ?: 0f) / 100f).coerceIn(0f, 1f),
                icon = Icons.Outlined.Thermostat,
                gaugeColor = thermalGaugeColor(temperatureCelsius),
            )
        }
        if (gpuUnsupported) {
            Text(
                text = stringResource(R.string.dashboard_gpu_unsupported_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun thermalGaugeColor(celsius: Float?): Color = when {
    celsius == null -> MaterialTheme.colorScheme.onSurfaceVariant
    celsius < 45f -> MaterialTheme.colorScheme.primary
    celsius < 65f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun MonitorGaugeCard(
    label: String,
    valueText: String,
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gaugeColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val strokeWidth = 6.dp.toPx()
                drawArc(
                    color = gaugeColor.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                drawArc(
                    color = gaugeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = gaugeColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Section "Info Device": model, chipset/board, versi Android, RAM, dan
 * penyimpanan — SEMUA tersedia tanpa Shizuku/Root (lihat KDoc
 * [com.aether.x.core.device.DeviceInfoProvider]), jadi section ini selalu
 * tampil terlepas dari backend akses yang aktif.
 *
 * REWORK TOTAL TAMPILAN (lihat perintah rework — "rework tampilan Info
 * Device"): sebelumnya SEMUA baris (identitas + penggunaan resource)
 * ditumpuk datar dalam satu Column tanpa pemisah visual. Sekarang dipecah
 * jadi 2 sub-grup dengan label kecil di masing-masing ("Identitas
 * Perangkat" untuk model/chipset/Android/CPU ABI, "Penggunaan Resource"
 * untuk RAM/storage) dipisahkan HorizontalDivider — lebih mudah dipindai
 * sekilas, dan progress bar RAM/storage jadi lebih menonjol sebagai
 * kelompok tersendiri alih-alih menyatu dengan baris teks identitas.
 */
@Composable
fun DeviceInfoSection(info: DeviceInfoSnapshot?, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.dashboard_section_device_info), modifier = modifier) {
        if (info == null) {
            Text(
                text = stringResource(R.string.dashboard_device_info_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Text(
            text = stringResource(R.string.dashboard_device_info_group_identity),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
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

        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp),
        )

        Text(
            text = stringResource(R.string.dashboard_device_info_group_usage),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // RAM & penyimpanan ditampilkan sebagai progress bar visual (bukan
        // cuma teks "4.2 GB / 8.0 GB") — sekilas pandang langsung terlihat
        // seberapa penuh, tanpa perlu menghitung sendiri dari dua angka.
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

/**
 * Baris progress bar penggunaan (RAM/Storage) — fitur baru Dashboard:
 * label + angka "terpakai / total" di baris atas, progress bar warna
 * berjenjang (hijau→kuning→merah mengikuti seberapa penuh) di bawahnya.
 */
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
                    modifier = Modifier.padding(start = 12.dp),
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
                .padding(top = 8.dp)
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
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
