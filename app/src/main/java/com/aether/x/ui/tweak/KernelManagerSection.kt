package com.aether.x.ui.tweak

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.kernel.CpuCoreInfo
import com.aether.x.core.kernel.GpuInfo
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import kotlinx.coroutines.delay

@Composable
fun KernelManagerSection(
    modifier: Modifier = Modifier,
    viewModel: KernelManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.kernel_manager_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.kernelVersion?.let {
                        stringResource(R.string.kernel_manager_version_format, it)
                    } ?: stringResource(R.string.kernel_manager_version_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.kernel_manager_refresh_cd),
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

        SectionCard(title = stringResource(R.string.kernel_manager_section_cpu), watermarkIcon = Icons.Outlined.Memory) {
            state.cpuCores.forEachIndexed { index, core ->
                CpuCoreCard(
                    core = core,
                    onFrequencyChange = { minKhz, maxKhz -> viewModel.applyCoreFrequency(core.coreIndex, minKhz, maxKhz, activity) },
                    onGovernorChange = { governor -> viewModel.applyCoreGovernor(core.coreIndex, governor, activity) },
                )
                if (index != state.cpuCores.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        state.gpu?.let { gpu ->
            SectionCard(title = stringResource(R.string.kernel_manager_section_gpu), watermarkIcon = Icons.Outlined.DeveloperBoard) {
                GpuRow(
                    gpu = gpu,
                    onFrequencyChange = { minKhz, maxKhz -> viewModel.applyGpuFrequency(minKhz, maxKhz, activity) },
                    onGovernorChange = { governor -> viewModel.applyGpuGovernor(governor, activity) },
                )
            }
        }

        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(3000)
                viewModel.consumeMessage()
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CpuCoreCard(
    core: CpuCoreInfo,
    onFrequencyChange: (minKhz: Int?, maxKhz: Int?) -> Unit,
    onGovernorChange: (governor: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoreIndexBadge(index = core.coreIndex)
                Text(
                    text = stringResource(R.string.kernel_manager_core_label, core.coreIndex),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text = core.currentFreqKhz?.let { freqMhzLabel(it) } ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (core.isUnavailable) {
            Text(
                text = stringResource(R.string.kernel_manager_core_unavailable, core.coreIndex),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        if (core.availableFrequenciesKhz.size > 1) {
            FrequencyRangeSlider(
                availableFrequenciesKhz = core.availableFrequenciesKhz,
                currentMinKhz = core.minFreqKhz,
                currentMaxKhz = core.maxFreqKhz,
                onChangeFinished = onFrequencyChange,
            )
        }

        if (core.availableGovernors.isNotEmpty()) {
            val selected = core.currentGovernor ?: core.availableGovernors.first()
            TweakDropdown(
                label = stringResource(R.string.kernel_manager_governor_label),
                description = stringResource(R.string.kernel_manager_governor_desc_format, core.coreIndex),
                options = core.availableGovernors,
                selected = selected,
                optionLabel = { it },
                onOptionSelected = onGovernorChange,
            )
        }
    }
}

@Composable
private fun CoreIndexBadge(index: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GpuRow(
    gpu: GpuInfo,
    onFrequencyChange: (minKhz: Int?, maxKhz: Int?) -> Unit,
    onGovernorChange: (governor: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (gpu.isUnavailable) {
            Text(
                text = stringResource(R.string.kernel_manager_gpu_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeveloperBoard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = gpu.currentFreqKhz?.let { freqMhzLabel(it) } ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (gpu.availableFrequenciesKhz.size > 1) {
            FrequencyRangeSlider(
                availableFrequenciesKhz = gpu.availableFrequenciesKhz,
                currentMinKhz = gpu.minFreqKhz,
                currentMaxKhz = gpu.maxFreqKhz,
                onChangeFinished = onFrequencyChange,
            )
        }

        if (gpu.availableGovernors.isNotEmpty()) {
            val selected = gpu.currentGovernor ?: gpu.availableGovernors.first()
            TweakDropdown(
                label = stringResource(R.string.kernel_manager_gpu_governor_label),
                description = stringResource(R.string.kernel_manager_gpu_governor_desc),
                options = gpu.availableGovernors,
                selected = selected,
                optionLabel = { it },
                onOptionSelected = onGovernorChange,
            )
        }
    }
}

@Composable
private fun FrequencyRangeSlider(
    availableFrequenciesKhz: List<Int>,
    currentMinKhz: Int?,
    currentMaxKhz: Int?,
    onChangeFinished: (minKhz: Int?, maxKhz: Int?) -> Unit,
) {
    val lastIndex = availableFrequenciesKhz.lastIndex
    val minIndexDefault = currentMinKhz
        ?.let { v -> availableFrequenciesKhz.indexOfFirst { it >= v }.takeIf { it >= 0 } }
        ?: 0
    val maxIndexDefault = currentMaxKhz
        ?.let { v -> availableFrequenciesKhz.indexOfLast { it <= v }.takeIf { it >= 0 } }
        ?: lastIndex

    var minIndex by remember(availableFrequenciesKhz, currentMinKhz) { mutableFloatStateOf(minIndexDefault.toFloat()) }
    var maxIndex by remember(availableFrequenciesKhz, currentMaxKhz) { mutableFloatStateOf(maxIndexDefault.toFloat()) }

    TweakSlider(
        label = stringResource(R.string.kernel_manager_freq_min_label),
        description = stringResource(R.string.kernel_manager_freq_min_desc),
        valueText = freqMhzLabel(availableFrequenciesKhz[minIndex.toInt()]),
        value = minIndex,
        range = 0f..lastIndex.toFloat(),
        steps = (lastIndex - 1).coerceAtLeast(0),
        onValueChange = { minIndex = it.coerceAtMost(maxIndex) },
        onValueChangeFinished = {
            onChangeFinished(availableFrequenciesKhz[minIndex.toInt()], null)
        },
    )
    TweakSlider(
        label = stringResource(R.string.kernel_manager_freq_max_label),
        description = stringResource(R.string.kernel_manager_freq_max_desc),
        valueText = freqMhzLabel(availableFrequenciesKhz[maxIndex.toInt()]),
        value = maxIndex,
        range = 0f..lastIndex.toFloat(),
        steps = (lastIndex - 1).coerceAtLeast(0),
        onValueChange = { maxIndex = it.coerceAtLeast(minIndex) },
        onValueChangeFinished = {
            onChangeFinished(null, availableFrequenciesKhz[maxIndex.toInt()])
        },
    )
}

@Composable
private fun freqMhzLabel(khz: Int): String =
    stringResource(R.string.kernel_manager_freq_mhz_format, khz / 1000)
