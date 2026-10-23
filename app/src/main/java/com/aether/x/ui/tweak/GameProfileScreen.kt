package com.aether.x.ui.tweak

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background

import android.app.Activity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.GameMode
import com.aether.x.data.GameProfile
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakSwitch
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import com.aether.x.core.locale.str

@Composable
fun GameProfileScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GameProfileViewModel = viewModel(),
) {
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()

    if (!privilegeStatus.rootGranted) {
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
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aetherCardSurface(shape = MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = stringResource(R.string.game_profile_root_required_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.game_profile_root_required_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                text = str(R.string.game_profile_akses_root_dibutuhkan),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIcon = Icons.Outlined.Lock,
            )
        }
    }
}

@Composable
private fun GameProfileListPane(
    state: GameProfileUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectGame: (String) -> Unit,
) {
    val configuredProfiles = state.profiles.values.count { countEnabledTweaks(it) > 0 }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameProfileHeaderCard(isActive = state.activeGameProfilePackage != null)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileMetric(
                value = state.installedGames.size.toString(),
                label = str(R.string.game_profile_game_terdeteksi),
                modifier = Modifier.weight(1f),
            )
            ProfileMetric(
                value = configuredProfiles.toString(),
                label = str(R.string.game_profile_profil_disetel),
                modifier = Modifier.weight(1f),
            )
            ProfileMetric(
                value = if (state.activeGameProfilePackage != null) "1" else "0",
                label = str(R.string.game_profile_sedang_aktif),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aetherCardSurface(shape = MaterialTheme.shapes.large)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 1.dp).size(18.dp),
            )
            Text(
                text = stringResource(R.string.game_profile_scope_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = str(R.string.game_profile_pilih_game),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = str(R.string.game_profile_tersedia, state.filteredGames.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.game_profile_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = StrokeSubtle,
            ),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            when {
                state.loadingGames -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                state.filteredGames.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = stringResource(R.string.game_profile_empty_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun GameProfileHeaderCard(isActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = MaterialTheme.shapes.extraLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = str(R.string.game_profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = str(R.string.game_profile_personalisasi_preset_dan_kontrol_perform),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusPill(
            text = if (isActive) str(R.string.game_profile_profil_game_aktif) else str(R.string.game_profile_menunggu_game),
            containerColor = if (isActive) AccentGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isActive) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIcon = if (isActive) Icons.Outlined.Bolt else Icons.Outlined.Info,
            leadingIconTint = if (isActive) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .aetherCardSurface(shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small),
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
            } else {
                Text(
                    text = if (configuredTweakCount > 0) {
                        stringResource(R.string.game_profile_configured_badge, configuredTweakCount)
                    } else {
                        str(R.string.game_profile_profil_default_belum_disesuaikan)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (configuredTweakCount > 0) AccentBlue else TextMuted,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ProfileMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .aetherCardSurface(shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun GameProfileDetailPane(
    state: GameProfileUiState,
    viewModel: GameProfileViewModel,
    onBack: () -> Unit,
) {
    val selectedEntry = state.installedGames.firstOrNull { it.packageName == state.selectedPackage }
    val profile = state.selectedProfile ?: return
    val activeTweakCount = countEnabledTweaks(profile)
    val totalTweakCount = 7

    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = str(R.string.game_profile_kembali_ke_daftar_game),
                    tint = TextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = str(R.string.game_profile_konfigurasi_game),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = str(R.string.game_profile_atur_mode_dan_kontrol_yang),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
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
                onCheckedChange = { checked -> viewModel.onCpuPerformanceModeChange(checked, activity) },
                icon = Icons.Outlined.Speed,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_ram_priority),
                description = stringResource(R.string.tweak_ram_priority_desc),
                checked = profile.ramPriorityMode,
                onCheckedChange = { checked -> viewModel.onRamPriorityModeChange(checked, activity) },
                icon = Icons.Outlined.Memory,
            )
        }

        SectionCard(title = stringResource(R.string.game_profile_category_gpu), watermarkIcon = Icons.Outlined.DeveloperBoard) {
            TweakSwitch(
                label = stringResource(R.string.tweak_gpu_performance),
                description = stringResource(R.string.tweak_gpu_performance_desc),
                checked = profile.gpuPerformanceMode,
                onCheckedChange = { checked -> viewModel.onGpuPerformanceModeChange(checked, activity) },
                icon = Icons.Outlined.DeveloperBoard,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_thermal_throttle),
                description = stringResource(R.string.tweak_thermal_throttle_desc),
                checked = profile.thermalThrottleOverride,
                onCheckedChange = { checked -> viewModel.onThermalThrottleOverrideChange(checked, activity) },
                icon = Icons.Outlined.Thermostat,
            )

            TweakSwitch(
                label = stringResource(R.string.tweak_gpu_rendering_priority),
                description = stringResource(R.string.tweak_gpu_rendering_priority_desc),
                checked = profile.gpuRenderingPriority,
                onCheckedChange = { checked -> viewModel.onGpuRenderingPriorityChange(checked, activity) },
                icon = Icons.Outlined.Bolt,
            )
        }

        SectionCard(title = stringResource(R.string.game_profile_category_system), watermarkIcon = Icons.Outlined.Build) {
            TweakSwitch(
                label = stringResource(R.string.tweak_io_scheduler_boost),
                description = stringResource(R.string.tweak_io_scheduler_boost_desc),
                checked = profile.ioSchedulerBoost,
                onCheckedChange = { checked -> viewModel.onIoSchedulerBoostChange(checked, activity) },
                icon = Icons.Outlined.SdStorage,
            )
            TweakSwitch(
                label = stringResource(R.string.tweak_vm_heap_boost),
                description = stringResource(R.string.tweak_vm_heap_boost_desc),
                checked = profile.vmHeapBoost,
                onCheckedChange = { checked -> viewModel.onVmHeapBoostChange(checked, activity) },
                icon = Icons.Outlined.Memory,
            )
        }

        OutlinedButton(
            onClick = { viewModel.resetSelectedProfile(activity) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
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
                    .clip(MaterialTheme.shapes.medium),
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
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) AccentBlue.copy(alpha = 0.18f) else SurfaceCardAlt)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentBlue else StrokeSubtle,
                shape = MaterialTheme.shapes.large,
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
    profile.gpuRenderingPriority,
    profile.ioSchedulerBoost,
    profile.vmHeapBoost,
).count { it }
