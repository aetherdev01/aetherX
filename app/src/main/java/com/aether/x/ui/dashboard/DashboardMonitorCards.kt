package com.aether.x.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.ui.components.SectionCard
import kotlin.math.roundToInt

/**
 * Baris tiga monitor ringkas (CPU, GPU, Suhu) sebagai gauge lingkaran kecil
 * berdampingan — ringkasan cepat "sekilas lihat" di puncak tab Dashboard,
 * SEBELUM detail Kernel Manager (khusus Root) yang lebih rinci di bawahnya.
 * Nilai null (mis. GPU load tidak didukung chipset ini) ditampilkan sebagai
 * "-" alih-alih angka palsu — sama seperti perlakuan null di KernelManagerSection.
 */
@Composable
fun DashboardMonitorRow(
    cpuLoadPercent: Int?,
    gpuLoadPercent: Int?,
    temperatureCelsius: Float?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
            valueText = gpuLoadPercent?.let { "$it%" } ?: "-",
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
            label = stringResource(R.string.dashboard_device_ram),
            value = stringResource(
                R.string.dashboard_device_ram_format,
                (info.totalRamBytes - info.availableRamBytes).toGbLabel(),
                info.totalRamBytes.toGbLabel(),
            ),
        )
        DeviceInfoRow(
            icon = Icons.Outlined.SdStorage,
            label = stringResource(R.string.dashboard_device_storage),
            value = stringResource(
                R.string.dashboard_device_storage_format,
                (info.totalStorageBytes - info.availableStorageBytes).toGbLabel(),
                info.totalStorageBytes.toGbLabel(),
            ),
        )
        DeviceInfoRow(
            icon = Icons.Outlined.Memory,
            label = stringResource(R.string.dashboard_device_cpu_abi),
            value = info.cpuAbi.ifBlank { "-" },
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
