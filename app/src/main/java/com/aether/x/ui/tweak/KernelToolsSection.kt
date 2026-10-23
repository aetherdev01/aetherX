package com.aether.x.ui.tweak

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt
import com.aether.x.core.locale.str
import com.aether.x.R

/** v3.6 — Advanced -> Kernel Tools (khusus Root). Lihat [KernelToolsViewModel]. */
@Composable
fun KernelToolsSection(
    modifier: Modifier = Modifier,
    viewModel: KernelToolsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = str(R.string.kernel_tools_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = str(R.string.kernel_tools_i_o_jaringan_dan_memori),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = str(R.string.kernel_manager_refresh_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (state.loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        if (!state.rootAvailable) {
            Text(
                text = str(R.string.kernel_tools_akses_root_tidak_tersedia),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        SectionCard(title = str(R.string.kernel_tools_io_scheduler_title), watermarkIcon = Icons.Outlined.SdStorage) {
            if (state.blockDevices.isEmpty()) {
                EmptyHint(str(R.string.kernel_tools_perangkat_blok_tidak_terdeteksi))
            }
            state.blockDevices.forEachIndexed { index, dev ->
                val current = dev.currentScheduler
                if (current != null && dev.availableSchedulers.isNotEmpty()) {
                    TweakDropdown(
                        label = dev.name,
                        description = str(R.string.kernel_tools_scheduler_antrean_i_o),
                        options = dev.availableSchedulers,
                        selected = current,
                        optionLabel = { it },
                        onOptionSelected = { viewModel.setScheduler(dev.name, it, activity) },
                        icon = Icons.Outlined.SdStorage,
                    )
                }
                dev.readAheadKb?.let { ra ->
                    val options = (listOf(128, 256, 512, 1024, 2048, 4096) + ra).distinct().sorted()
                    TweakDropdown(
                        label = str(R.string.kernel_tools_read_ahead_label, dev.name),
                        description = str(R.string.kernel_tools_prabaca_data_kb),
                        options = options,
                        selected = ra,
                        optionLabel = { "$it KB" },
                        onOptionSelected = { viewModel.setReadAhead(dev.name, it, activity) },
                    )
                }
                if (index != state.blockDevices.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        SectionCard(title = str(R.string.kernel_tools_jaringan_tcp), watermarkIcon = Icons.Outlined.NetworkCheck) {
            val current = state.tcpCurrent
            if (current != null && state.tcpAvailable.isNotEmpty()) {
                TweakDropdown(
                    label = str(R.string.kernel_tools_tcp_congestion_control_label),
                    description = str(R.string.kernel_tools_algoritma_pengatur_kemacetan_jaringan),
                    options = state.tcpAvailable,
                    selected = current,
                    optionLabel = { it },
                    onOptionSelected = { viewModel.setTcp(it, activity) },
                    icon = Icons.Outlined.NetworkCheck,
                )
            } else {
                EmptyHint(str(R.string.kernel_tools_kernel_ini_tidak_menyediakan_pilihan))
            }
        }

        SectionCard(title = str(R.string.kernel_tools_memori_virtual), watermarkIcon = Icons.Outlined.Memory) {
            state.swappiness?.let { value ->
                var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
                TweakSlider(
                    label = str(R.string.kernel_tools_swappiness_label),
                    description = str(R.string.kernel_tools_seberapa_agresif_kernel_memindahkan_memo),
                    valueText = slider.roundToInt().toString(),
                    value = slider,
                    range = 0f..200f,
                    steps = 0,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { viewModel.setSwappiness(slider.roundToInt(), activity) },
                )
            }
            state.vfsCachePressure?.let { value ->
                var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
                TweakSlider(
                    label = str(R.string.kernel_tools_vfs_cache_pressure_label),
                    description = str(R.string.kernel_tools_seberapa_cepat_cache_direktori_inode),
                    valueText = slider.roundToInt().toString(),
                    value = slider,
                    range = 1f..500f,
                    steps = 0,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { viewModel.setVfsCachePressure(slider.roundToInt(), activity) },
                )
            }
            if (state.swappiness == null && state.vfsCachePressure == null) {
                EmptyHint(str(R.string.kernel_tools_parameter_memori_virtual_tidak_terbaca))
            }
        }

        SectionCard(title = "ZRAM", watermarkIcon = Icons.Outlined.Tune) {
            val size = state.zramSizeBytes
            if (size == null) {
                EmptyHint(str(R.string.kernel_tools_zram_tidak_aktif_di_perangkat))
            } else {
                InfoLine(str(R.string.crosshair_size_label), String.format(Locale.US, "%.2f GB", size / 1_073_741_824.0))
                state.zramAlgorithm?.let { InfoLine(str(R.string.kernel_tools_algoritma), it) }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
