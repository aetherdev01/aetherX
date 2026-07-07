package com.aether.x.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.ui.components.SectionCard
import kotlin.math.roundToInt

/**
 * FITUR BARU (lihat perintah rework — "dibawah header itu dibikin card
 * berisi info dari aplikasi aetherX lengkap, status mode Root/No Root"):
 * kartu paling atas di tab Dashboard, di bawah header, SEBELUM
 * [DashboardMonitorRow]. Menampilkan identitas aplikasi (nama + versi dari
 * [BuildConfig.VERSION_NAME]) dan status mode akses aktif saat ini
 * (Root/Shizuku/No Root) sebagai badge berwarna berbeda per status —
 * hijau untuk Root (privilese penuh), biru untuk Shizuku (privilese
 * terbatas), abu-abu untuk No Root (tanpa privilese, sebagian besar tweak
 * tidak akan tersedia).
 */
@Composable
fun AetherXInfoCard(
    activeBackend: PrivilegeBackend,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.dashboard_app_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrivilegeBackendBadge(activeBackend)
    }
}

@Composable
private fun PrivilegeBackendBadge(backend: PrivilegeBackend) {
    val (label, color) = when (backend) {
        PrivilegeBackend.ROOT -> stringResource(R.string.dashboard_privilege_root) to MaterialTheme.colorScheme.primary
        PrivilegeBackend.SHIZUKU -> stringResource(R.string.dashboard_privilege_shizuku) to MaterialTheme.colorScheme.tertiary
        PrivilegeBackend.NONE -> stringResource(R.string.dashboard_privilege_none) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
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
