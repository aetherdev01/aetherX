package com.aether.x.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.ui.components.LiquidButton
import com.aether.x.R
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.permission.RequestFailureReason
import com.aether.x.core.permission.RequestFeedback
import com.aether.x.ui.components.AetherBackground
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.components.LiquidToggle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Setup stays non-blocking: users can enter AetherX without root, while root-only
 * controls remain unavailable until the permission manager grants access.
 * Surfaces intentionally use the shared Liquid Glass implementation so blur,
 * tint and glass strength follow AetherXTheme settings.
 *
 * [onAllComplete] (opsional): dipanggil otomatis ±0,9 dtk setelah SEMUA pemeriksaan
 * (root + 3 izin pendukung) selesai — dipakai onboarding untuk langsung pindah ke
 * halaman 2. Dibiarkan null di layar Kelola Akses supaya tidak menutup sendiri.
 */
@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
    onAllComplete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val isEnglish = remember(context) { LocaleManager.current(context) == AppLanguage.ENGLISH }
    val snackbarHostState = remember { SnackbarHostState() }
    val appPreferences = remember(context.applicationContext) { AetherXPreferences(context.applicationContext) }
    val savedPrefs by appPreferences.preferences.collectAsStateWithLifecycle(initialValue = com.aether.x.data.AppPreferences())
    val setupScope = rememberCoroutineScope()

    var ignoringBatteryOptimizations by remember {
        mutableStateOf(PrivilegeManager.isIgnoringBatteryOptimizations(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    LaunchedEffect(Unit) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    LaunchedEffect(Unit) {
        PrivilegeManager.events.collect { feedback ->
            val message = when (feedback) {
                is RequestFeedback.Granted -> context.getString(R.string.permission_feedback_root_granted)
                is RequestFeedback.Failed -> when (feedback.reason) {
                    RequestFailureReason.ROOT_DENIED_OR_UNAVAILABLE ->
                        context.getString(R.string.permission_feedback_root_denied)
                    RequestFailureReason.ROOT_ALREADY_IN_PROGRESS ->
                        context.getString(R.string.permission_feedback_root_in_progress)
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshSupportingPermissions(context)
                PrivilegeManager.refreshAll()
                ignoringBatteryOptimizations = PrivilegeManager.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rootStatusText = when {
        status.checkingRoot -> if (isEnglish) "Checking access" else "Memeriksa akses"
        status.rootGranted -> stringResource(R.string.setup_status_granted)
        else -> stringResource(R.string.setup_status_not_granted)
    }
    val supportingGranted = listOf(
        status.writeSettingsGranted,
        status.overlayGranted,
        status.notificationsGranted,
    ).count { it }
    val completedItems = supportingGranted + if (status.rootGranted) 1 else 0

    // Semua pemeriksaan selesai → jeda singkat agar user sempat melihat 100%, lalu
    // otomatis pindah ke halaman 2. Efek dibatalkan sendiri bila status berbalik.
    val allComplete = status.rootGranted && supportingGranted == 3
    val latestOnAllComplete by rememberUpdatedState(onAllComplete)
    LaunchedEffect(allComplete) {
        if (allComplete && latestOnAllComplete != null) {
            delay(900)
            latestOnAllComplete?.invoke()
        }
    }

    AetherBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                SetupBottomBar(
                    isEnglish = isEnglish,
                    rootGranted = status.rootGranted,
                    requireAccessToContinue = requireAccessToContinue,
                    onContinue = onContinue,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .statusBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SetupIntro(isEnglish = isEnglish, rootGranted = status.rootGranted)

                    Column(
                        modifier = Modifier.fillMaxWidth().aetherCardSurface(shape = RoundedCornerShape(24.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (isEnglish) "Low-end phone mode" else "Mode ponsel low-end", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(if (isEnglish) "Use lighter glass, less blur and reduced motion to help keep the UI responsive." else "Gunakan glass lebih ringan, blur rendah, dan animasi minimal agar UI lebih responsif.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LiquidToggle(
                                checked = savedPrefs.lowEndMode,
                                onCheckedChange = { enabled -> setupScope.launch { appPreferences.setLowEndMode(enabled) } },
                            )
                        }
                        Text(if (isEnglish) "You can change this later in Settings → Liquid Glass & Performance." else "Bisa diubah lagi di Pengaturan → Liquid Glass & Performa.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    SetupProgressCard(
                        isEnglish = isEnglish,
                        completed = completedItems,
                        total = 4,
                        rootGranted = status.rootGranted,
                        supportingGranted = supportingGranted,
                    )

                    RootAccessCard(
                        isEnglish = isEnglish,
                        rootGranted = status.rootGranted,
                        checkingRoot = status.checkingRoot,
                        rootStatusText = rootStatusText,
                        onRequestRoot = { PrivilegeManager.requestRoot(context) },
                        onRefreshRoot = { PrivilegeManager.refreshAll() },
                    )

                    if (!status.rootGranted) RestrictedModeCard(isEnglish = isEnglish)

                    SupportingPermissionsSection(
                        isEnglish = isEnglish,
                        writeSettingsGranted = status.writeSettingsGranted,
                        overlayGranted = status.overlayGranted,
                        notificationsGranted = status.notificationsGranted,
                        onRequestWriteSettings = { PrivilegeManager.requestWriteSettings(context) },
                        onRequestOverlay = { PrivilegeManager.requestOverlayPermission(context) },
                        onRequestNotifications = {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )

                    if (!ignoringBatteryOptimizations) {
                        BatteryOptimizationNote(
                            isEnglish = isEnglish,
                            onRequestExemption = {
                                PrivilegeManager.requestIgnoreBatteryOptimizations(context)
                            },
                        )
                    }

                    if (status.rootGranted) SafetyNote(isEnglish = isEnglish)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupIntro(isEnglish: Boolean, rootGranted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(28.dp))
            .padding(start = 19.dp, end = 19.dp, top = 20.dp, bottom = 19.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(colors.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "AETHERX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    color = colors.onSurface,
                )
                Text(
                    text = if (isEnglish) "DEVICE ACCESS CENTER" else "PUSAT AKSES PERANGKAT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.7.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            SetupStatusPill(
                text = if (rootGranted) {
                    if (isEnglish) "READY" else "SIAP"
                } else {
                    if (isEnglish) "SETUP" else "PERSIAPAN"
                },
                positive = rootGranted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = if (isEnglish) "Set up your access" else "Siapkan akses AetherX",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = if (isEnglish) {
                    "Connect the permissions AetherX needs for system controls, overlays, and background monitoring. You can review each item below."
                } else {
                    "Atur izin yang dibutuhkan AetherX untuk kontrol sistem, overlay, dan pemantauan latar belakang. Periksa setiap item di bawah ini."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.primary.copy(alpha = 0.075f))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = if (rootGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Shield,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (isEnglish) {
                    if (rootGranted) "Core access is connected" else "Root unlocks advanced system controls"
                } else {
                    if (rootGranted) "Akses utama sudah terhubung" else "Root membuka kontrol sistem lanjutan"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun SetupProgressCard(
    isEnglish: Boolean,
    completed: Int,
    total: Int,
    rootGranted: Boolean,
    supportingGranted: Int,
) {
    val colors = MaterialTheme.colorScheme
    val progress = completed.toFloat() / total.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(23.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (isEnglish) "SETUP OVERVIEW" else "RINGKASAN PENGATURAN",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = if (isEnglish) "$completed of $total checks complete" else "$completed dari $total pemeriksaan selesai",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = colors.primary,
            trackColor = colors.surfaceVariant.copy(alpha = 0.8f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetupProgressMetric(
                modifier = Modifier.weight(1f),
                value = if (rootGranted) "1/1" else "0/1",
                label = if (isEnglish) "Root access" else "Akses root",
                complete = rootGranted,
            )
            SetupProgressMetric(
                modifier = Modifier.weight(1f),
                value = "$supportingGranted/3",
                label = if (isEnglish) "Extra access" else "Izin tambahan",
                complete = supportingGranted == 3,
            )
        }
    }
}

@Composable
private fun SetupProgressMetric(
    modifier: Modifier,
    value: String,
    label: String,
    complete: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.48f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background((if (complete) colors.primary else colors.onSurfaceVariant).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (complete) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (complete) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RootAccessCard(
    isEnglish: Boolean,
    rootGranted: Boolean,
    checkingRoot: Boolean,
    rootStatusText: String,
    onRequestRoot: () -> Unit,
    onRefreshRoot: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(25.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupIcon(
                imageVector = Icons.Outlined.Shield,
                tint = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                size = 48.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isEnglish) "Core permission" else "Izin utama",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.7.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.setup_page_access_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    text = rootStatusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                )
            }
            if (rootGranted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.setup_method_root_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.48f))
                .padding(12.dp),
        ) {
            Text(
                text = if (isEnglish) {
                    "Works with Magisk, KernelSU, and APatch. Approve the request in your root manager, then return here."
                } else {
                    "Mendukung Magisk, KernelSU, dan APatch. Setujui permintaan di aplikasi pengelola root, lalu kembali ke halaman ini."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        if (!rootGranted) {
            LiquidButton(
                onClick = onRequestRoot,
                enabled = !checkingRoot,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                if (checkingRoot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                    )
                    Text(
                        text = if (isEnglish) "Checking access…" else "Memeriksa akses…",
                        modifier = Modifier.padding(start = 9.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = if (isEnglish) "Grant root access" else "Berikan akses root",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(19.dp),
                    )
                }
            }
        } else {
            TextButton(onClick = onRefreshRoot, modifier = Modifier.align(Alignment.End)) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = if (isEnglish) "Verify again" else "Periksa ulang",
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RestrictedModeCard(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupIcon(Icons.Outlined.Lock, colors.error, size = 42.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isEnglish) "Limited mode" else "Mode terbatas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    text = if (isEnglish) "Root access not connected" else "Akses root belum terhubung",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
            }
        }
        Text(
            text = if (isEnglish) {
                "You can still open AetherX and explore available screens. Root-only controls remain locked and will not apply system changes."
            } else {
                "Kamu tetap bisa membuka AetherX dan melihat halaman yang tersedia. Kontrol khusus root tetap terkunci dan tidak akan menerapkan perubahan sistem."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.error.copy(alpha = 0.07f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LockedFeatureLine(if (isEnglish) "CPU/GPU and kernel controls" else "Kontrol CPU/GPU dan kernel")
            LockedFeatureLine(if (isEnglish) "System tweaks, terminal, and app management" else "Tweak sistem, terminal, dan pengelola aplikasi")
            LockedFeatureLine(if (isEnglish) "Automatic monitoring that requires root" else "Pemantauan otomatis yang membutuhkan root")
        }
    }
}

@Composable
private fun LockedFeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SupportingPermissionsSection(
    isEnglish: Boolean,
    writeSettingsGranted: Boolean,
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestWriteSettings: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val grantedCount = listOf(writeSettingsGranted, overlayGranted, notificationsGranted).count { it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (isEnglish) "Supporting permissions" else "Izin pendukung",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isEnglish) {
                        "Optional access for specific tools and background features."
                    } else {
                        "Izin tambahan untuk alat tertentu dan fitur latar belakang."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SetupStatusPill(
                text = "$grantedCount/3",
                positive = grantedCount == 3,
            )
        }

        SupportingPermissionCard(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.setup_method_write_settings),
            description = stringResource(R.string.setup_method_write_settings_desc),
            granted = writeSettingsGranted,
            isEnglish = isEnglish,
            onRequest = onRequestWriteSettings,
        )
        SupportingPermissionCard(
            icon = Icons.Outlined.Layers,
            title = stringResource(R.string.setup_method_overlay),
            description = stringResource(R.string.setup_method_overlay_desc),
            granted = overlayGranted,
            isEnglish = isEnglish,
            onRequest = onRequestOverlay,
        )
        SupportingPermissionCard(
            icon = Icons.Outlined.NotificationsActive,
            title = stringResource(R.string.setup_method_notifications),
            description = stringResource(R.string.setup_method_notifications_desc),
            granted = notificationsGranted,
            isEnglish = isEnglish,
            onRequest = onRequestNotifications,
        )
    }
}

@Composable
private fun SupportingPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    isEnglish: Boolean,
    onRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupIcon(
                imageVector = icon,
                tint = if (granted) colors.primary else colors.onSurfaceVariant,
                size = 43.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (granted) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (granted) {
                            if (isEnglish) "Permission granted" else "Izin sudah diberikan"
                        } else {
                            if (isEnglish) "Permission not granted" else "Izin belum diberikan"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (granted) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (!granted) {
            TextButton(
                onClick = onRequest,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = if (isEnglish) "Grant permission" else "Beri izin",
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 3.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationNote(isEnglish: Boolean, onRequestExemption: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        SetupIcon(Icons.Outlined.BatteryAlert, colors.onSurfaceVariant, size = 42.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = stringResource(R.string.setup_battery_optimization_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.setup_battery_optimization_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = onRequestExemption, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.setup_battery_optimization_action), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SafetyNote(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SetupIcon(Icons.Outlined.WarningAmber, colors.onSurfaceVariant, size = 38.dp)
        Text(
            text = if (isEnglish) {
                "Change CPU, GPU, or kernel settings carefully. Unsupported values can affect stability, temperature, and battery life."
            } else {
                "Ubah pengaturan CPU, GPU, atau kernel secara hati-hati. Nilai yang tidak sesuai dapat memengaruhi stabilitas, suhu, dan daya tahan baterai."
            },
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupIcon(imageVector: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun SetupStatusPill(text: String, positive: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = if (positive) colors.primary else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.11f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun SetupBottomBar(
    isEnglish: Boolean,
    rootGranted: Boolean,
    requireAccessToContinue: Boolean,
    onContinue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            )
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (requireAccessToContinue && !rootGranted) {
            Text(
                text = if (isEnglish) {
                    "You can set up root later from Access settings. Root-only features will stay locked until then."
                } else {
                    "Akses root bisa diatur nanti melalui menu Akses. Fitur khusus root tetap terkunci sampai akses diberikan."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        LiquidButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text(
                text = when {
                    !requireAccessToContinue -> if (isEnglish) "Close" else stringResource(R.string.permission_setup_kembali)
                    !rootGranted -> if (isEnglish) "Open app (limited mode)" else "Masuk aplikasi (mode terbatas)"
                    else -> if (isEnglish) "Open AetherX" else "Buka AetherX"
                },
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 5.dp).size(19.dp),
            )
        }
    }
}
