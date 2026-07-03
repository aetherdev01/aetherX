package com.aether.x.ui.tweak

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.components.TweakSwitch


@Composable
fun TweakScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TweakViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sub-tab "Game Profile" (lihat GameProfileScreen) hanya relevan untuk
    // backend Root — kalau pengguna beralih ke Shizuku sementara sub-tab ini
    // sedang dibuka, otomatis kembali ke sub-tab Tweak biasa supaya tidak
    // menampilkan fitur khusus-root ke pengguna non-root.
    var selectedSubTab by remember { mutableStateOf(TweakSubTab.TWEAK) }
    LaunchedEffect(privilegeStatus.activeBackend) {
        if (privilegeStatus.activeBackend != PrivilegeBackend.ROOT) {
            selectedSubTab = TweakSubTab.TWEAK
        }
    }

    // Deteksi ulang game terpasang setiap kali layar Tweak kembali aktif
    // (mis. setelah pengguna baru saja memasang Free Fire dari luar app).
    // Tombol buka game sendiri sekarang tampil sebagai FAB di MainScreen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDetectedGames()
                // Coba lagi alokasi ID pengguna kalau sebelumnya gagal (mis.
                // dibuka pertama kali sebelum jaringan siap) — lihat catatan
                // di TweakViewModel.retryResolveUserIdIfMissing().
                viewModel.retryResolveUserIdIfMissing()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (selectedSubTab == TweakSubTab.GAME_PROFILE) {
        // GameProfileScreen mengurus tata letaknya sendiri (sidebar list +
        // panel detail), termasuk header "Game Profile" — jadi ditampilkan
        // sebagai pengganti penuh Column tweak biasa di bawah, bukan
        // disisipkan di dalamnya.
        Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
            TweakSubTabSwitcher(
                selected = selectedSubTab,
                onSelect = { selectedSubTab = it },
                showGameProfileTab = privilegeStatus.activeBackend == PrivilegeBackend.ROOT,
            )
            GameProfileScreen(modifier = Modifier.weight(1f))
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TweakHeader(userId = state.userId, onRetryUserId = viewModel::retryResolveUserIdIfMissing)

            // Sub-tab switcher Tweak/Game Profile — HANYA ditampilkan sama
            // sekali kalau backend aktif adalah Root, karena Game Profile
            // sepenuhnya fitur khusus root (lihat perintah pengguna). Untuk
            // backend Shizuku/belum ada akses, tab ini tidak ada gunanya
            // jadi tidak perlu memakan tempat di layar.
            if (privilegeStatus.activeBackend == PrivilegeBackend.ROOT) {
                TweakSubTabSwitcher(
                    selected = selectedSubTab,
                    onSelect = { selectedSubTab = it },
                    showGameProfileTab = true,
                )
            }

            // Section Input Driver (pointer speed & touch boost) khusus untuk
            // backend NON-ROOT (Shizuku) — disembunyikan saat backend aktif
            // adalah Root, sama seperti section "Root" di bawah yang hanya
            // muncul untuk backend Root. Ini bagian dari pemisahan fitur
            // root vs non-root: pengguna root diarahkan memakai tweak
            // kernel-level (governor CPU/GPU, swappiness, dst.) di section
            // Root, bukan campur dengan tweak Input Driver.
            if (privilegeStatus.activeBackend != PrivilegeBackend.ROOT) {
                SectionCard(title = stringResource(R.string.tweak_section_touch)) {
                    // Nilai diterapkan langsung ke sistem saat slider dilepas (tidak perlu
                    // tombol "Terapkan" terpisah lagi).
                    TweakSlider(
                        label = stringResource(R.string.tweak_pointer_speed),
                        description = stringResource(R.string.tweak_pointer_speed_desc),
                        valueText = state.pointerSpeed.toString(),
                        value = state.pointerSpeed.toFloat(),
                        range = -7f..7f,
                        steps = 13,
                        onValueChange = viewModel::onPointerSpeedChange,
                        onValueChangeFinished = viewModel::onPointerSpeedChangeFinished,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_touch_boost),
                        description = stringResource(R.string.tweak_touch_boost_desc),
                        checked = state.touchBoost,
                        onCheckedChange = viewModel::onTouchBoostChange,
                        icon = Icons.Outlined.TouchApp,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.tweak_section_refresh)) {
                TweakSwitch(
                    label = stringResource(R.string.tweak_force_refresh),
                    description = stringResource(
                        R.string.tweak_force_refresh_desc,
                    ) + " (${state.displayInfo.maxRefreshRate.toInt()}Hz)",
                    checked = state.forceMaxRefreshRate,
                    onCheckedChange = viewModel::onForceRefreshChange,
                    icon = Icons.Outlined.Bolt,
                )
            }

            SectionCard(title = stringResource(R.string.tweak_section_game_mode)) {
                TweakSwitch(
                    label = stringResource(R.string.tweak_game_mode),
                    description = stringResource(R.string.tweak_game_mode_desc),
                    checked = state.gameModeEnabled,
                    onCheckedChange = viewModel::onGameModeChange,
                )
            }

            // Tweak kernel-level (CPU governor, swappiness) hanya bisa dijalankan
            // lewat akses root sungguhan — Shizuku/adb shell biasa tidak punya izin
            // tulis ke /sys atau /proc/sys, jadi section ini disembunyikan sampai
            // backend aktifnya benar-benar Root.
            if (privilegeStatus.activeBackend == PrivilegeBackend.ROOT) {
                SectionCard(title = stringResource(R.string.tweak_section_root)) {
                    TweakSwitch(
                        label = stringResource(R.string.tweak_cpu_performance),
                        description = stringResource(R.string.tweak_cpu_performance_desc),
                        checked = state.cpuPerformanceMode,
                        onCheckedChange = viewModel::onCpuPerformanceModeChange,
                        icon = Icons.Outlined.Speed,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_ram_priority),
                        description = stringResource(R.string.tweak_ram_priority_desc),
                        checked = state.ramPriorityMode,
                        onCheckedChange = viewModel::onRamPriorityModeChange,
                        icon = Icons.Outlined.Memory,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_gpu_performance),
                        description = stringResource(R.string.tweak_gpu_performance_desc),
                        checked = state.gpuPerformanceMode,
                        onCheckedChange = viewModel::onGpuPerformanceModeChange,
                        icon = Icons.Outlined.DeveloperBoard,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_thermal_throttle),
                        description = stringResource(R.string.tweak_thermal_throttle_desc),
                        checked = state.thermalThrottleOverride,
                        onCheckedChange = viewModel::onThermalThrottleOverrideChange,
                        icon = Icons.Outlined.Thermostat,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_io_scheduler_boost),
                        description = stringResource(R.string.tweak_io_scheduler_boost_desc),
                        checked = state.ioSchedulerBoost,
                        onCheckedChange = viewModel::onIoSchedulerBoostChange,
                        icon = Icons.Outlined.SdStorage,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_vm_heap_boost),
                        description = stringResource(R.string.tweak_vm_heap_boost_desc),
                        checked = state.vmHeapBoost,
                        onCheckedChange = viewModel::onVmHeapBoostChange,
                        icon = Icons.Outlined.Memory,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_kill_background_apps),
                        description = stringResource(R.string.tweak_kill_background_apps_desc),
                        checked = state.killBackgroundApps,
                        onCheckedChange = viewModel::onKillBackgroundAppsChange,
                        icon = Icons.Outlined.CleaningServices,
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::resetTweaks,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.tweak_reset),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

