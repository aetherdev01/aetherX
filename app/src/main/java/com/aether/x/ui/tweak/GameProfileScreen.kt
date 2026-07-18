package com.aether.x.ui.tweak

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.GameMode
import com.aether.x.data.GameProfile
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakSwitch
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

/**
 * Layar Game Profile — KHUSUS ROOT (lihat perintah pengguna): daftar game
 * dari `assets/gamelist.txt` yang terpasang di perangkat ditampilkan sebagai
 * sidebar di sisi kiri, dengan panel detail tweak root per-game di sisi
 * kanan/bawah. Tweak yang disimpan di sini diterapkan/direset otomatis oleh
 * [com.aether.x.core.monitor.GameProfileMonitorService] saat game terkait
 * dibuka/ditutup dari recent apps — layar ini murni tempat mengatur data,
 * tidak menjalankan shell apa pun secara langsung.
 *
 * Kalau backend privilese aktif BUKAN Root, seluruh layar diganti dengan
 * pesan "Butuh Akses Root" — konsisten dengan pemisahan fitur root/non-root
 * yang sudah ada di section "Root" pada TweakScreen.
 */
@Composable
fun GameProfileScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GameProfileViewModel = viewModel(),
) {
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()

    if (privilegeStatus.activeBackend != PrivilegeBackend.ROOT) {
        GameProfileRootRequiredNotice(modifier = modifier, contentPadding = contentPadding)
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        if (state.selectedPackage == null) {
            GameProfileListPane(
                state = state,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSelectGame = viewModel::selectGame,
            )
        } else {
            GameProfileDetailPane(
                state = state,
                viewModel = viewModel,
                onBack = viewModel::clearSelection,
            )
        }
    }
}

