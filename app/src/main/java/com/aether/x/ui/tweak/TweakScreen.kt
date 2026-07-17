package com.aether.x.ui.tweak

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.CpuGovernor
import com.aether.x.ui.appmanager.AppManagerScreen
// BuildPropScreen ada di package yang sama (com.aether.x.ui.tweak), tidak perlu import terpisah.
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.components.TweakSwitch
import com.aether.x.core.adb.WirelessDebuggingMonitor
import com.aether.x.ui.dashboard.AetherXInfoCard
import com.aether.x.ui.dashboard.GameActivitySection
import com.aether.x.ui.dashboard.DashboardViewModel
import com.aether.x.ui.dashboard.DeviceInfoSection
import com.aether.x.ui.dashboard.WirelessDebuggingQuickCard
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.launch


@Composable
fun TweakScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TweakViewModel = viewModel(),
    onNavigateToGameBooster: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // ViewModel ringkasan CPU/GPU/Suhu/Info Device untuk sub-tab Dashboard
    // (dulu bernama "Tweak" — lihat perintah rework) — lihat KDoc
    // DashboardViewModel soal kenapa ini terpisah dari TweakViewModel
    // (semua datanya tidak butuh Shizuku/Root, beda dari tweak di bawahnya).
    val dashboardViewModel: DashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()

    // FITUR BARU — kartu pintasan "Aktifkan Wireless Debugging" (khusus
    // No Root/ADB, lihat KDoc WirelessDebuggingQuickCard) di tab Dashboard.
    val wirelessDebuggingEnabled by WirelessDebuggingMonitor.state.collectAsStateWithLifecycle()

    // Dipakai HANYA untuk parameter transient onKillBackgroundAppsChange di
    // bawah (interstitial ad setelah aksi selesai) — lihat KDoc fungsi itu
    // di TweakViewModel untuk alasan kenapa Activity tidak disimpan di
    // ViewModel sendiri. LocalContext di sini selalu berupa Activity karena
    // TweakScreen hanya pernah dirender dari MainActivity.
    val context = LocalContext.current
    val activity = context as? Activity

    // Sub-tab "Game Profile", "Kernel Manager", "App Manager" & "Build.prop
    // Editor" hanya relevan untuk backend Root — direset ke Dashboard
    // otomatis kalau backend berubah non-Root (lihat LaunchedEffect di
    // bawah, setelah drawerState dideklarasikan). "Dashboard" dan "Tweak"
    // sendiri TERSEDIA UNTUK SEMUA BACKEND (termasuk NONE) — keduanya jadi
    // pintu masuk default, makanya drawer sekarang selalu bisa dibuka
    // terlepas dari backend privilese yang aktif (lihat gesturesEnabled di
    // ModalNavigationDrawer bawah).
    var selectedSubTab by remember { mutableStateOf(TweakSubTab.DASHBOARD) }

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

    // Sub-tab yang butuh privilege direset ke Dashboard otomatis kalau
    // backend berubah jadi NONE sepenuhnya (mis. pengguna cabut akses
    // root/lupakan pairing ADB dari luar) — mencegah layar menampilkan
    // konten yang sudah tidak relevan lagi. Dashboard/Tweak/Game Profile
    // TIDAK direset karena tetap relevan untuk backend apa pun (Game
    // Profile lihat KDoc di NavigationDrawerItem-nya sendiri di bawah).
    //
    // BUG FIX (lihat perintah rework — "user no root sekarang bisa pakai
    // opsi App Manager"): SEBELUMNYA kondisi ini (dan showRootOnlyItems di
    // drawer) memakai `activeBackend != PrivilegeBackend.ROOT` untuk
    // App Manager juga — TERNYATA App Manager BUKAN benar-benar root-only:
    // ketiga perintah shell-nya (lihat AppManagerRepository.kt — `pm
    // disable-user`, `pm enable`, `am force-stop`) adalah perintah yang
    // memang BISA dijalankan lewat UID `shell` (backend ADB tertanam),
    // BUKAN cuma root — ini kemampuan standar `adb shell` di Android,
    // bukan celah keamanan. Laporan "user no root bisa akses App Manager"
    // ternyata user itu sudah pairing ADB (privilege sah, bukan NONE).
    // Sekarang App Manager hanya direset kalau backend benar-benar NONE
    // (tanpa privilege apa pun) — TETAP tersedia untuk ADB maupun Root.
    // Kernel Manager & Build Prop TETAP eksklusif Root murni (keduanya
    // menyentuh /sys/kernel, /proc/sys, dan build.prop sistem yang butuh
    // akses lebih dalam dari yang bisa diberikan UID shell).
    LaunchedEffect(privilegeStatus.activeBackend) {
        val backend = privilegeStatus.activeBackend
        val needsRootOnly = selectedSubTab == TweakSubTab.KERNEL_MANAGER ||
            selectedSubTab == TweakSubTab.BUILD_PROP
        val needsAnyPrivilege = selectedSubTab == TweakSubTab.APP_MANAGER
        val shouldReset = (needsRootOnly && backend != PrivilegeBackend.ROOT) ||
            (needsAnyPrivilege && backend == PrivilegeBackend.NONE)
        if (shouldReset) {
            selectedSubTab = TweakSubTab.DASHBOARD
            if (drawerState.isOpen) drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Drawer sekarang SELALU bisa dibuka (gestur maupun tombol
        // hamburger) untuk SEMUA backend — beda dari sebelumnya yang
        // cuma aktif untuk Root. Alasannya: sekarang drawer minimal berisi
        // "Dashboard" dan "Tweak" yang relevan untuk backend apa pun (lihat
        // TweakDrawerContent); Game Profile juga tersedia untuk semua
        // backend; App Manager tersedia untuk Root ATAU ADB (lihat KDoc
        // TweakDrawerContent soal kenapa BUKAN root-only); Kernel
        // Manager/Build.prop Editor tetap eksklusif Root murni.
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                TweakDrawerContent(
                    selected = selectedSubTab,
                    showPrivilegedItems = privilegeStatus.activeBackend != PrivilegeBackend.NONE,
                    showRootOnlyItems = privilegeStatus.activeBackend == PrivilegeBackend.ROOT,
                    onSelect = { tab ->
                        selectedSubTab = tab
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNavigateToGameBooster = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToGameBooster()
                    },
                )
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        // Section tweak biasa & Kernel Manager perlu scroll dari
                        // Column ini (keduanya cuma berisi SectionCard biasa,
                        // bukan LazyColumn internal). GameProfileScreen & App
                        // Manager beda — masing-masing mengurus scroll-nya
                        // SENDIRI secara internal (LazyColumn sendiri-sendiri)
                        // — verticalScroll ganda di sini akan bentrok dengan
                        // LazyColumn di dalamnya.
                        if (selectedSubTab == TweakSubTab.GAME_PROFILE ||
                            selectedSubTab == TweakSubTab.APP_MANAGER ||
                            selectedSubTab == TweakSubTab.BUILD_PROP
                        ) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        },
                    )
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Header "AetherX" (ikon + judul) + pill ID pengguna SELALU
                // tampil, di SEMUA sub-tab (Dashboard/Tweak/Game
                // Profile/Kernel Manager/App Manager/Build.prop Editor) —
                // hanya konten di bawahnya yang berganti. Tombol hamburger
                // (buka drawer) SEKARANG SELALU tampil untuk backend apa
                // pun (lihat KDoc TweakHeader) karena drawer sekarang selalu
                // berguna (minimal ada Dashboard + Tweak).
                TweakHeader(
                    userId = state.userId,
                    isMembershipActive = state.isMembershipActive,
                    onRetryUserId = viewModel::retryResolveUserIdIfMissing,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                )

                if (selectedSubTab == TweakSubTab.DASHBOARD) {
                    // === Konten tab "Dashboard" ===
                    // REWORK TOTAL (lihat perintah rework — "rework total
                    // tampilan Dashboard hapus section CPU, GPU, SUHU..."):
                    // hero card sekarang ramping satu baris (logo+versi+pill
                    // mode akses, lihat KDoc AetherXInfoCard) — kartu status
                    // mode akses terpisah (DashboardStatusRow) DIHAPUS karena
                    // pill-nya sudah pindah ke dalam hero card ini, tidak
                    // perlu diulang di kartu kedua.
                    AetherXInfoCard(activeBackend = privilegeStatus.activeBackend)

                    // FITUR BARU — perbaikan alur "server Wireless debugging
                    // mati" (lihat perintah rework: "ga perlu setup isi kode
                    // 6 digit lagi biar ga ribet / buatkan opsi aktifkan
                    // Wireless Debugging di tab Settings/Dashboard khusus no
                    // root & muncul ketika Wireless Debugging belum aktif dan
                    // hilangkan saat aktif"). Kartu ini SENGAJA return
                    // langsung (tidak render apa pun) di dalam composable-nya
                    // sendiri kalau backend aktif Root ATAU toggle sedang
                    // menyala — lihat KDoc WirelessDebuggingQuickCard.
                    WirelessDebuggingQuickCard(
                        activeBackend = privilegeStatus.activeBackend,
                        wirelessDebuggingEnabled = wirelessDebuggingEnabled,
                        onOpenWirelessDebugging = { PrivilegeManager.openWirelessDebuggingSettings(context) },
                    )

                    // Monitor CPU/GPU/Suhu (gauge kecil) DIHAPUS TOTAL dari
                    // Dashboard — domain itu sekarang murni milik Game
                    // Booster (lihat GameBoosterScreen), yang memang dipakai
                    // SELAMA sesi bermain, bukan di layar ringkasan yang
                    // dilihat sebentar-sebentar.

                    // FITUR BARU: "Aktivitas Game" — daftar game terpasang
                    // yang bisa di-scroll horizontal, game terakhir dipakai
                    // di posisi pertama, tap untuk buka langsung.
                    GameActivitySection(
                        games = dashboardState.installedGames,
                        loading = dashboardState.loadingGames,
                        lastPlayedPackage = dashboardState.lastPlayedPackage,
                        onGameClick = dashboardViewModel::onGameClick,
                    )

                    DeviceInfoSection(info = dashboardState.deviceInfo)
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.GAME_PROFILE) {
                    // GameProfileScreen mengisi SISA ruang di bawah header
                    // dengan tata letaknya sendiri (sidebar list + panel detail).
                    GameProfileScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        // Padding horizontal/vertical sudah diterapkan Column induk
                        // di atas, jadi GameProfileScreen tidak perlu padding ganda.
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.KERNEL_MANAGER) {
                    // KernelManagerSection dulu sempat ditumpuk di dalam section
                    // "Kernel Tuning (Root)" pada sub-tab Tweak biasa — dipindah
                    // ke sub-tab tersendiri supaya tab Tweak tetap ringkas. Tidak
                    // perlu Modifier.weight/verticalScroll tambahan di sini —
                    // KernelManagerSection cuma SectionCard biasa (bukan
                    // LazyColumn internal seperti GameProfileScreen), jadi cukup
                    // jadi child biasa dan ikut ter-scroll oleh Column induk yang
                    // sudah scrollable untuk sub-tab ini (lihat kondisi scroll di
                    // atas).
                    KernelManagerSection()
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.APP_MANAGER) {
                    // AppManagerScreen (seperti GameProfileScreen) punya
                    // LazyColumn internal sendiri untuk daftar aplikasi yang
                    // bisa sangat panjang — BUTUH Modifier.weight(1f) supaya
                    // dapat tinggi terbatas dari Column induk, dan TIDAK BOLEH
                    // ikut di-scroll oleh verticalScroll Column induk (yang
                    // untuk sub-tab ini memang tidak diterapkan — lihat kondisi
                    // scroll di atas, APP_MANAGER termasuk yang dikecualikan
                    // sama seperti GAME_PROFILE).
                    AppManagerScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.BUILD_PROP) {
                    // BuildPropScreen juga punya LazyColumn internal sendiri
                    // (daftar key bisa ratusan baris) — perlakuan weight(1f)
                    // yang sama seperti APP_MANAGER, dikecualikan dari
                    // verticalScroll Column induk (lihat kondisi scroll di
                    // atas, BUILD_PROP ditambahkan ke daftar pengecualian yang
                    // sama).
                    BuildPropScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                // === Konten tab "Tweak" (dulu bercampur dengan monitor CPU/GPU
                // di sub-tab yang sama sebelum Dashboard dipisah — lihat
                // perintah rework) ===
                // Section Input Driver (pointer speed & touch boost) khusus untuk
                // backend NON-ROOT (Shizuku) — disembunyikan saat backend aktif
                // adalah Root, sama seperti section "Root" di bawah yang hanya
                // muncul untuk backend Root. Ini bagian dari pemisahan fitur
                // root vs non-root: pengguna root diarahkan memakai tweak
                // kernel-level (governor CPU/GPU, swappiness, dst.) di section
                // Root, bukan campur dengan tweak Input Driver.
                if (privilegeStatus.activeBackend != PrivilegeBackend.ROOT) {
                    SectionCard(title = stringResource(R.string.tweak_section_touch), watermarkIcon = Icons.Outlined.TouchApp) {
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

                SectionCard(title = stringResource(R.string.tweak_section_refresh), watermarkIcon = Icons.Outlined.RestartAlt) {
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

                SectionCard(title = stringResource(R.string.tweak_section_game_mode), watermarkIcon = Icons.Outlined.NotificationsOff) {
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
                //
                // REWORK LAYOUT (lihat perintah rework — "layout tata letak
                // lebih rapi"): sebelumnya SEMUA 6+ tweak root ditumpuk datar
                // dalam satu SectionCard tunggal ("tweak_section_root").
                // Sekarang dipecah jadi 2 SectionCard kategori — "CPU &
                // Performa" (governor, RAM priority, GPU performance,
                // thermal override) dan "Memori & Sistem" (I/O scheduler, VM
                // heap, kill background apps, doze) — konsisten secara
                // visual dengan pengelompokan kategori yang sudah dipakai di
                // GameProfileScreen (CPU / GPU & Termal / Sistem).
                if (privilegeStatus.activeBackend == PrivilegeBackend.ROOT) {
                    SectionCard(title = stringResource(R.string.tweak_section_root_cpu), watermarkIcon = Icons.Outlined.Memory) {
                        TweakDropdown(
                            label = stringResource(R.string.tweak_cpu_governor),
                            description = stringResource(R.string.tweak_cpu_governor_desc),
                            options = listOf(
                                CpuGovernor.SCHEDUTIL,
                                CpuGovernor.PERFORMANCE,
                                CpuGovernor.ONDEMAND,
                                CpuGovernor.POWERSAVE,
                                CpuGovernor.UNIVERSAL,
                            ),
                            selected = state.cpuGovernor,
                            optionLabel = { governor -> cpuGovernorLabel(governor) },
                            onOptionSelected = viewModel::onCpuGovernorChange,
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
                    }

                    SectionCard(title = stringResource(R.string.tweak_section_root_system), watermarkIcon = Icons.Outlined.Terminal) {
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
                        // FITUR BARU (lihat perintah rework — "tambahkan
                        // fitur baru yang berguna khusus root"): cegah OS
                        // membekukan proses game/service background saat
                        // perangkat idle sesaat — lihat KDoc
                        // TweakRepository.applyDozeDisable.
                        TweakSwitch(
                            label = stringResource(R.string.tweak_doze_disabled),
                            description = stringResource(R.string.tweak_doze_disabled_desc),
                            checked = state.dozeDisabled,
                            onCheckedChange = viewModel::onDozeDisabledChange,
                            icon = Icons.Outlined.BatteryChargingFull,
                        )
                        TweakSwitch(
                            label = stringResource(R.string.tweak_kill_background_apps),
                            description = stringResource(R.string.tweak_kill_background_apps_desc),
                            checked = state.killBackgroundApps,
                            onCheckedChange = { checked -> viewModel.onKillBackgroundAppsChange(checked, activity) },
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
}

/** Label tampilan untuk tiap pilihan [CpuGovernor] di dropdown Governor CPU. */
@Composable
private fun cpuGovernorLabel(governor: CpuGovernor): String = when (governor) {
    CpuGovernor.SCHEDUTIL -> stringResource(R.string.tweak_cpu_governor_schedutil)
    CpuGovernor.PERFORMANCE -> stringResource(R.string.tweak_cpu_governor_performance)
    CpuGovernor.ONDEMAND -> stringResource(R.string.tweak_cpu_governor_ondemand)
    CpuGovernor.POWERSAVE -> stringResource(R.string.tweak_cpu_governor_battery)
    CpuGovernor.UNIVERSAL -> stringResource(R.string.tweak_cpu_governor_universal)
}

private enum class TweakSubTab { DASHBOARD, TWEAK, GAME_PROFILE, KERNEL_MANAGER, APP_MANAGER, BUILD_PROP }

/**
 * Isi drawer navigasi. Dibuka lewat tombol hamburger di [TweakHeader] atau
 * swipe dari tepi kiri (`gesturesEnabled` di [ModalNavigationDrawer] pada
 * [TweakScreen] SEKARANG SELALU true, lihat KDoc di sana) — drawer ini
 * SEKARANG RELEVAN UNTUK SEMUA BACKEND (bukan cuma Root lagi seperti
 * sebelumnya), karena minimal berisi dua item yang tersedia untuk backend
 * apa pun:
 * - "Dashboard": kartu identitas app + Aktivitas Game (daftar game
 *   terpasang, scroll horizontal) + Info Device (lihat
 *   [DashboardViewModel], [GameActivitySection], [DeviceInfoSection]) — CPU/
 *   GPU/Suhu SUDAH TIDAK ADA di sini sejak rework total, lihat KDoc
 *   [DashboardViewModel].
 * - "Game Booster": layar landscape khusus untuk dipakai SELAMA sesi
 *   bermain — FPS overlay, mode game, jangan ganggu, screenshot, mode
 *   boost/hemat, dan grafik monitoring CPU/GPU/Suhu real-time (lihat
 *   [com.aether.x.ui.booster.GameBoosterScreen]) — tersedia untuk SEMUA
 *   backend seperti "Dashboard" & "Tweak".
 * - "Tweak": seluruh kontrol tweak (Input Driver untuk Shizuku, Refresh
 *   Rate, Mode Game, dan Kernel Tuning untuk Root) — konten yang
 *   SEBELUMNYA bercampur dengan monitor CPU/GPU di satu sub-tab yang sama,
 *   sekarang dipisah murni jadi kontrol tweak saja.
 *
 * Item "Kernel Manager" (baca/tulis frekuensi & governor per-core CPU, GPU,
 * dan suhu live) dan "Build.prop Editor" (edit persisten properti sistem
 * lewat file, lihat KDoc [BuildPropScreen] soal bedanya dengan `setprop`
 * runtime) HANYA dirender kalau [showRootOnlyItems] true (backend Root
 * aktif — keduanya menyentuh /sys/kernel, /proc/sys, dan file sistem yang
 * butuh akses lebih dalam dari yang bisa diberikan UID shell ADB).
 *
 * Item "App Manager" (freeze/unfreeze aplikasi pihak ketiga & bloatware
 * terkurasi) dirender kalau [showPrivilegedItems] true (backend Root ATAU
 * ADB tertanam) — BUKAN root-only. BUG FIX (lihat perintah rework "user no
 * root sekarang bisa pakai opsi App Manager"): perintah shell App Manager
 * (`pm disable-user`, `pm enable`, `am force-stop` — lihat
 * AppManagerRepository.kt) memang BISA dijalankan lewat UID `shell`
 * (backend ADB), bukan cuma root — ini kemampuan standar `adb shell` di
 * Android. SEBELUMNYA item ini ikut disatukan ke grup root-only bersama
 * Kernel Manager/Build Prop, sehingga kategorisasinya menyesatkan (label
 * "Khusus Root" padahal ADB pun cukup) walau bukan celah keamanan (executor
 * tetap null-checked di AppManagerViewModel untuk backend NONE).
 *
 * Kedua grup dipisah dengan [HorizontalDivider] + label kecil masing-masing
 * ("Privilege Aktif" utk App Manager, "Khusus Root" utk Kernel
 * Manager/Build Prop) supaya jelas kenapa jumlah item drawer berbeda-beda
 * tergantung backend yang aktif, bukan terlihat seperti bug/item hilang.
 */
// Saklar fitur (RILIS v2.0 — lihat perintah rework "jadikan opsi drawer
// game booster sementara tidak bisa diakses"): Game Booster hasil rework
// total masih perlu pengujian lebih lanjut sebelum dirilis ke pengguna,
// jadi item drawer-nya ditampilkan terkunci (disabled + badge "Segera
// Hadir") sampai fitur ini resmi dibuka. Ubah ke false untuk unlock —
// pola SAMA PERSIS dengan FPS_MONITOR_FEATURE_UNLOCKED di
// FpsMonitorSettingsSection.kt (lihat KDoc di sana).
private const val GAME_BOOSTER_DRAWER_LOCKED = true

// Lambda no-op bertipe eksplisit (() -> Unit) untuk onClick NavigationDrawerItem
// yang terkunci — didefinisikan sebagai val TERPISAH (bukan `{}` inline langsung
// di dalam ekspresi if/else) supaya tidak ambigu secara sintaks Kotlin antara
// "blok kosong" vs "lambda kosong bertipe () -> Unit" pada posisi tersebut.
private val LOCKED_ITEM_NO_OP_CLICK: () -> Unit = {}

@Composable
private fun TweakDrawerContent(
    selected: TweakSubTab,
    showPrivilegedItems: Boolean,
    showRootOnlyItems: Boolean,
    onSelect: (TweakSubTab) -> Unit,
    onNavigateToGameBooster: () -> Unit,
) {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_dashboard)) },
        icon = { Icon(imageVector = Icons.Outlined.SpaceDashboard, contentDescription = null) },
        selected = selected == TweakSubTab.DASHBOARD,
        onClick = { onSelect(TweakSubTab.DASHBOARD) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_tweak)) },
        icon = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
        selected = selected == TweakSubTab.TWEAK,
        onClick = { onSelect(TweakSubTab.TWEAK) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_game_profile)) },
        icon = { Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null) },
        selected = selected == TweakSubTab.GAME_PROFILE,
        onClick = { onSelect(TweakSubTab.GAME_PROFILE) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
    // FITUR BARU — lihat KDoc lengkap di atas kelas ini soal Game Booster:
    // TERSEDIA UNTUK SEMUA BACKEND (bukan di-gate showRootOnlyItems) karena
    // mode Boost/Hemat/DND tetap berguna walau sebagian aksi (FPS real-time,
    // CPU governor) memerlukan root — bukan destination TweakSubTab
    // internal seperti item lain di atas, tapi NAVIGASI KELUAR sepenuhnya
    // (lihat AetherXRoutes.GAME_BOOSTER) karena butuh memaksa orientasi
    // landscape yang berbeda dari seluruh scaffold TweakScreen ini yang
    // portrait.
    //
    // BUG FIX / RILIS v2.0 (lihat perintah rework — "jadikan opsi drawer
    // game booster sementara tidak bisa diakses, gak bisa dipencet"):
    // dikunci SEMENTARA lewat const [GAME_BOOSTER_DRAWER_LOCKED] di bawah —
    // pola SAMA PERSIS seperti FPS_MONITOR_FEATURE_UNLOCKED di
    // FpsMonitorSettingsSection.kt (satu const boolean, gampang di-toggle
    // balik saat fitur ini siap dirilis).
    //
    // BUG FIX (CI gagal build — "No parameter with name 'enabled' found"):
    // SEBELUMNYA dipakai `enabled = !GAME_BOOSTER_DRAWER_LOCKED` dengan
    // asumsi NavigationDrawerItem Material3 punya parameter `enabled`
    // otomatis seperti kebanyakan komponen M3 lain (Button, Switch, dst)
    // — TERNYATA TIDAK, NavigationDrawerItem HANYA punya: label, selected,
    // onClick, modifier, icon, badge, shape, colors, interactionSource
    // (diverifikasi dari dokumentasi API resmi androidx.compose.material3,
    // bukan asumsi lagi). Diganti 2 mekanisme MANUAL:
    // 1. `.alpha(0.38f)` (alpha "disabled" standar Material Design) pada
    //    modifier terluar — meredupkan SELURUH item (ikon+label+badge
    //    sekaligus, karena alpha di root Modifier berlaku ke seluruh
    //    subtree) TANPA perlu menerapkan alpha terpisah ke tiap elemen.
    // 2. `onClick` diganti no-op ({}) saat terkunci, TIDAK memanggil
    //    [onNavigateToGameBooster] sama sekali — supaya item benar-benar
    //    "gak bisa dipencet" (bukan cuma redup visual tapi tetap
    //    ter-trigger kalau tersentuh).
    // Begitu GAME_BOOSTER_DRAWER_LOCKED di-flip ke `false`, kedua efek ini
    // otomatis nonaktif tanpa langkah lain yang perlu diingat/dikembalikan.
    // Badge "Segera Hadir" ditambahkan supaya pengguna paham KENAPA item
    // ini tidak bisa ditekan, bukan mengira itu bug.
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.game_booster_title)) },
        icon = { Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null) },
        badge = {
            // BUG FIX (ditemukan saat verifikasi akhir): SEBELUMNYA
            // `return@NavigationDrawerItem` dipakai di sini untuk early-exit
            // — TAPI `badge` adalah named parameter (bukan trailing lambda),
            // jadi label implisit `@NavigationDrawerItem` TIDAK valid untuk
            // lambda ini dan akan gagal compile ("unresolved label" / label
            // mengikat ke tempat yang salah). Diganti pola `if` sederhana
            // tanpa return berlabel sama sekali — lambda Composable
            // ber-tipe Unit tidak butuh early return, cukup biarkan blok
            // if tidak mengeksekusi apa pun kalau kondisinya false.
            if (GAME_BOOSTER_DRAWER_LOCKED) {
                Text(
                    text = stringResource(R.string.game_booster_drawer_locked_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        selected = false,
        onClick = if (GAME_BOOSTER_DRAWER_LOCKED) LOCKED_ITEM_NO_OP_CLICK else onNavigateToGameBooster,
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .alpha(if (GAME_BOOSTER_DRAWER_LOCKED) 0.38f else 1f),
    )

    if (showPrivilegedItems) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
        )
        Text(
            text = stringResource(R.string.drawer_privileged_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_app_manager)) },
            icon = { Icon(imageVector = Icons.Outlined.Apps, contentDescription = null) },
            selected = selected == TweakSubTab.APP_MANAGER,
            onClick = { onSelect(TweakSubTab.APP_MANAGER) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }

    if (showRootOnlyItems) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
        )
        Text(
            text = stringResource(R.string.drawer_root_only_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.kernel_manager_title)) },
            icon = { Icon(imageVector = Icons.Outlined.DeveloperBoard, contentDescription = null) },
            selected = selected == TweakSubTab.KERNEL_MANAGER,
            onClick = { onSelect(TweakSubTab.KERNEL_MANAGER) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_build_prop)) },
            icon = { Icon(imageVector = Icons.Outlined.Code, contentDescription = null) },
            selected = selected == TweakSubTab.BUILD_PROP,
            onClick = { onSelect(TweakSubTab.BUILD_PROP) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/**
 * Header "hero" di puncak halaman (dulu menampilkan judul "Tweak" + subjudul
 * deskripsi panjang — lihat perintah rework): sekarang menampilkan ikon
 * logo AetherX di kiri berdampingan dengan judul "AetherX", dan pill ID
 * pengguna lokal (mis. "ID-67128") rapi di kanan — menggantikan pill status
 * Shizuku/Root yang dipakai sebelumnya. Header ini SAMA untuk seluruh
 * sub-tab (Dashboard/Tweak/Game Profile/Kernel Manager/App Manager/Build.prop),
 * hanya konten di bawahnya yang berganti.
 *
 * Kalau [userId] masih null (alokasi dari Firestore belum/gagal), pill TIDAK
 * disembunyikan total lagi seperti sebelumnya (yang bikin terkesan "hilang"
 * atau error tanpa penjelasan) — sekarang tampil pill "Menyambungkan…" yang
 * bisa diketuk untuk mencoba ulang secara manual lewat [onRetryUserId],
 * selain otomatis dicoba ulang tiap kali layar ini kembali aktif.
 *
 * Tombol hamburger (buka drawer navigasi Dashboard/Tweak/Game Profile/Kernel
 * Manager/App Manager/Build.prop Editor) SEKARANG SELALU tampil untuk
 * backend apa pun (dulu hanya untuk Root — lihat perintah rework: drawer
 * sekarang minimal berisi Dashboard+Tweak yang relevan untuk semua orang).
 */
/**
 * REWORK (lihat perintah rework):
 * 1. "untuk badge ID jangan ada • nya" — [dotColor] TIDAK LAGI diteruskan
 *    ke [StatusPill] untuk badge ID pengguna (dulu selalu diisi warna
 *    primary/onSurfaceVariant, yang menampilkan lingkaran kecil di kiri
 *    teks menyerupai bullet). Badge sekarang hanya berisi ikon (kalau
 *    member VIP) + teks ID.
 * 2. "untuk badge user Membership ada Logo VIP di sisi kiri badge ID dan
 *    itu real icon" — [isMembershipActive] SEKARANG dialirkan dari
 *    [TweakViewModel] (baca [com.aether.x.data.AetherXPreferences.isMembershipActive],
 *    status yang SAMA dipakai [com.aether.x.ui.membership.MembershipViewModel]
 *    untuk [com.aether.x.ui.membership.MembershipUiStatus.ACTIVE]) — kalau
 *    true, badge ID menampilkan ikon mahkota nyata (`Icons.Filled.WorkspacePremium`,
 *    ikon Material yang SAMA dipakai tab Membership di bottom nav, BUKAN
 *    placeholder shape) di ujung kiri, sebelum teks ID.
 */
@Composable
private fun TweakHeader(
    userId: Int?,
    isMembershipActive: Boolean,
    onRetryUserId: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.tweak_menu_open_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_aetherx_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (userId != null) {
            // REWORK (lihat perintah rework — "warna card ... default
            // mengikuti warna tema bawaan"): badge ID SEKARANG memakai
            // MaterialTheme.colorScheme.primary (biru [AccentBlue], warna
            // aksen utama app) — bukan lagi DashboardAccentOrange (bekas token
            // oranye custom yang sebelumnya hanya dipakai di sini & kartu
            // Dashboard, sekarang dihapus supaya seluruh app konsisten satu
            // identitas warna).
            StatusPill(
                text = stringResource(R.string.tweak_user_id_format, userId),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                leadingIcon = if (isMembershipActive) Icons.Outlined.WorkspacePremium else null,
                leadingIconTint = MaterialTheme.colorScheme.primary,
            )
        } else {
            StatusPill(
                text = stringResource(R.string.tweak_user_id_pending),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onRetryUserId),
            )
        }
    }
}
