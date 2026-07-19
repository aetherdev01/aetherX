package com.aether.x.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R
import com.aether.x.core.adb.AdbConnectionState
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.permission.RequestFailureReason
import com.aether.x.core.permission.RequestFeedback
import com.aether.x.core.permission.RequestState
import com.aether.x.ui.components.AdbPairingCard
import com.aether.x.ui.components.PermissionMethodCard
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentGreenContainer
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.OnAccentBlue
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
) {
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val accessSatisfied = status.hasAccess
    val writeSettingsSatisfied = status.writeSettingsGranted
    val overlaySatisfied = status.overlayGranted
    val notificationsSatisfied = status.notificationsGranted

    var kernelWarningAcknowledged by remember { mutableStateOf(false) }

    val pageSatisfied = listOf(
        accessSatisfied,
        writeSettingsSatisfied,
        overlaySatisfied,
        notificationsSatisfied,
        kernelWarningAcknowledged,
    )
    val pageCount = pageSatisfied.size

    val allSatisfiedOnEntry = requireAccessToContinue &&
        (accessSatisfied && writeSettingsSatisfied && overlaySatisfied && notificationsSatisfied)

    val pagerState = rememberPagerState(pageCount = { pageCount })

    val canContinue = !requireAccessToContinue || pageSatisfied.all { it }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        PrivilegeManager.events.collect { feedback ->
            val message = when (feedback) {
                is RequestFeedback.Granted -> when (feedback.backend) {
                    PrivilegeBackend.ADB -> context.getString(R.string.permission_feedback_adb_granted)
                    PrivilegeBackend.ROOT -> context.getString(R.string.permission_feedback_root_granted)
                    PrivilegeBackend.NONE -> null
                }
                is RequestFeedback.Failed -> when (feedback.reason) {
                    RequestFailureReason.ADB_WIRELESS_DEBUGGING_OFF -> context.getString(R.string.permission_feedback_adb_wireless_debugging_off)
                    RequestFailureReason.ADB_AUTO_DISCOVERY_TIMEOUT -> context.getString(R.string.permission_feedback_adb_auto_discovery_timeout)
                    RequestFailureReason.ADB_PAIRING_CODE_INVALID_OR_EXPIRED -> context.getString(R.string.permission_feedback_adb_pairing_invalid)
                    RequestFailureReason.ADB_HOST_UNREACHABLE -> context.getString(R.string.permission_feedback_adb_host_unreachable)
                    RequestFailureReason.ADB_CONNECT_AFTER_PAIRING_FAILED -> context.getString(R.string.permission_feedback_adb_connect_after_pairing_failed)
                    RequestFailureReason.ADB_SHELL_REJECTED_NEEDS_REPAIR -> context.getString(R.string.permission_feedback_adb_shell_rejected)
                    RequestFailureReason.ADB_UNKNOWN -> context.getString(R.string.permission_feedback_adb_unknown)
                    RequestFailureReason.ADB_ALREADY_IN_PROGRESS -> context.getString(R.string.permission_feedback_adb_in_progress)
                    RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE -> context.getString(R.string.permission_feedback_root_denied)
                    RequestFailureReason.ROOT_ALREADY_IN_PROGRESS -> context.getString(R.string.permission_feedback_root_in_progress)
                }
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    var pendingAutoPairAfterNotificationGrant by remember { mutableStateOf(false) }
    var pendingReconnectAfterNotificationGrant by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PrivilegeManager.refreshSupportingPermissions(context)
        if (granted && pendingAutoPairAfterNotificationGrant) {
            PrivilegeManager.startAutoPairAdb(context)
        }
        if (granted && pendingReconnectAfterNotificationGrant) {
            PrivilegeManager.reconnectAdb(context)
        }
        pendingAutoPairAfterNotificationGrant = false
        pendingReconnectAfterNotificationGrant = false
    }

    val startAutoPairingGuarded: () -> Unit = {
        if (status.notificationsGranted) {
            PrivilegeManager.startAutoPairAdb(context)
        } else {
            pendingAutoPairAfterNotificationGrant = true
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val reconnectAdbGuarded: () -> Unit = {
        if (status.notificationsGranted) {
            PrivilegeManager.reconnectAdb(context)
        } else {
            pendingReconnectAfterNotificationGrant = true
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshSupportingPermissions(context)
                PrivilegeManager.refreshAll()
                PrivilegeManager.adoptExistingGrantIfNoPreference(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    LaunchedEffect(Unit) {
        if (requireAccessToContinue && !status.notificationsGranted &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(status.adbGranted, status.rootGranted) {
        PrivilegeManager.adoptExistingGrantIfNoPreference(context)
    }

    LaunchedEffect(pageSatisfied, pagerState.currentPage) {
        if (!requireAccessToContinue) return@LaunchedEffect
        val currentSatisfied = pageSatisfied.getOrNull(pagerState.currentPage) == true
        val hasNextPage = pagerState.currentPage < pageCount - 1
        if (currentSatisfied && hasNextPage) {
            kotlinx.coroutines.delay(600)
            if (pageSatisfied.getOrNull(pagerState.currentPage) == true) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Scaffold(
        containerColor = BgVoid,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding),
        ) {
            PermissionHeader()

            PageDotsIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                pageSatisfied = pageSatisfied,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),

                userScrollEnabled = true,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xl)
                        .padding(bottom = Spacing.xxl),
                ) {
                    when (page) {
                        0 -> AccessMethodPage(
                            status = status,
                            context = context,
                            onStartAutoPairing = startAutoPairingGuarded,
                            onReconnect = reconnectAdbGuarded,
                        )
                        1 -> WriteSettingsPage(status = status, context = context)
                        2 -> OverlayPage(status = status, context = context)
                        3 -> NotificationsPage(
                            status = status,
                            onRequestNotifications = {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                        4 -> KernelWarningPage(
                            acknowledged = kernelWarningAcknowledged,
                            onAcknowledgedChange = { kernelWarningAcknowledged = it },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCardAlt)
                    .padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val isLastPage = pagerState.currentPage == pageCount - 1
                val currentPageSatisfied = pageSatisfied.getOrNull(pagerState.currentPage) == true

                val nextEnabled = !requireAccessToContinue || currentPageSatisfied

                Button(
                    onClick = {
                        if (isLastPage) {
                            onContinue()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = nextEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = OnAccentBlue,
                        disabledContainerColor = StrokeSubtle,
                        disabledContentColor = TextMuted,
                    ),
                ) {
                    Text(
                        text = if (isLastPage) {
                            stringResource(R.string.setup_action_continue)
                        } else {
                            stringResource(R.string.setup_action_next)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                AnimatedVisibility(visible = !nextEnabled, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = stringResource(R.string.setup_locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessMethodPage(
    status: com.aether.x.core.permission.PrivilegeStatus,
    context: android.content.Context,
    onStartAutoPairing: () -> Unit,
    onReconnect: () -> Unit,
) {
    PageIcon(icon = Icons.Outlined.Shield, tint = AccentBlue)
    PageTitle(
        title = stringResource(R.string.setup_page_access_title),
        subtitle = stringResource(R.string.setup_page_access_subtitle),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))

    ReadinessBanner(canContinue = status.hasAccess)
    Spacer(modifier = Modifier.height(Spacing.lg))

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {

        val adbLocked = status.preferredBackend == PrivilegeBackend.ROOT
        val rootLocked = status.preferredBackend == PrivilegeBackend.ADB

        AdbPairingCard(
            connected = status.adbGranted && !adbLocked,

            paired = status.adbState.let {
                it != AdbConnectionState.NotPaired &&
                    it !is AdbConnectionState.SearchingForPairing &&
                    it !is AdbConnectionState.PairingFound
            },
            isBusy = status.adbRequestState == RequestState.REQUESTING,
            locked = adbLocked,
            lockedHint = stringResource(R.string.setup_locked_by_root_hint),
            onOpenWirelessDebugging = { PrivilegeManager.openWirelessDebuggingSettings(context) },
            onStartAutoPairing = onStartAutoPairing,
            onReconnect = onReconnect,
            onForget = { PrivilegeManager.forgetAdbPairing() },
        )

        if (!status.hasAccess) {
            OrDivider()
        }

        PermissionMethodCard(
            title = stringResource(R.string.setup_method_root),
            description = stringResource(R.string.setup_method_root_desc),
            statusText = when {
                status.checkingRoot -> stringResource(R.string.setup_status_checking)
                status.rootGranted -> stringResource(R.string.setup_status_granted)
                else -> stringResource(R.string.setup_status_not_granted)
            },
            granted = status.rootGranted && !rootLocked,
            locked = rootLocked,
            lockedHint = stringResource(R.string.setup_locked_by_adb_hint),
            actionLabel = stringResource(R.string.setup_action_request),
            onAction = { PrivilegeManager.requestRoot(context) },
            isRequesting = status.rootRequestState == RequestState.REQUESTING,
            requestingLabel = stringResource(R.string.setup_action_requesting),
        )

        AnimatedVisibility(
            visible = status.preferredBackend != PrivilegeBackend.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.setup_switch_method),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .clickable { PrivilegeManager.clearBackendPreference(context) },
                )
            }
        }
    }
}

@Composable
private fun WriteSettingsPage(
    status: com.aether.x.core.permission.PrivilegeStatus,
    context: android.content.Context,
) {
    PageIcon(icon = Icons.Outlined.Tune, tint = AccentBlue)
    PageTitle(
        title = stringResource(R.string.setup_page_write_settings_title),
        subtitle = stringResource(R.string.setup_page_write_settings_subtitle),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))
    ReadinessBanner(
        canContinue = status.writeSettingsGranted,
        notReadyText = stringResource(R.string.setup_page_write_settings_banner),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))

    PermissionMethodCard(
        title = stringResource(R.string.setup_method_write_settings),
        description = stringResource(R.string.setup_method_write_settings_desc),
        statusText = if (status.writeSettingsGranted) {
            stringResource(R.string.setup_status_granted)
        } else {
            stringResource(R.string.setup_status_not_granted)
        },
        granted = status.writeSettingsGranted,
        actionLabel = stringResource(R.string.setup_action_request),
        onAction = { PrivilegeManager.requestWriteSettings(context) },
    )
}

@Composable
private fun OverlayPage(
    status: com.aether.x.core.permission.PrivilegeStatus,
    context: android.content.Context,
) {
    PageIcon(icon = Icons.Outlined.Layers, tint = AccentBlue)
    PageTitle(
        title = stringResource(R.string.setup_page_overlay_title),
        subtitle = stringResource(R.string.setup_page_overlay_subtitle),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))
    ReadinessBanner(
        canContinue = status.overlayGranted,
        notReadyText = stringResource(R.string.setup_page_overlay_banner),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))

    PermissionMethodCard(
        title = stringResource(R.string.setup_method_overlay),
        description = stringResource(R.string.setup_method_overlay_desc),
        statusText = if (status.overlayGranted) {
            stringResource(R.string.setup_status_granted)
        } else {
            stringResource(R.string.setup_status_not_granted)
        },
        granted = status.overlayGranted,
        actionLabel = stringResource(R.string.setup_action_request),
        onAction = { PrivilegeManager.requestOverlayPermission(context) },
    )
}

@Composable
private fun NotificationsPage(
    status: com.aether.x.core.permission.PrivilegeStatus,
    onRequestNotifications: () -> Unit,
) {
    PageIcon(icon = Icons.Outlined.NotificationsActive, tint = AccentBlue)
    PageTitle(
        title = stringResource(R.string.setup_page_notifications_title),
        subtitle = stringResource(R.string.setup_page_notifications_subtitle),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))
    ReadinessBanner(
        canContinue = status.notificationsGranted,
        notReadyText = stringResource(R.string.setup_page_notifications_banner),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))

    PermissionMethodCard(
        title = stringResource(R.string.setup_method_notifications),
        description = stringResource(R.string.setup_method_notifications_desc),
        statusText = if (status.notificationsGranted) {
            stringResource(R.string.setup_status_granted)
        } else {
            stringResource(R.string.setup_status_not_granted)
        },
        granted = status.notificationsGranted,
        actionLabel = stringResource(R.string.setup_action_request),
        onAction = onRequestNotifications,
    )
}

@Composable
private fun KernelWarningPage(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
) {
    PageIcon(icon = Icons.Outlined.WarningAmber, tint = AccentRed)
    PageTitle(
        title = stringResource(R.string.setup_page_kernel_warning_title),
        subtitle = stringResource(R.string.setup_page_kernel_warning_subtitle),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardAlt)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.setup_kernel_warning_point_governor),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.setup_kernel_warning_point_thermal),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.setup_kernel_warning_point_responsibility),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }

    Spacer(modifier = Modifier.height(Spacing.lg))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAcknowledgedChange(!acknowledged) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = acknowledged,
            onCheckedChange = onAcknowledgedChange,
            colors = CheckboxDefaults.colors(checkedColor = AccentBlue),
        )
        Text(
            text = stringResource(R.string.setup_kernel_warning_acknowledge),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun PageIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .padding(top = Spacing.md)
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCardAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun PermissionHeader() {
    Column(
        modifier = Modifier
            .padding(top = Spacing.md)
            .padding(horizontal = Spacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
    }
}

@Composable
private fun PageDotsIndicator(
    pageCount: Int,
    currentPage: Int,
    pageSatisfied: List<Boolean>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until pageCount) {
            val satisfied = pageSatisfied.getOrNull(index) == true
            val isCurrent = index == currentPage
            val color = when {
                satisfied -> AccentGreen
                isCurrent -> AccentBlue
                else -> StrokeSubtle
            }
            Icon(
                imageVector = if (isCurrent || satisfied) Icons.Filled.Circle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(if (isCurrent) 10.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun ReadinessBanner(canContinue: Boolean, notReadyText: String = stringResource(R.string.setup_required_banner)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (canContinue) AccentGreenContainer else SurfaceCardAlt)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (canContinue) AccentGreen else AccentRed),
        )
        Text(
            text = if (canContinue) stringResource(R.string.setup_ready_hint) else notReadyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (canContinue) AccentGreen else TextSecondary,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.setup_or_divider),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
        )
    }
}
