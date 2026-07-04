package com.aether.x.ui.tweak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thermostat
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.kernel.CpuCoreInfo
import com.aether.x.core.kernel.GpuInfo
import com.aether.x.core.kernel.ThermalZoneInfo
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import kotlinx.coroutines.delay

/**
 * Section "Kernel Manager": baca-tulis nilai kernel MENTAH (frekuensi
 * per-core CPU, governor per-core, frekuensi & governor GPU, suhu semua
 * zona termal live). BEDA dari section "Root" yang sudah ada di
 * TweakScreen (toggle bernama dengan mode terbatas) — lihat KDoc
 * [KernelManagerViewModel] untuk perbandingan lengkap.
 *
 * DIPASANG DI TweakScreen dengan gating IDENTIK dengan section "Root"
 * yang sudah ada: hanya tampil kalau `privilegeStatus.activeBackend ==
 * PrivilegeBackend.ROOT`, karena baca/tulis sysfs mentah di sini butuh
 * akses root sungguhan (Shizuku/adb shell tidak diberi izin tulis ke
 * /sys/devices/system/cpu maupun /sys/class/devfreq).
 *
 * Semua teks memakai `stringResource(R.string.kernel_manager_*)` —
 * resource-nya ada di strings.xml, section "Kernel Manager" tepat di
 * bawah `tweak_io_scheduler_boost_desc`.
 */
@Composable
fun KernelManagerSection(
    modifier: Modifier = Modifier,
    viewModel: KernelManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SectionCard(title = stringResource(R.string.kernel_manager_title), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.kernelVersion?.let {
                    stringResource(R.string.kernel_manager_version_format, it)
                } ?: stringResource(R.string.kernel_manager_version_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@SectionCard
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.kernel_manager_section_cpu),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.cpuCores.forEach { core ->
            CpuCoreRow(
                core = core,
                onFrequencyChange = { minKhz, maxKhz -> viewModel.applyCoreFrequency(core.coreIndex, minKhz, maxKhz) },
                onGovernorChange = { governor -> viewModel.applyCoreGovernor(core.coreIndex, governor) },
            )
        }

        state.gpu?.let { gpu ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.kernel_manager_section_gpu),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GpuRow(
                gpu = gpu,
                onFrequencyChange = { minKhz, maxKhz -> viewModel.applyGpuFrequency(minKhz, maxKhz) },
                onGovernorChange = { governor -> viewModel.applyGpuGovernor(governor) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.kernel_manager_section_thermal),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.thermalZones.isEmpty()) {
            Text(
                text = stringResource(R.string.kernel_manager_thermal_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.thermalZones.forEach { zone -> ThermalZoneRow(zone) }
        }

        // Pesan error ditampilkan INLINE di dalam section ini (bukan lewat
        // SnackbarHost milik TweakScreen) supaya KernelManagerSection tetap
        // independen dari TweakScreen/TweakViewModel — lihat KDoc
        // KernelManagerViewModel soal alasan kedua ViewModel ini terpisah.
        // state.message sendiri SUDAH berupa string jadi (di-resolve di
        // ViewModel lewat appString/getString), bukan resource ID, jadi
        // langsung ditampilkan tanpa stringResource() di sini.
        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(3000)
                viewModel.consumeMessage()
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CpuCoreRow(
    core: CpuCoreInfo,
    onFrequencyChange: (minKhz: Int?, maxKhz: Int?) -> Unit,
    onGovernorChange: (governor: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.kernel_manager_core_label, core.coreIndex),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
private fun GpuRow(
    gpu: GpuInfo,
    onFrequencyChange: (minKhz: Int?, maxKhz: Int?) -> Unit,
    onGovernorChange: (governor: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
private fun ThermalZoneRow(zone: ThermalZoneInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Thermostat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = zone.type.ifBlank { stringResource(R.string.kernel_manager_thermal_zone_format, zone.zoneIndex) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.kernel_manager_temp_celsius_format, zone.temperatureCelsius),
            style = MaterialTheme.typography.labelLarge,
            color = thermalColorFor(zone.temperatureCelsius),
        )
    }
}

/**
 * Slider ganda (min & max) berbasis INDEX ke [availableFrequenciesKhz],
 * BUKAN nilai KHz linear bebas — frekuensi kernel hanya boleh salah satu
 * dari step yang benar-benar didukung (mis. 300000, 576000, 748800, ...
 * tidak linear), jadi slider dengan `steps = size - 1` pada domain index
 * dijamin selalu berhenti tepat di step valid, lalu di-map balik ke KHz
 * saat ditampilkan/diterapkan.
 *
 * DUA SLIDER TERPISAH (bukan satu range slider) karena [TweakSlider] yang
 * sudah ada di app ini adalah slider nilai TUNGGAL — dipakai dua kali
 * (min lalu max) alih-alih menulis komponen range-slider baru, konsisten
 * dengan komponen yang sudah ada di codebase ini.
 */
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

/** Hijau di bawah 45°C, kuning 45-65°C, merah di atas 65°C — ambang kasar umum, bukan standar resmi vendor tertentu. */
@Composable
private fun thermalColorFor(celsius: Float) = when {
    celsius < 45f -> MaterialTheme.colorScheme.primary
    celsius < 65f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
