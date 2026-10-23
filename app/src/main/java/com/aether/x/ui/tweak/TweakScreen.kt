package com.aether.x.ui.tweak

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideogameAsset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.withoutBottom
import com.aether.x.ui.components.LocalBackdropHaze
import com.aether.x.ui.components.glassPanel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.CpuGovernor
import com.aether.x.ui.appmanager.AppManagerScreen

import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.components.TweakSwitch
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.components.dragPopEffect
import com.aether.x.ui.dashboard.DashboardGameMonitorSection
import com.aether.x.ui.dashboard.RecentlyPlayedGamesSection
import com.aether.x.ui.dashboard.DashboardViewModel
import com.aether.x.ui.dashboard.DeviceStatusSection
import com.aether.x.ui.dashboard.RamCleanerCard
import com.aether.x.ui.monitor.RootMonitorSection
import com.aether.x.ui.monitor.GameMonitorScreen
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.aether.x.core.locale.str

@Composable
fun TweakScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TweakViewModel = viewModel(),
    dashboardRequest: Int = 0,
    onlineUserCount: Int = -1,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Sumber blur drawer = latar aurora (zIndex 0, dari LocalBackdropHaze) + isi
    // layar Tweak (zIndex 1, SIBLING sheet). Latar ikut jadi sumber supaya area
    // kosong juga terblur; tanpa itu blur hanya muncul di belakang konten.
    val backdropHaze = LocalBackdropHaze.current
    val ownHaze = rememberHazeState(blurEnabled = true)
    val drawerHaze = backdropHaze ?: ownHaze
    val coroutineScope = rememberCoroutineScope()

    val dashboardViewModel: DashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
    val gameProfileViewModel: GameProfileViewModel = viewModel()

    val context = LocalContext.current
    val activity = context as? Activity

    var selectedSubTab by remember { mutableStateOf(TweakSubTab.DASHBOARD) }

    // Bottom tab Dashboard adalah perintah untuk kembali ke HOME/Dashboard,
    // bukan sekadar memilih MainTab.TWEAK. Saat user sedang berada di
    // Game Profile/Root Monitor/App Manager, MainTab.TWEAK tetap aktif sehingga
    // klik Dashboard sebelumnya tidak mengubah apa pun. Request counter dari
    // MainScreen membuat event klik tetap terdeteksi walaupun index tab sama.
    LaunchedEffect(dashboardRequest) {
        if (dashboardRequest > 0) {
            selectedSubTab = TweakSubTab.DASHBOARD
            if (drawerState.isOpen) {
                drawerState.close()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDetectedGames()
                dashboardViewModel.refreshDeviceInfo()
                viewModel.retryResolveUserIdIfMissing()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(privilegeStatus.rootGranted) {
        val needsRoot = selectedSubTab == TweakSubTab.KERNEL_MANAGER ||
            selectedSubTab == TweakSubTab.BUILD_PROP ||
            selectedSubTab == TweakSubTab.ROOT_MONITOR ||
            selectedSubTab == TweakSubTab.APP_MANAGER ||
            selectedSubTab == TweakSubTab.POWER ||
            selectedSubTab == TweakSubTab.KERNEL_TOOLS ||
            selectedSubTab == TweakSubTab.TERMINAL
        val shouldReset = needsRoot && !privilegeStatus.rootGranted
        if (shouldReset) {
            selectedSubTab = TweakSubTab.DASHBOARD
            if (drawerState.isOpen) drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,

        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                // Keep the blur panel stretched over the complete drawer height.
                // Without an explicit height, some nested screens (Game Profile,
                // App Manager and Build.prop) can leave the lower part unblurred.
                modifier = Modifier
                    .fillMaxHeight()
                    .glassPanel(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topEnd = 28.dp,
                            bottomEnd = 28.dp,
                        ),
                        // Tint digambar setelah blur -> merata di seluruh sheet,
                        // tidak ada bagian yang terlihat transparan.
                        minAlpha = 0.20f,
                        hazeState = drawerHaze,
                        blurBoost = 2.15f,
                        tintScale = 0.62f,
                        tintAfterBlur = true,
                    ),
            ) {
                TweakDrawerContent(
                    selected = selectedSubTab,
                    showRootOnlyItems = privilegeStatus.rootGranted,
                    onlineUserCount = onlineUserCount,
                    onSelect = { tab ->
                        selectedSubTab = tab
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .hazeSource(drawerHaze, zIndex = 1f)
                .padding(contentPadding.withoutBottom())
                // Terminal Root must resize above the software keyboard so the
                // command field and run button remain visible while typing.
                .then(if (selectedSubTab == TweakSubTab.TERMINAL) Modifier.imePadding() else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(

                        if (selectedSubTab == TweakSubTab.GAME_PROFILE ||
                            selectedSubTab == TweakSubTab.GAME_MONITOR ||
                            selectedSubTab == TweakSubTab.GAME_LIBRARY ||
                            selectedSubTab == TweakSubTab.APP_MANAGER ||
                            selectedSubTab == TweakSubTab.BUILD_PROP ||
                            selectedSubTab == TweakSubTab.TERMINAL
                        ) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        },
                    )
                    // Tinggi navbar ditaruh sebagai padding bawah DI DALAM area scroll,
                    // jadi konten bisa tembus ke belakang navbar tanpa strip kosong.
                    .padding(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.lg,
                        bottom = Spacing.lg + contentPadding.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {

                TweakHeader(
                    userId = state.userId,
                    isMembershipActive = state.isMembershipActive,
                    onRetryUserId = viewModel::retryResolveUserIdIfMissing,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    showPowerButton = privilegeStatus.rootGranted,
                    onPowerClick = { selectedSubTab = TweakSubTab.POWER },
                )

                if (selectedSubTab == TweakSubTab.DASHBOARD) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.nav_dashboard),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = str(R.string.tweak_ringkasan_perangkat_game_dan_alat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Long-press sebuah card lalu geser untuk mengubah urutan —
                    // tidak ada lagi tombol "Atur urutan" terpisah maupun panah
                    // naik/turun. reorderable menahan scroll induk selama drag
                    // aktif sehingga geser tidak konflik dengan verticalScroll
                    // di Column pembungkus.
                    val listState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(
                        lazyListState = listState,
                    ) { from, to ->
                        dashboardViewModel.swapCards(from.index, to.index)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 4000.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.section),
                        userScrollEnabled = false,
                    ) {
                        itemsIndexed(dashboardState.cardOrder, key = { _, cardId -> cardId }) { index, cardId ->
                            ReorderableItem(reorderState, key = cardId) { isDragging ->
                                // Elevation/shadow pakai spring (bukan tween) supaya
                                // card terasa terangkat saat ditahan dan kembali elastis
                                // ke tempatnya saat dilepas.
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 20.dp else 0.dp,
                                    animationSpec = spring(
                                        dampingRatio = if (isDragging) {
                                            Spring.DampingRatioLowBouncy
                                        } else {
                                            Spring.DampingRatioMediumBouncy
                                        },
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    label = "cardDragElevation",
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cardEnterAnimation(index = index)
                                        .dragPopEffect(isDragging = isDragging)
                                        .shadow(elevation, MaterialTheme.shapes.medium)
                                        .longPressDraggableHandle(
                                            onDragStopped = {
                                                dashboardViewModel.saveCardOrder()
                                            },
                                        ),
                                ) {
                                    when (cardId) {
                                        "status" -> DeviceStatusSection(
                                            info = dashboardState.deviceInfo,
                                            rootGranted = privilegeStatus.rootGranted,
                                        )
                                        "recent_games" -> RecentlyPlayedGamesSection(
                                            games = dashboardState.recentPlayedGames,
                                            loading = dashboardState.loadingGames,
                                            lastPlayedPackage = dashboardState.lastPlayedPackage,
                                            onGameClick = dashboardViewModel::onGameClick,
                                        )
                                        "game_monitor" -> DashboardGameMonitorSection(
                                            onOpenMonitor = { selectedSubTab = TweakSubTab.GAME_MONITOR },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.V310_CENTER) {
                    V310ControlCenterScreen(
                        onOpenMonitor = { selectedSubTab = TweakSubTab.ROOT_MONITOR },
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.GAME_LIBRARY) {
                    GameLibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                        onConfigureGame = { packageName ->
                            gameProfileViewModel.selectGame(packageName)
                            selectedSubTab = TweakSubTab.GAME_PROFILE
                        },
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.GAME_MONITOR) {
                    GameMonitorScreen(
                        rootGranted = privilegeStatus.rootGranted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.GAME_PROFILE) {
                    GameProfileScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.KERNEL_MANAGER) {

                    KernelManagerSection()
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.ROOT_MONITOR) {

                    RootMonitorSection()
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.APP_MANAGER) {

                    AppManagerScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.BUILD_PROP) {

                    BuildPropScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.POWER) {
                    PowerMenuScreen()
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.KERNEL_TOOLS) {
                    KernelToolsSection()
                    return@Column
                }

                if (selectedSubTab == TweakSubTab.TERMINAL) {
                    TerminalScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    return@Column
                }

                TweakOverviewCard(rootGranted = privilegeStatus.rootGranted)

                // Alat pembersih diletakkan setelah ringkasan, sebelum kontrol
                // agar alur layar dimulai dari status lalu tindakan yang umum.
                RamCleanerCard()

                SectionCard(title = stringResource(R.string.tweak_section_touch), watermarkIcon = Icons.Outlined.TouchApp) {

                    TweakSlider(
                        label = stringResource(R.string.tweak_pointer_speed),
                        description = stringResource(R.string.tweak_pointer_speed_desc),
                        valueText = state.pointerSpeed.toString(),
                        value = state.pointerSpeed.toFloat(),
                        range = -7f..7f,
                        steps = 13,
                        onValueChange = viewModel::onPointerSpeedChange,
                        onValueChangeFinished = { viewModel.onPointerSpeedChangeFinished(activity) },
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_touch_boost),
                        description = stringResource(R.string.tweak_touch_boost_desc),
                        checked = state.touchBoost,
                        onCheckedChange = { checked -> viewModel.onTouchBoostChange(checked, activity) },
                        icon = Icons.Outlined.TouchApp,
                    )
                }

                SectionCard(title = stringResource(R.string.tweak_section_display_game), watermarkIcon = Icons.Outlined.RestartAlt) {
                    TweakSwitch(
                        label = stringResource(R.string.tweak_force_refresh),
                        description = stringResource(R.string.tweak_force_refresh_desc) + " (${state.displayInfo.maxRefreshRate.toInt()}Hz)",
                        checked = state.forceMaxRefreshRate,
                        onCheckedChange = { checked -> viewModel.onForceRefreshChange(checked, activity) },
                        icon = Icons.Outlined.Bolt,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_rotation_lock),
                        description = stringResource(R.string.tweak_rotation_lock_desc),
                        checked = state.rotationLockEnabled,
                        onCheckedChange = { checked -> viewModel.onRotationLockChange(checked, activity) },
                        icon = Icons.Outlined.PhoneAndroid,
                    )
                    TweakSwitch(
                        label = stringResource(R.string.tweak_game_mode),
                        description = stringResource(R.string.tweak_game_mode_desc),
                        checked = state.gameModeEnabled,
                        onCheckedChange = { checked -> viewModel.onGameModeChange(checked, activity) },
                        icon = Icons.Outlined.NotificationsOff,
                    )
                }

                if (privilegeStatus.rootGranted) {
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
                            onOptionSelected = { governor -> viewModel.onCpuGovernorChange(governor, activity) },
                            icon = Icons.Outlined.Speed,
                        )
                        TweakSwitch(
                            label = stringResource(R.string.tweak_ram_priority),
                            description = stringResource(R.string.tweak_ram_priority_desc),
                            checked = state.ramPriorityMode,
                            onCheckedChange = { checked -> viewModel.onRamPriorityModeChange(checked, activity) },
                            icon = Icons.Outlined.Memory,
                        )
                        TweakSwitch(
                            label = stringResource(R.string.tweak_gpu_performance),
                            description = stringResource(R.string.tweak_gpu_performance_desc),
                            checked = state.gpuPerformanceMode,
                            onCheckedChange = { checked -> viewModel.onGpuPerformanceModeChange(checked, activity) },
                            icon = Icons.Outlined.DeveloperBoard,
                        )
                        TweakSwitch(
                            label = stringResource(R.string.tweak_thermal_throttle),
                            description = stringResource(R.string.tweak_thermal_throttle_desc),
                            checked = state.thermalThrottleOverride,
                            onCheckedChange = { checked -> viewModel.onThermalThrottleOverrideChange(checked, activity) },
                            icon = Icons.Outlined.Thermostat,
                        )
                    }

                    SectionCard(title = stringResource(R.string.tweak_section_root_system), watermarkIcon = Icons.Outlined.Terminal) {
                        TweakSwitch(
                            label = stringResource(R.string.tweak_io_scheduler_boost),
                            description = stringResource(R.string.tweak_io_scheduler_boost_desc),
                            checked = state.ioSchedulerBoost,
                            onCheckedChange = { checked -> viewModel.onIoSchedulerBoostChange(checked, activity) },
                            icon = Icons.Outlined.SdStorage,
                        )
                        TweakSwitch(
                            label = stringResource(R.string.tweak_vm_heap_boost),
                            description = stringResource(R.string.tweak_vm_heap_boost_desc),
                            checked = state.vmHeapBoost,
                            onCheckedChange = { checked -> viewModel.onVmHeapBoostChange(checked, activity) },
                            icon = Icons.Outlined.Memory,
                        )

                        TweakSwitch(
                            label = stringResource(R.string.tweak_doze_disabled),
                            description = stringResource(R.string.tweak_doze_disabled_desc),
                            checked = state.dozeDisabled,
                            onCheckedChange = { checked -> viewModel.onDozeDisabledChange(checked, activity) },
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
                    onClick = { viewModel.resetTweaks(activity) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
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

@Composable
private fun cpuGovernorLabel(governor: CpuGovernor): String = when (governor) {
    CpuGovernor.SCHEDUTIL -> stringResource(R.string.tweak_cpu_governor_schedutil)
    CpuGovernor.PERFORMANCE -> stringResource(R.string.tweak_cpu_governor_performance)
    CpuGovernor.ONDEMAND -> stringResource(R.string.tweak_cpu_governor_ondemand)
    CpuGovernor.POWERSAVE -> stringResource(R.string.tweak_cpu_governor_battery)
    CpuGovernor.UNIVERSAL -> stringResource(R.string.tweak_cpu_governor_universal)
}

private enum class TweakSubTab { DASHBOARD, TWEAK, GAME_LIBRARY, GAME_PROFILE, GAME_MONITOR, V310_CENTER, KERNEL_MANAGER, APP_MANAGER, BUILD_PROP, ROOT_MONITOR, POWER, KERNEL_TOOLS, TERMINAL }

@Composable
private fun TweakDrawerContent(
    selected: TweakSubTab,
    showRootOnlyItems: Boolean,
    onlineUserCount: Int,
    onSelect: (TweakSubTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            // Leave enough scrollable room so the floating bottom navbar never
            // covers the last drawer actions on short and low-resolution screens.
            .padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        )
        // Small live presence indicator in the drawer header; it is informational,
        // not another navigation option.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .glassPanel(
                    shape = RoundedCornerShape(16.dp),
                    blurBoost = 1.05f,
                    tintAfterBlur = true,
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = str(R.string.tweak_pengguna_online),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (onlineUserCount >= 0) str(R.string.tweak_sedang_online, onlineUserCount) else str(R.string.tweak_menghubungkan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (onlineUserCount >= 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
        Text(
            text = str(R.string.tweak_navigation_section),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
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
            label = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.nav_tweak), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.tweak_sentuhan_refresh_rate_cpu_gpu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            },
            icon = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
            selected = selected == TweakSubTab.TWEAK,
            onClick = { onSelect(TweakSubTab.TWEAK) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        )
        Text(
            text = str(R.string.tweak_game_monitoring),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        )
        NavigationDrawerItem(
            label = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(str(R.string.game_library_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.tweak_cari_favoritkan_mainkan_dan_buka), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            },
            icon = { Icon(imageVector = Icons.Outlined.VideogameAsset, contentDescription = null) },
            selected = selected == TweakSubTab.GAME_LIBRARY,
            onClick = { onSelect(TweakSubTab.GAME_LIBRARY) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )

        NavigationDrawerItem(
            label = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.nav_game_profile), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(str(R.string.tweak_atur_preset_performa_untuk_tiap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            },
            icon = { Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null) },
            selected = selected == TweakSubTab.GAME_PROFILE,
            onClick = { onSelect(TweakSubTab.GAME_PROFILE) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )

        NavigationDrawerItem(
            label = { Text(str(R.string.tweak_monitor_game)) },
            icon = { Icon(imageVector = Icons.Outlined.MonitorHeart, contentDescription = null) },
            selected = selected == TweakSubTab.GAME_MONITOR,
            onClick = { onSelect(TweakSubTab.GAME_MONITOR) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        )
        Text(
            text = str(R.string.tweak_alat_tambahan),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        )
        NavigationDrawerItem(
            label = { Text(str(R.string.tweak_pusat_kontrol)) },
            icon = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
            selected = selected == TweakSubTab.V310_CENTER,
            onClick = { onSelect(TweakSubTab.V310_CENTER) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )

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
                label = { Text(stringResource(R.string.nav_app_manager)) },
                icon = { Icon(imageVector = Icons.Outlined.Apps, contentDescription = null) },
                selected = selected == TweakSubTab.APP_MANAGER,
                onClick = { onSelect(TweakSubTab.APP_MANAGER) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            NavigationDrawerItem(
                label = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.kernel_manager_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(str(R.string.tweak_frekuensi_dan_governor_sistem_yang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                },
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
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_root_monitor)) },
                icon = { Icon(imageVector = Icons.Outlined.MonitorHeart, contentDescription = null) },
                selected = selected == TweakSubTab.ROOT_MONITOR,
                onClick = { onSelect(TweakSubTab.ROOT_MONITOR) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_kernel_tools)) },
                icon = { Icon(imageVector = Icons.Outlined.Build, contentDescription = null) },
                selected = selected == TweakSubTab.KERNEL_TOOLS,
                onClick = { onSelect(TweakSubTab.KERNEL_TOOLS) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_terminal)) },
                icon = { Icon(imageVector = Icons.Outlined.Terminal, contentDescription = null) },
                selected = selected == TweakSubTab.TERMINAL,
                onClick = { onSelect(TweakSubTab.TERMINAL) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun TweakOverviewCard(rootGranted: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(26.dp), accent = accent)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = str(R.string.tweak_tweak_performance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = str(R.string.tweak_atur_respons_sentuhan_layar_cpu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                text = if (rootGranted) str(R.string.tweak_root_aktif) else str(R.string.tweak_tanpa_root),
                containerColor = if (rootGranted) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (rootGranted) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (rootGranted) {
                    str(R.string.tweak_kontrol_kernel_yang_didukung_perangkat)
                } else {
                    str(R.string.tweak_kontrol_sistem_tertentu_memerlukan_akses)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TweakHeader(
    userId: String?,
    isMembershipActive: Boolean,
    onRetryUserId: () -> Unit,
    onMenuClick: () -> Unit,
    showPowerButton: Boolean,
    onPowerClick: () -> Unit,
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
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                )
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPowerButton) {
            IconButton(onClick = onPowerClick) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = stringResource(R.string.nav_power_menu),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (userId != null) {

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
}