private enum class TweakSubTab { TWEAK, GAME_PROFILE }

/**
 * Sub-tab "list kayak sidebar" di sisi... sebenarnya ditampilkan sebagai
 * segmented switcher horizontal di ATAS konten (bukan sidebar vertikal
 * permanen) karena tab Tweak sendiri sudah berupa layar penuh di HP —
 * sidebar SESUNGGUHNYA (daftar game di kiri, detail di kanan) ada DI DALAM
 * [GameProfileScreen] begitu sub-tab "Game Profile" ini dipilih.
 */
@Composable
private fun TweakSubTabSwitcher(
    selected: TweakSubTab,
    onSelect: (TweakSubTab) -> Unit,
    showGameProfileTab: Boolean,
) {
    if (!showGameProfileTab) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TweakSubTabButton(
            label = stringResource(R.string.nav_tweak),
            selected = selected == TweakSubTab.TWEAK,
            onClick = { onSelect(TweakSubTab.TWEAK) },
            modifier = Modifier.weight(1f),
        )
        TweakSubTabButton(
            label = stringResource(R.string.nav_game_profile),
            selected = selected == TweakSubTab.GAME_PROFILE,
            onClick = { onSelect(TweakSubTab.GAME_PROFILE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TweakSubTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Header "hero" di puncak halaman Tweak: judul, subjudul singkat, dan pill ID
 * pengguna lokal (mis. "ID-67128") di kanan atas — menggantikan pill status
 * Shizuku/Root yang dipakai sebelumnya.
 *
 * Kalau [userId] masih null (alokasi dari Firestore belum/gagal), pill TIDAK
 * disembunyikan total lagi seperti sebelumnya (yang bikin terkesan "hilang"
 * atau error tanpa penjelasan) — sekarang tampil pill "Menyambungkan…" yang
 * bisa diketuk untuk mencoba ulang secara manual lewat [onRetryUserId],
 * selain otomatis dicoba ulang tiap kali layar ini kembali aktif.
 */
@Composable
private fun TweakHeader(userId: Int?, onRetryUserId: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nav_tweak),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.tweak_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (userId != null) {
            StatusPill(
                text = stringResource(R.string.tweak_user_id_format, userId),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                dotColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            StatusPill(
                text = stringResource(R.string.tweak_user_id_pending),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onRetryUserId),
            )
        }
    }
}
