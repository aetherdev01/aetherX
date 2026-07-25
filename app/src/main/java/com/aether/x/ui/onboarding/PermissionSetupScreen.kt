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
import androidx.compose.material.icons.outlined.BatteryAlert
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.permission.RequestFailureReason
import com.aether.x.core.permission.RequestFeedback
import com.aether.x.core.permission.RequestState
import com.aether.x.ui.components.ShizukuCard
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
                    PrivilegeBackend.SHIZUKU -> context.getString(R.string.permission_feedback_shizuku_granted)
                    PrivilegeBackend.ROOT -> context.getString(R.string.permission_feedback_root_granted)
                    PrivilegeBackend.NONE -> null
                }
                is RequestFeedback.Failed -> when (feedback.reason) {
                    RequestFailureReason.SHIZUKU_NOT_INSTALLED -> context.getString(R.string.permission_feedback_shizuku_not_installed)
                    RequestFailureReason.SHIZUKU_SERVICE_NOT_RUNNING -> context.getString(R.string.permission_feedback_shizuku_service_not_running)
                    RequestFailureReason.SHIZUKU_PERMISSION_DENIED -> context.getString(R.string.permission_feedback_shizuku_permission_denied)
                    RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE -> context.getString(R.string.permission_feedback_root_denied)
                    RequestFailureReason.ROOT_ALREADY_IN_PROGRESS -> context.getString(R.string.permission_feedback_root_in_progress)
                }
            }
            message?.let { snackbarHostState.showSnackbar(it) }
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

    LaunchedEffect(status.shizukuGranted, status.rootGranted) {
        PrivilegeManager.adoptExistingGrantIfNoPreference(context)
    }

    // BUG FIX — "animasi slide macet di tengah / layar berikutnya cuma
    // setengah" setelah mengaktifkan izin (Shizuku, overlay, dll).
    //
    // Penyebab #1: key LaunchedEffect auto-advance SEBELUMNYA menyertakan
    // `pagerState.currentPage`. Dokumentasi resmi Compose Foundation
    // mendefinisikan `currentPage` sebagai "halaman TERDEKAT ke posisi
    // snap" — nilainya BISA BERUBAH DI TENGAH ANIMASI, begitu posisi
    // scroll melewati titik tengah antar halaman, BUKAN cuma di akhir
    // animasi. Karena nilai yang berubah itu ada di dalam key,
    // LaunchedEffect restart di tengah animasi, MEMBATALKAN coroutine
    // `animateScrollToPage` sebelum sempat selesai — pager berhenti di
    // posisi transisi (parsial) alih-alih tuntas ke halaman berikutnya.
    // Fix: pakai `settledPage`, properti yang SENGAJA didesain Compose
    // Foundation supaya nilainya TETAP selama animasi berlangsung, hanya
    // berubah SETELAH benar-benar tuntas (settle).
    //
    // Detail implementasi: closure `derivedStateOf` di bawah membaca
    // `status` (StateFlow reaktif) langsung, BUKAN variabel lokal
    // `pageSatisfied` yang dideklarasikan di atas — closure di dalam
    // `remember { }` tanpa key hanya dibuat SEKALI seumur hidup composable
    // ini, jadi kalau closure itu menangkap `pageSatisfied` (List biasa
    // yang dibuat ulang tiap recomposition, bukan State), closure tersebut
    // akan selamanya merujuk ke List versi pertama kali remember
    // dieksekusi dan tidak pernah ter-update lagi.
    val autoAdvanceTrigger by remember {
        derivedStateOf {
            val currentStatus = status
            val satisfiedList = listOf(
                currentStatus.hasAccess,
                currentStatus.writeSettingsGranted,
                currentStatus.overlayGranted,
                currentStatus.notificationsGranted,
                kernelWarningAcknowledged,
            )
            val page = pagerState.settledPage
            val satisfied = satisfiedList.getOrNull(page) == true
            val hasNextPage = page < satisfiedList.size - 1
            if (satisfied && hasNextPage) page else -1
        }
    }
    LaunchedEffect(autoAdvanceTrigger) {
        if (!requireAccessToContinue) return@LaunchedEffect
        val targetPage = autoAdvanceTrigger
        if (targetPage < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        // Cek ulang setelah delay: batalkan auto-advance kalau kondisinya
        // sudah berubah lagi selama jeda 600ms itu (mis. pengguna sudah
        // pindah halaman manual, atau justru izin barusan dicabut lagi).
        if (autoAdvanceTrigger == targetPage) {
            pagerState.animateScrollToPage(targetPage + 1)
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

        val shizukuLocked = status.preferredBackend == PrivilegeBackend.ROOT
        val rootLocked = status.preferredBackend == PrivilegeBackend.SHIZUKU

        // ROLLBACK — status Shizuku di-refresh setiap layar ini kembali ke
        // foreground (mis. pengguna baru kembali dari app Shizuku Manager
        // setelah start service/pairing di sana). Lihat KDoc
        // [com.aether.x.core.shizuku.ShizukuManager.refresh].
        val shizukuLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(shizukuLifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    PrivilegeManager.refreshShizuku()
                }
            }
            shizukuLifecycleOwner.lifecycle.addObserver(observer)
            onDispose { shizukuLifecycleOwner.lifecycle.removeObserver(observer) }
        }

        ShizukuCard(
            state = status.shizukuState,
            locked = shizukuLocked,
            lockedHint = stringResource(R.string.setup_locked_by_root_hint),
            onOpenShizukuManager = { PrivilegeManager.openShizukuManager(context) },
            onRequestPermission = { PrivilegeManager.requestShizukuPermission() },
            onRefresh = { PrivilegeManager.refreshShizuku() },
        )

        // Battery optimization tetap relevan untuk Shizuku: ROM agresif
        // (MIUI dkk) bisa membunuh proses app Shizuku Manager di background
        // walau service-nya sedang dipakai, memutus binder AetherX secara
        // tiba-tiba. Sama seperti sebelumnya, murni informatif + tombol ke
        // dialog sistem resmi, tidak memaksa apa pun.
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
            visible = !shizukuLocked && !ignoringBatteryOptimizations,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BatteryOptimizationBanner(
                onRequestExemption = { PrivilegeManager.requestIgnoreBatteryOptimizations(context) },
            )
        }

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

/**
 * Banner peringatan murni informatif (bukan blocker — pengguna tetap bisa
 * lanjut tanpa mengizinkan ini) yang muncul kalau AetherX MASIH kena
 * battery optimization sistem. Relevan untuk ROM agresif seperti MIUI yang
 * bisa membunuh proses app Shizuku Manager di background (memutus binder
 * Shizuku secara tiba-tiba) kalau belum dikecualikan dari battery saver.
 *
 * NOTE untuk asw: tambahkan string berikut ke strings.xml (belum ada di
 * source yang di-share, jadi placeholder Indonesia berikut dipakai
 * langsung apa adanya di kode sampai dipindah ke resource):
 * - setup_battery_optimization_title
 * - setup_battery_optimization_desc
 * - setup_battery_optimization_action
 */
@Composable
private fun BatteryOptimizationBanner(onRequestExemption: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardAlt)
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
                text = "Optimasi baterai masih membatasi AetherX",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Di sebagian perangkat (MIUI, ColorOS, dll), ini bisa membuat pairing wireless gagal terhubung setelah kode dimasukkan, walau Wireless debugging tetap aktif. Izinkan AetherX berjalan tanpa batasan supaya pairing lebih stabil.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        TextButton(
            onClick = onRequestExemption,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = "Izinkan", fontWeight = FontWeight.SemiBold)
        }
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
