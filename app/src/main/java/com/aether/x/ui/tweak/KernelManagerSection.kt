package com.aether.x.ui.tweak

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

/**
 * Section "Kernel Manager": baca-tulis nilai kernel MENTAH (frekuensi
 * per-core CPU, governor per-core, frekuensi & governor GPU). BEDA dari
 * section "Root" yang sudah ada di TweakScreen (toggle bernama dengan mode
 * terbatas) — lihat KDoc [KernelManagerViewModel] untuk perbandingan
 * lengkap.
 *
 * REWORK TOTAL TAMPILAN (lihat perintah rework):
 * 1. Section suhu (live)/thermal zones DIHAPUS SEPENUHNYA dari sini — suhu
 *    perangkat sudah ditampilkan di tab Dashboard
 *    ([com.aether.x.ui.dashboard.DashboardMonitorRow]), jadi section ini
 *    dulu duplikat murni. [KernelManagerViewModel] juga sudah tidak lagi
 *    melakukan polling thermal sama sekali.
 * 2. CPU dan GPU sekarang masing-masing SectionCard TERPISAH (dulu satu
 *    Column panjang dengan HorizontalDivider sebagai pemisah semua
 *    kategori) — lebih mudah dipindai sekilas, konsisten secara visual
 *    dengan pengelompokan kategori yang sudah dipakai di GameProfileScreen.
 * 3. Kartu info kernel (versi + tombol refresh) sekarang jadi strip
 *    ringkas tersendiri di puncak, terpisah dari card CPU/GPU di
 *    bawahnya — bukan menyatu jadi header dalam satu SectionCard besar.
 * 4. Tiap core CPU ditampilkan dalam sub-card berlatar sedikit beda
 *    (`surfaceVariant` tipis) dengan badge nomor core, supaya batas
 *    antar-core jelas tanpa harus mengandalkan HorizontalDivider tipis
 *    yang mudah terlewat.
 *
 * DIPASANG DI TweakScreen dengan gating IDENTIK dengan section "Root" yang
 * sudah ada: hanya tampil kalau `privilegeStatus.activeBackend ==
 * PrivilegeBackend.ROOT`, karena baca/tulis sysfs mentah di sini butuh
 * akses root sungguhan (Shizuku/adb shell tidak diberi izin tulis ke
 * /sys/devices/system/cpu maupun /sys/class/devfreq).
 */
@Composable
fun KernelManagerSection(
    modifier: Modifier = Modifier,
    viewModel: KernelManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Strip info kernel + refresh — ringkas, bukan bagian dari card CPU/GPU.
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

        // Card CPU — terpisah dari GPU (dulu satu Column sama dengan divider tipis).
        SectionCard(title = stringResource(R.string.kernel_manager_section_cpu)) {
            state.cpuCores.forEachIndexed { index, core ->
                CpuCoreCard(
                    core = core,
                    onFrequencyChange = { minKhz, maxKhz -> viewModel.applyCoreFrequency(core.coreIndex, minKhz, maxKhz) },
                    onGovernorChange = { governor -> viewModel.applyCoreGovernor(core.coreIndex, governor) },
                )
                if (index != state.cpuCores.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        // Card GPU — hanya dirender kalau data GPU tersedia (chipset tanpa
        // devfreq GPU yang bisa dibaca akan membuat state.gpu tetap null).
        state.gpu?.let { gpu ->
            SectionCard(title = stringResource(R.string.kernel_manager_section_gpu)) {
                GpuRow(
                    gpu = gpu,
                    onFrequencyChange = { minKhz, maxKhz -> viewModel.applyGpuFrequency(minKhz, maxKhz) },
                    onGovernorChange = { governor -> viewModel.applyGpuGovernor(governor) },
                )
            }
        }

        // Pesan error ditampilkan INLINE (bukan lewat SnackbarHost milik
        // TweakScreen) supaya KernelManagerSection tetap independen dari
        // TweakScreen/TweakViewModel — lihat KDoc KernelManagerViewModel.
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

/**
 * Sub-card satu core CPU: badge index core (lingkaran kecil bernomor) +
 * frekuensi saat ini di kanan, lalu slider rentang frekuensi & dropdown
 * governor di bawahnya kalau tersedia. Latar sedikit beda dari card
 * induknya (`surfaceVariant` tipis) supaya batas antar-core terlihat jelas
 * tanpa mengandalkan garis divider tipis saja.
 */
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

/** Badge lingkaran kecil berisi nomor index core — pengganti ikon Memory generik supaya tiap core mudah dibedakan sekilas. */
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