@Composable
private fun GameProfileRootRequiredNotice(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCardAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = stringResource(R.string.game_profile_root_required_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.game_profile_root_required_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Panel kiri: search box + daftar game terpasang sebagai sidebar list. */
@Composable
private fun GameProfileListPane(
    state: GameProfileUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectGame: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Judul besar "Game Profile" TIDAK diulang di sini — sudah jelas
        // dari drawer navigasi mana sub-tab yang sedang aktif, dan
        // TweakHeader ("AetherX" + pill ID) tetap tampil permanen di
        // puncak layar terlepas dari sub-tab mana yang dipilih.
        Text(
            text = stringResource(R.string.game_profile_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.game_profile_scope_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.game_profile_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = StrokeSubtle,
            ),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            when {
                state.loadingGames -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                state.filteredGames.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.game_profile_empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.filteredGames, key = { it.packageName }) { entry ->
                            GameListRow(
                                entry = entry,
                                isActive = entry.packageName == state.activeGameProfilePackage,
                                configuredTweakCount = state.profiles[entry.packageName]?.let { profile ->
                                    countEnabledTweaks(profile)
                                } ?: 0,
                                onClick = { onSelectGame(entry.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameListRow(
    entry: InstalledGameEntry,
    isActive: Boolean,
    configuredTweakCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
            )
            // Nama paket ditampilkan kecil di bawah nama game — identitas
            // pasti (dua game bisa punya label sama tapi package name selalu
            // unik) sekaligus memenuhi permintaan rework: package name ikut
            // tampil di info aplikasi, bukan cuma di panel detail.
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )
            if (isActive) {
                StatusPill(
                    text = stringResource(R.string.game_profile_active_badge),
                    containerColor = AccentGreen.copy(alpha = 0.16f),
                    contentColor = AccentGreen,
                    dotColor = AccentGreen,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (configuredTweakCount > 0) {
                Text(
                    text = stringResource(R.string.game_profile_configured_badge, configuredTweakCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentBlue,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Panel kanan/detail (rework total — lihat perintah pengguna): dulu cuma
 * header ringkas + satu SectionCard datar berisi 6 toggle berurutan tanpa
 * pengelompokan. Sekarang:
 * 1. Kartu identitas game (icon besar, badge status/jumlah tweak aktif).
 * 2. Section "Info Aplikasi" — menampilkan nama paket (package name), yang
 *    sebelumnya tidak ditampilkan sama sekali di info aplikasi manapun pada
 *    layar ini.
 * 3. Toggle tweak dikelompokkan per kategori (CPU, GPU & Termal, Sistem)
 *    alih-alih satu daftar datar — lebih mudah dipindai sekilas.
 */
@Composable
private fun GameProfileDetailPane(
    state: GameProfileUiState,
    viewModel: GameProfileViewModel,
    onBack: () -> Unit,
) {
    val selectedEntry = state.installedGames.firstOrNull { it.packageName == state.selectedPackage }
    val profile = state.selectedProfile ?: return
    val activeTweakCount = countEnabledTweaks(profile)
    val totalTweakCount = 6

    // Dipakai HANYA untuk parameter transient onGameModeChange di bawah
    // (interstitial ad setelah preset Game Mode diterapkan) — lihat KDoc
    // GameProfileViewModel.onGameModeChange untuk alasan lengkapnya, pola
    // identik dengan TweakScreen.kt untuk onKillBackgroundAppsChange.
    // LocalContext di sini selalu berupa Activity karena GameProfileScreen
    // (layar ini) hanya pernah dirender dari MainActivity.
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary,
                )
            }
        }

        if (selectedEntry != null) {
            GameIdentityCard(
                entry = selectedEntry,
                isActive = selectedEntry.packageName == state.activeGameProfilePackage,
                activeTweakCount = activeTweakCount,
                totalTweakCount = totalTweakCount,
            )

            SectionCard(title = stringResource(R.string.game_profile_app_info_title), watermarkIcon = Icons.Outlined.Info) {
                GameInfoRow(
                    label = stringResource(R.string.game_profile_app_info_package),
                    value = selectedEntry.packageName,
                )
            }
        }

        // FITUR BARU (lihat perintah rework — "tambahkan fitur baru Game
        // Mode : Low, Mid, Boost"): preset yang mengisi ke-6 toggle di
        // bawahnya sekaligus, tapi tetap membiarkan tiap toggle diubah
        // manual sesudahnya — lihat KDoc GameMode/GameProfile.withGameMode.
        SectionCard(title = stringResource(R.string.game_profile_mode_label), watermarkIcon = Icons.Outlined.SportsEsports) {
            GameModeSelector(
                selected = profile.gameMode,
                onSelect = { mode -> viewModel.onGameModeChange(mode, activity) },
            )
        }

        SectionCard(title = stringResource(R.string.game_profile_category_cpu), watermarkIcon = Icons.Outlined.Memory) {
            TweakSwitch(
                label = stringResource(R.string.tweak_cpu_performance),
                description = stringResource(R.string.tweak_cpu_performance_desc),
                checked = profile.cpuPerformanceMode,
                onCheckedChange = viewModel::onCpuPerformanceModeChange,
                icon = Icons.Outlined.Speed,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_ram_priority),
                description = stringResource(R.string.tweak_ram_priority_desc),
                checked = profile.ramPriorityMode,
                onCheckedChange = viewModel::onRamPriorityModeChange,
                icon = Icons.Outlined.Memory,
            )
        }

        SectionCard(title = stringResource(R.string.game_profile_category_gpu), watermarkIcon = Icons.Outlined.DeveloperBoard) {
            TweakSwitch(
                label = stringResource(R.string.tweak_gpu_performance),
                description = stringResource(R.string.tweak_gpu_performance_desc),
                checked = profile.gpuPerformanceMode,
                onCheckedChange = viewModel::onGpuPerformanceModeChange,
                icon = Icons.Outlined.DeveloperBoard,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_thermal_throttle),
                description = stringResource(R.string.tweak_thermal_throttle_desc),
                checked = profile.thermalThrottleOverride,
                onCheckedChange = viewModel::onThermalThrottleOverrideChange,
                icon = Icons.Outlined.Thermostat,
            )
            // FITUR BARU — tweak ke-7 Game Profile: GPU Rendering Priority
            // (SurfaceFlinger) — lihat KDoc TweakRepository.applyGpuRenderingPriority.
            TweakSwitch(
                label = stringResource(R.string.tweak_gpu_rendering_priority),
                description = stringResource(R.string.tweak_gpu_rendering_priority_desc),
                checked = profile.gpuRenderingPriority,
                onCheckedChange = viewModel::onGpuRenderingPriorityChange,
                icon = Icons.Outlined.Bolt,
            )
        }

        SectionCard(title = stringResource(R.string.game_profile_category_system), watermarkIcon = Icons.Outlined.Build) {
            TweakSwitch(
                label = stringResource(R.string.tweak_io_scheduler_boost),
                description = stringResource(R.string.tweak_io_scheduler_boost_desc),
                checked = profile.ioSchedulerBoost,
                onCheckedChange = viewModel::onIoSchedulerBoostChange,
                icon = Icons.Outlined.SdStorage,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_vm_heap_boost),
                description = stringResource(R.string.tweak_vm_heap_boost_desc),
                checked = profile.vmHeapBoost,
                onCheckedChange = viewModel::onVmHeapBoostChange,
                icon = Icons.Outlined.Memory,
            )
        }

        OutlinedButton(
            onClick = viewModel::resetSelectedProfile,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, StrokeSubtle),
        ) {
            Text(
                text = stringResource(R.string.game_profile_reset_button),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Kartu identitas game di puncak panel detail: icon besar, nama game, dan
 * ringkasan status dalam satu pandangan — menggantikan header baris tunggal
 * yang sebelumnya cuma icon kecil + nama + badge kecil di sampingnya.
 */
@Composable
private fun GameIdentityCard(
    entry: InstalledGameEntry,
    isActive: Boolean,
    activeTweakCount: Int,
    totalTweakCount: Int,
) {
    SectionCard(title = null, watermarkIcon = Icons.Outlined.Apps) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = entry.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = if (activeTweakCount > 0) {
                        stringResource(R.string.game_profile_summary_active_count, activeTweakCount, totalTweakCount)
                    } else {
                        stringResource(R.string.game_profile_summary_inactive)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeTweakCount > 0) AccentBlue else TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (isActive) {
                    StatusPill(
                        text = stringResource(R.string.game_profile_active_badge),
                        containerColor = AccentGreen.copy(alpha = 0.16f),
                        contentColor = AccentGreen,
                        dotColor = AccentGreen,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Baris info sederhana label/value — dipakai section "Info Aplikasi" (nama paket). */
/**
 * Segmented chip 3-opsi (Low/Mid/Boost) untuk memilih [GameMode] preset —
 * memakai pola visual yang mirip [StatusPill] (chip dengan border accent
 * saat terpilih) yang sudah dipakai di layar ini, supaya konsisten secara
 * visual alih-alih memakai komponen baru yang berbeda gaya.
 */
@Composable
private fun GameModeSelector(
    selected: GameMode,
    onSelect: (GameMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameModeChip(
            label = stringResource(R.string.game_profile_mode_low),
            isSelected = selected == GameMode.LOW,
            onClick = { onSelect(GameMode.LOW) },
            modifier = Modifier.weight(1f),
        )
        GameModeChip(
            label = stringResource(R.string.game_profile_mode_mid),
            isSelected = selected == GameMode.MID,
            onClick = { onSelect(GameMode.MID) },
            modifier = Modifier.weight(1f),
        )
        GameModeChip(
            label = stringResource(R.string.game_profile_mode_boost),
            isSelected = selected == GameMode.BOOST,
            onClick = { onSelect(GameMode.BOOST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GameModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AccentBlue.copy(alpha = 0.18f) else SurfaceCardAlt)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentBlue else StrokeSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) AccentBlue else TextSecondary,
        )
    }
}

@Composable
private fun GameInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
    }
}

private fun countEnabledTweaks(profile: GameProfile): Int = listOf(
    profile.cpuPerformanceMode,
    profile.ramPriorityMode,
    profile.thermalThrottleOverride,
    profile.gpuPerformanceMode,
    profile.ioSchedulerBoost,
    profile.vmHeapBoost,
).count { it }
