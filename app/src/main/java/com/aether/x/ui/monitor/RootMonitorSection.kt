package com.aether.x.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.Spacing

/**
 * RootMonitorSection — grafik CPU per-core + GPU real-time. HANYA
 * dipanggil dari TweakScreen saat `TweakSubTab.ROOT_MONITOR` dan backend
 * aktif ROOT (gating sama seperti Kernel Manager/Build.prop Editor —
 * lihat TweakScreen.kt, blok LaunchedEffect(privilegeStatus.activeBackend)
 * yang otomatis melempar kembali ke Dashboard kalau backend bukan ROOT).
 *
 * Polling dimulai saat composable ini masuk komposisi ([DisposableEffect]
 * memanggil [RootMonitorViewModel.onResume]) dan berhenti saat keluar
 * (mis. pengguna pindah sub-tab lain di drawer) — lihat KDoc
 * RootMonitorViewModel untuk alasan lengkap.
 */
@Composable
fun RootMonitorSection(
    modifier: Modifier = Modifier,
    viewModel: RootMonitorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onResume()
        onDispose { viewModel.onPause() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            text = stringResource(R.string.root_monitor_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.nativeAvailable) {
            SectionCard(title = null, watermarkIcon = Icons.Outlined.Memory) {
                Text(
                    text = stringResource(R.string.root_monitor_native_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        SectionCard(
            title = stringResource(R.string.root_monitor_section_cpu),
            watermarkIcon = Icons.Outlined.Memory,
        ) {
            if (!state.hasSamples) {
                Text(
                    text = stringResource(R.string.root_monitor_waiting_samples),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val latestAggregate = state.cpuAggregateHistory.lastOrNull() ?: 0f
                MonitorValueRow(
                    label = stringResource(R.string.root_monitor_cpu_aggregate),
                    valueText = "${latestAggregate.toInt()}%",
                    valueColor = AccentBlue,
                )
                RealtimeLineChart(
                    values = state.cpuAggregateHistory,
                    maxValue = 100f,
                    lineColor = AccentBlue,
                )

                if (state.cpuPerCoreLatest.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.cpuPerCoreLatest.forEachIndexed { index, percent ->
                            CoreLoadBar(
                                label = stringResource(R.string.root_monitor_cpu_core_format, index),
                                percent = percent,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.root_monitor_section_gpu),
            watermarkIcon = Icons.Outlined.DeveloperBoard,
        ) {
            if (state.gpuLoadHistory.isEmpty()) {
                Text(
                    text = stringResource(R.string.root_monitor_gpu_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val latestGpu = state.gpuLoadHistory.lastOrNull() ?: 0f
                MonitorValueRow(
                    label = stringResource(R.string.root_monitor_gpu_load),
                    valueText = "${latestGpu.toInt()}%",
                    valueColor = AccentAmber,
                )
                RealtimeLineChart(
                    values = state.gpuLoadHistory,
                    maxValue = 100f,
                    lineColor = AccentAmber,
                )
                Text(
                    text = state.gpuFreqMhz?.let {
                        stringResource(R.string.root_monitor_gpu_freq_format, it)
                    } ?: stringResource(R.string.root_monitor_gpu_freq_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonitorValueRow(label: String, valueText: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
private fun CoreLoadBar(label: String, percent: Float) {
    val clamped = percent.coerceIn(0f, 100f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = AccentBlue,
            trackColor = AccentBlue.copy(alpha = 0.16f),
        )
        Text(
            text = "${clamped.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(40.dp),
        )
    }
}
