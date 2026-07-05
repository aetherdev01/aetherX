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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
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
import com.aether.x.ui.dashboard.DashboardMonitorRow
import com.aether.x.ui.dashboard.DashboardViewModel
import com.aether.x.ui.dashboard.DeviceInfoSection
import kotlinx.coroutines.launch


@Composable
fun TweakScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TweakViewModel = viewModel(),
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

    // Dipakai HANYA untuk parameter transient onKillBackgroundAppsChange di
    // bawah (interstitial ad setelah aksi selesai) — lihat KDoc fungsi itu
    // di TweakViewModel untuk alasan kenapa Activity tidak disimpan di
    // ViewModel sendiri. LocalContext di sini selalu berupa Activity karena
    // TweakScreen hanya pernah dirender dari MainActivity.
    val activity = LocalContext.current as? Activity

    // Sub-tab "Game Profile", "Kernel Manager" & "App Manager" (lihat
    // GameProfileScreen, KernelManagerSection, AppManagerScreen) hanya
    // relevan untuk backend Root — direset ke
    // Tweak biasa otomatis kalau backend berubah non-Root (lihat
    // LaunchedEffect di bawah, setelah drawerState dideklarasikan).
    var selectedSubTab by remember { mutableStateOf(TweakSubTab.TWEAK) }

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

    // Drawer otomatis ditutup kalau backend berubah jadi non-Root sementara
    // sedang terbuka (mis. pengguna cabut akses root dari luar) — mencegah
    // drawer terbuka menampilkan item Game Profile/Kernel Manager/App
    // Manager yang sudah tidak relevan lagi untuk backend baru.
    LaunchedEffect(privilegeStatus.activeBackend) {
        if (privilegeStatus.activeBackend != PrivilegeBackend.ROOT) {
            selectedSubTab = TweakSubTab.TWEAK
            if (drawerState.isOpen) drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // gesturesEnabled hanya untuk backend Root — sub-tab lain (Game
        // Profile, Kernel Manager) memang cuma relevan untuk Root, jadi
        // swipe-buka-drawer dimatikan total untuk backend Shizuku/NONE
        // supaya tidak ada gestur yang membuka drawer kosong/percuma.
        gesturesEnabled = privilegeStatus.activeBackend == PrivilegeBackend.ROOT,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                TweakDrawerContent(
                    selected = selectedSubTab,
                    onSelect = { tab ->
                        selectedSubTab = tab
                        coroutineScope.launch { drawerState.close() }
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header "AetherX" (ikon + judul) + pill ID pengguna SELALU
                // tampil, baik di sub-tab Dashboard, Game Profile, Kernel
                // Manager, App Manager, maupun Build.prop Editor — hanya
                // konten di bawahnya yang berganti. Tombol hamburger (buka
                // drawer) HANYA muncul untuk backend Root, karena tanpa Root
                // cuma ada satu sub-tab (Dashboard) — drawer tidak ada
                // gunanya.
                TweakHeader(
                    userId = state.userId,
                    onRetryUserId = viewModel::retryResolveUserIdIfMissing,
                    showMenuButton = privilegeStatus.activeBackend == PrivilegeBackend.ROOT,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                )

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

                // === Konten tab Dashboard (dulu "Tweak" — lihat perintah
                // rework) ===
                // Ringkasan CPU/GPU/Suhu (gauge kecil) + Info Device SELALU
                // tampil paling atas untuk sub-tab Dashboard, TIDAK butuh
                // Shizuku/Root sama sekali (lihat KDoc DashboardViewModel) —
                // baru di bawahnya section-section tweak yang sudah ada
                // (Input Driver, Refresh Rate, Mode Game, Root) mengikuti
                // gating akses masing-masing seperti sebelumnya.
                DashboardMonitorRow(
                    cpuLoadPercent = dashboardState.cpuLoadPercent,
                    gpuLoadPercent = dashboardState.gpuLoadPercent,
                    temperatureCelsius = dashboardState.temperatureCelsius,
                )
                DeviceInfoSection(info = dashboardState.deviceInfo)

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

private enum class TweakSubTab { TWEAK, GAME_PROFILE, KERNEL_MANAGER, APP_MANAGER, BUILD_PROP }

/**
 * Isi drawer navigasi sub-tab Dashboard (dulu disebut "Tweak" — lihat
 * perintah rework). Dibuka lewat tombol hamburger di [TweakHeader] atau
 * swipe dari tepi kiri (lihat `gesturesEnabled` di [ModalNavigationDrawer]
 * pada [TweakScreen]) — HANYA relevan untuk backend Root, karena tanpa Root
 * cuma ada satu sub-tab (Dashboard) jadi drawer ini tidak pernah dibuka
 * (tombol hamburger-nya juga tidak dirender untuk backend selain Root,
 * lihat [TweakHeader]).
 *
 * Item "Kernel Manager" (baca/tulis frekuensi & governor per-core CPU, GPU,
 * dan suhu live), "App Manager" (freeze/unfreeze aplikasi pihak ketiga
 * & bloatware terkurasi), dan "Build.prop Editor" (edit persisten properti
 * sistem lewat file, lihat KDoc [BuildPropScreen] soal bedanya dengan
 * `setprop` runtime) masing-masing sub-tab tersendiri, BUKAN digabung
 * ke dalam section "Kernel Tuning (Root)" di sub-tab Dashboard biasa seperti
 * percobaan awal Kernel Manager — menumpuk semuanya di satu scroll membuat
 * tab Dashboard jadi sangat panjang dan padat. Sebelumnya dicoba sebagai
 * segmented switcher horizontal di atas konten, tapi drawer dipilih supaya
 * header tab Dashboard tetap ringkas (satu tombol hamburger, bukan banyak
 * tombol sub-tab yang selalu makan tempat).
 */
@Composable
private fun TweakDrawerContent(
    selected: TweakSubTab,
    onSelect: (TweakSubTab) -> Unit,
) {
    Text(
        text = stringResource(R.string.nav_dashboard),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_dashboard)) },
        icon = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
        selected = selected == TweakSubTab.TWEAK,
        onClick = { onSelect(TweakSubTab.TWEAK) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_game_profile)) },
        icon = { Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null) },
        selected = selected == TweakSubTab.GAME_PROFILE,
        onClick = { onSelect(TweakSubTab.GAME_PROFILE) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.kernel_manager_title)) },
        icon = { Icon(imageVector = Icons.Outlined.DeveloperBoard, contentDescription = null) },
        selected = selected == TweakSubTab.KERNEL_MANAGER,
        onClick = { onSelect(TweakSubTab.KERNEL_MANAGER) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_app_manager)) },
        icon = { Icon(imageVector = Icons.Outlined.Apps, contentDescription = null) },
        selected = selected == TweakSubTab.APP_MANAGER,
        onClick = { onSelect(TweakSubTab.APP_MANAGER) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.nav_build_prop)) },
        icon = { Icon(imageVector = Icons.Outlined.Code, contentDescription = null) },
        selected = selected == TweakSubTab.BUILD_PROP,
        onClick = { onSelect(TweakSubTab.BUILD_PROP) },
        colors = NavigationDrawerItemDefaults.colors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/**
 * Header "hero" di puncak halaman (dulu menampilkan judul "Tweak" + subjudul
 * deskripsi panjang — lihat perintah rework): sekarang menampilkan ikon
 * logo AetherX di kiri berdampingan dengan judul "AetherX", dan pill ID
 * pengguna lokal (mis. "ID-67128") rapi di kanan — menggantikan pill status
 * Shizuku/Root yang dipakai sebelumnya. Header ini SAMA untuk seluruh
 * sub-tab (Dashboard/Game Profile/Kernel Manager/App Manager/Build.prop),
 * hanya konten di bawahnya yang berganti.
 *
 * Kalau [userId] masih null (alokasi dari Firestore belum/gagal), pill TIDAK
 * disembunyikan total lagi seperti sebelumnya (yang bikin terkesan "hilang"
 * atau error tanpa penjelasan) — sekarang tampil pill "Menyambungkan…" yang
 * bisa diketuk untuk mencoba ulang secara manual lewat [onRetryUserId],
 * selain otomatis dicoba ulang tiap kali layar ini kembali aktif.
 *
 * Tombol hamburger (buka drawer sub-tab Dashboard/Game Profile/Kernel
 * Manager/App Manager) HANYA muncul kalau [showMenuButton] true (yaitu
 * backend Root) — tanpa Root cuma ada satu sub-tab, jadi tombol menu tidak
 * ada gunanya dan malah membingungkan kalau tetap tampil.
 */
@Composable
private fun TweakHeader(
    userId: Int?,
    onRetryUserId: () -> Unit,
    showMenuButton: Boolean,
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
            if (showMenuButton) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.tweak_menu_open_cd),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Image(
                painter = painterResource(id = R.drawable.ic_aetherx_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp),
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
