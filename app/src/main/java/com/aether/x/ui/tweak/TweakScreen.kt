package com.aether.x.ui.tweak

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.CpuGovernor
import com.aether.x.ui.appmanager.AppManagerScreen

import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.TweakDropdown
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.components.TweakSwitch
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.dashboard.AetherXInfoCard
import com.aether.x.ui.dashboard.GameActivitySection
import com.aether.x.ui.dashboard.DashboardViewModel
import com.aether.x.ui.dashboard.DeviceInfoSection
import com.aether.x.ui.dashboard.RamCleanerCard
import com.aether.x.ui.monitor.RootMonitorSection
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

    val dashboardViewModel: DashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity

    var selectedSubTab by remember { mutableStateOf(TweakSubTab.DASHBOARD) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDetectedGames()

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

    LaunchedEffect(privilegeStatus.rootGranted) {
        val needsRoot = selectedSubTab == TweakSubTab.KERNEL_MANAGER ||
            selectedSubTab == TweakSubTab.BUILD_PROP ||
            selectedSubTab == TweakSubTab.ROOT_MONITOR ||
            selectedSubTab == TweakSubTab.APP_MANAGER
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
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                TweakDrawerContent(
                    selected = selectedSubTab,
                    showRootOnlyItems = privilegeStatus.rootGranted,
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

                TweakHeader(
                    userId = state.userId,
                    isMembershipActive = state.isMembershipActive,
                    onRetryUserId = viewModel::retryResolveUserIdIfMissing,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                )

                if (selectedSubTab == TweakSubTab.DASHBOARD) {
                    var editingOrder by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        androidx.compose.material3.TextButton(onClick = { editingOrder = !editingOrder }) {
                            Text(
                                text = if (editingOrder) {
                                    stringResource(R.string.dashboard_order_done)
                                } else {
                                    stringResource(R.string.dashboard_order_edit)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    dashboardState.cardOrder.forEachIndexed { index, cardId ->
                        Row(
                            modifier = Modifier.fillMaxWidth().cardEnterAnimation(index = index),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (cardId) {
                                    "info" -> AetherXInfoCard()
                                    "activity" -> GameActivitySection(
                                        games = dashboardState.installedGames,
                                        loading = dashboardState.loadingGames,
                                        lastPlayedPackage = dashboardState.lastPlayedPackage,
                                        onGameClick = dashboardViewModel::onGameClick,
                                    )
                                    "device" -> DeviceInfoSection(info = dashboardState.deviceInfo)
                                    "ram" -> RamCleanerCard()
                                }
                            }
                            if (editingOrder) {
                                Column {
                                    IconButton(
                                        onClick = { dashboardViewModel.moveCard(cardId, -1) },
                                        enabled = index != 0,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.dashboard_order_move_up_cd),
                                        )
                                    }
                                    IconButton(
                                        onClick = { dashboardViewModel.moveCard(cardId, 1) },
                                        enabled = index != dashboardState.cardOrder.lastIndex,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.dashboard_order_move_down_cd),
                                        )
                                    }
                                }
                            }
                        }
                    }
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

                SectionCard(title = stringResource(R.string.tweak_section_refresh), watermarkIcon = Icons.Outlined.RestartAlt) {
                    TweakSwitch(
                        label = stringResource(R.string.tweak_force_refresh),
                        description = stringResource(
                            R.string.tweak_force_refresh_desc,
                        ) + " (${state.displayInfo.maxRefreshRate.toInt()}Hz)",
                        checked = state.forceMaxRefreshRate,
                        onCheckedChange = { checked -> viewModel.onForceRefreshChange(checked, activity) },
                        icon = Icons.Outlined.Bolt,
                    )
                }

                SectionCard(title = stringResource(R.string.tweak_section_game_mode), watermarkIcon = Icons.Outlined.NotificationsOff) {
                    TweakSwitch(
                        label = stringResource(R.string.tweak_game_mode),
                        description = stringResource(R.string.tweak_game_mode_desc),
                        checked = state.gameModeEnabled,
                        onCheckedChange = { checked -> viewModel.onGameModeChange(checked, activity) },
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

private enum class TweakSubTab { DASHBOARD, TWEAK, GAME_PROFILE, KERNEL_MANAGER, APP_MANAGER, BUILD_PROP, ROOT_MONITOR }

private const val GAME_BOOSTER_DRAWER_LOCKED = true

private val LOCKED_ITEM_NO_OP_CLICK: () -> Unit = {}

@Composable
private fun TweakDrawerContent(
    selected: TweakSubTab,
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

    NavigationDrawerItem(
        label = { Text(stringResource(R.string.game_booster_title)) },
        icon = { Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null) },
        badge = {

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
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_root_monitor)) },
            icon = { Icon(imageVector = Icons.Outlined.MonitorHeart, contentDescription = null) },
            selected = selected == TweakSubTab.ROOT_MONITOR,
            onClick = { onSelect(TweakSubTab.ROOT_MONITOR) },
            colors = NavigationDrawerItemDefaults.colors(),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun TweakHeader(
    userId: String?,
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
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
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
