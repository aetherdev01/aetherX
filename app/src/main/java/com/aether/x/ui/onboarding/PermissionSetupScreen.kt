package com.aether.x.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.permission.RequestFailureReason
import com.aether.x.core.permission.RequestFeedback
import com.aether.x.core.permission.RequestState
import com.aether.x.ui.components.AetherBackground
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.PermissionMethodCard
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentGreenContainer
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.OnAccentBlue
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceCard
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.SurfaceRaised
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

    // v3.1 PURE ROOT: mode "Lewati tanpa akses" (No Root skip) DIHAPUS
    // TOTAL — aplikasi sekarang ketat hanya untuk pengguna root, halaman
    // akses hanya dianggap terpenuhi kalau status.hasAccess (root granted)
    // benar-benar true.
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


    val pagerState = rememberPagerState(pageCount = { pageCount })

    val canContinue = !requireAccessToContinue || pageSatisfied.all { it }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        PrivilegeManager.events.collect { feedback ->
            val message = when (feedback) {
                is RequestFeedback.Granted -> context.getString(R.string.permission_feedback_root_granted)
                is RequestFeedback.Failed -> when (feedback.reason) {
                    RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE -> context.getString(R.string.permission_feedback_root_denied)
                    RequestFailureReason.ROOT_ALREADY_IN_PROGRESS -> context.getString(R.string.permission_feedback_root_in_progress)
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshSupportingPermissions(context)
                PrivilegeManager.refreshAll()
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

    // Auto-advance dinonaktifkan atas permintaan pengguna — sebelumnya
    // pager pindah halaman otomatis 600ms setelah satu izin terpenuhi,
    // tapi perilaku ini menyebabkan bug (macet/animasi tanggung, lihat
    // riwayat komentar di versi sebelumnya). Sekarang navigasi antar
    // halaman sepenuhnya manual: swipe atau tombol "Lanjut" di bawah.

    AetherBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding(),
            ) {
                PermissionHeader(
                    currentPage = pagerState.currentPage,
                    pageCount = pageCount,
                )

                PageDotsIndicator(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    pageSatisfied = pageSatisfied,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
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
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                    ) {
                        when (page) {
                            0 -> AccessMethodPage(status = status, context = context)
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

                val isLastPage = pagerState.currentPage == pageCount - 1
                val currentPageSatisfied = pageSatisfied.getOrNull(pagerState.currentPage) == true
                val nextEnabled = when {
                    !requireAccessToContinue -> true
                    isLastPage -> canContinue
                    else -> currentPageSatisfied
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .aetherCardSurface(shape = RoundedCornerShape(30.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLastPage) "KONFIRMASI AKHIR" else "LANGKAH ${pagerState.currentPage + 1} DARI $pageCount",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                text = if (isLastPage) {
                                    "Pastikan semua akses siap sebelum masuk."
                                } else {
                                    "Selesaikan bagian ini, lalu lanjut ke langkah berikutnya."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            enabled = pagerState.currentPage > 0,
                        ) {
                            Text("Kembali", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (isLastPage) onContinue()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        enabled = nextEnabled,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            contentColor = OnAccentBlue,
                            disabledContainerColor = SurfaceRaised,
                            disabledContentColor = TextMuted,
                        ),
                    ) {
                        Text(
                            text = if (isLastPage) stringResource(R.string.setup_action_continue)
                            else stringResource(R.string.setup_action_next),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    AnimatedVisibility(visible = !nextEnabled, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = if (isLastPage) {
                                stringResource(R.string.setup_locked_final_hint)
                            } else {
                                stringResource(R.string.setup_locked_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessMethodPage(
    status: com.aether.x.core.permission.PrivilegeStatus,
    context: android.content.Context,
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

        // v3.1 PURE ROOT: battery optimization tetap relevan — ROM agresif
        // (MIUI dkk) bisa membunuh daemon root shell di background pada
        // sebagian perangkat. Murni informatif + tombol ke dialog sistem
        // resmi, tidak memaksa apa pun.
        var ignoringBatteryOptimizations by remember {
            mutableStateOf(PrivilegeManager.isIgnoringBatteryOptimizations(context))
        }
        val batteryLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(batteryLifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    ignoringBatteryOptimizations = PrivilegeManager.isIgnoringBatteryOptimizations(context)
                }
            }
            batteryLifecycleOwner.lifecycle.addObserver(observer)
            onDispose { batteryLifecycleOwner.lifecycle.removeObserver(observer) }
        }
        AnimatedVisibility(
            visible = !ignoringBatteryOptimizations,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BatteryOptimizationBanner(
                onRequestExemption = { PrivilegeManager.requestIgnoreBatteryOptimizations(context) },
            )
        }

        PermissionMethodCard(
            title = stringResource(R.string.setup_method_root),
            description = stringResource(R.string.setup_method_root_desc),
            statusText = when {
                status.checkingRoot -> stringResource(R.string.setup_status_checking)
                status.rootGranted -> stringResource(R.string.setup_status_granted)
                else -> stringResource(R.string.setup_status_not_granted)
            },
            granted = status.rootGranted,
            actionLabel = stringResource(R.string.setup_action_request),
            onAction = { PrivilegeManager.requestRoot(context) },
            isRequesting = status.rootRequestState == RequestState.REQUESTING,
            requestingLabel = stringResource(R.string.setup_action_requesting),
        )
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
            .aetherCardSurface(shape = MaterialTheme.shapes.medium)
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
            .aetherCardSurface(shape = RoundedCornerShape(20.dp), accent = AccentRed)
            .clickable { onAcknowledgedChange(!acknowledged) }
            .padding(horizontal = 10.dp, vertical = Spacing.sm),
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
            .padding(top = 8.dp)
            .size(64.dp)
            .aetherCardSurface(shape = RoundedCornerShape(23.dp), accent = tint)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .aetherCardSurface(shape = RoundedCornerShape(25.dp))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun PermissionHeader(currentPage: Int, pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .aetherCardSurface(shape = RoundedCornerShape(27.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .aetherCardSurface(shape = RoundedCornerShape(17.dp), accent = AccentBlue)
                .padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(27.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "A E T H E R X",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .aetherCardSurface(shape = RoundedCornerShape(15.dp), accent = AccentBlue)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${currentPage + 1}/$pageCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AccentBlue,
            )
        }
    }
}

@Composable
private fun PageDotsIndicator(
    pageCount: Int,
    currentPage: Int,
    pageSatisfied: List<Boolean>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LANGKAH ${currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${pageSatisfied.count { it }} DARI $pageCount SIAP",
                style = MaterialTheme.typography.labelSmall,
                color = if (pageSatisfied.all { it }) AccentGreen else TextSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (index in 0 until pageCount) {
                val satisfied = pageSatisfied.getOrNull(index) == true
                val isCurrent = index == currentPage
                Box(
                    modifier = Modifier
                        .weight(if (isCurrent) 1.7f else 1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                satisfied -> AccentGreen
                                isCurrent -> AccentBlue
                                else -> StrokeSubtle
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReadinessBanner(canContinue: Boolean, notReadyText: String = stringResource(R.string.setup_required_banner)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(9.dp).clip(CircleShape).background(if (canContinue) AccentGreen else AccentRed),
        )
        Text(
            text = if (canContinue) stringResource(R.string.setup_ready_hint) else notReadyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (canContinue) AccentGreen else TextSecondary,
        )
    }
}

/**
 * Banner peringatan murni informatif (bukan blocker — pengguna tetap bisa
 * lanjut tanpa mengizinkan ini) yang muncul kalau AetherX MASIH kena
 * battery optimization sistem. Relevan untuk ROM agresif seperti MIUI yang
 * bisa membunuh daemon root shell AetherX di background kalau belum
 * dikecualikan dari battery saver.
 */
@Composable
private fun BatteryOptimizationBanner(onRequestExemption: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.BatteryAlert,
                contentDescription = null,
                tint = AccentRed,
            )
            Text(
                text = stringResource(R.string.setup_battery_optimization_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.setup_battery_optimization_desc),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        TextButton(
            onClick = onRequestExemption,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text = stringResource(R.string.setup_battery_optimization_action),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
