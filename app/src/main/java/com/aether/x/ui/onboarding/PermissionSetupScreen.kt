package com.aether.x.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.permission.RequestFailureReason
import com.aether.x.core.permission.RequestFeedback
import com.aether.x.ui.components.AetherBackground
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

/**
 * Compact, card-based setup page. Root is optional for entering the app;
 * features requiring elevated access remain locked until root is granted.
 */
@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
) {
    val context = LocalContext.current
    // The first-run setup is mandatory navigation: system Back must not close the app.
    // Manage Access passes requireAccessToContinue=false and keeps normal Back behavior.
    BackHandler(enabled = requireAccessToContinue) { /* Intentionally consume Back. */ }

    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val isEnglish = remember(context) { LocaleManager.current(context) == AppLanguage.ENGLISH }
    val snackbarHostState = remember { SnackbarHostState() }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(PrivilegeManager.isIgnoringBatteryOptimizations(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    LaunchedEffect(Unit) { PrivilegeManager.refreshSupportingPermissions(context) }
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
        status.checkingRoot -> if (isEnglish) "Checking access…" else "Memeriksa akses…"
        status.rootGranted -> stringResource(R.string.setup_status_granted)
        else -> stringResource(R.string.setup_status_not_granted)
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
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SetupIntro(isEnglish = isEnglish)
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
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

/** Page heading rather than a large hero card: less wasted space and a clearer hierarchy. */
@Composable
private fun SetupIntro(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AetherX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    text = if (isEnglish) "SETUP & PERMISSIONS" else "PENYIAPAN & IZIN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
        }
        Text(
            text = if (isEnglish) "Set up your access" else "Atur akses aplikasi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = if (isEnglish) {
                "Root unlocks system controls. You can still use AetherX in limited mode without root."
            } else {
                "Root membuka kontrol sistem. Kamu tetap bisa memakai AetherX dalam mode terbatas tanpa root."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (rootGranted) colors.primary.copy(alpha = 0.42f) else colors.outlineVariant.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupIcon(
                    imageVector = Icons.Outlined.Shield,
                    tint = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isEnglish) "Root access" else "Akses root",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = rootStatusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                    )
                }
                if (rootGranted) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                text = if (rootGranted) {
                    if (isEnglish) "Root is ready for supported system controls." else "Root siap digunakan untuk kontrol sistem yang didukung."
                } else {
                    if (isEnglish) "Approve the request in Magisk, KernelSU, or APatch." else "Setujui permintaan melalui Magisk, KernelSU, atau APatch."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (!rootGranted) {
                Button(
                    onClick = onRequestRoot,
                    enabled = !checkingRoot,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    if (checkingRoot) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                        Text(if (isEnglish) "Checking…" else "Memeriksa…", modifier = Modifier.padding(start = 9.dp), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(if (isEnglish) "Grant root access" else "Minta akses root", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                TextButton(onClick = onRefreshRoot, modifier = Modifier.align(Alignment.End)) {
                    Text(if (isEnglish) "Check again" else "Periksa ulang")
                }
            }
        }
    }
}

@Composable
private fun RestrictedModeCard(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(19.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isEnglish) "Limited mode" else "Mode terbatas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = if (isEnglish) {
                        "You can continue without root. CPU/GPU, kernel, and other root-only controls stay locked."
                    } else {
                        "Kamu bisa lanjut tanpa root. Kontrol CPU/GPU, kernel, dan fitur khusus root tetap terkunci."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (isEnglish) "Supporting permissions" else "Izin pendukung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = if (isEnglish) "Optional permissions for specific features." else "Izin tambahan untuk fitur tertentu; bisa diatur nanti.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 2.dp)) {
                SupportingPermissionRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.setup_method_write_settings),
                    description = stringResource(R.string.setup_method_write_settings_desc),
                    granted = writeSettingsGranted,
                    isEnglish = isEnglish,
                    onRequest = onRequestWriteSettings,
                )
                PermissionDivider()
                SupportingPermissionRow(
                    icon = Icons.Outlined.Layers,
                    title = stringResource(R.string.setup_method_overlay),
                    description = stringResource(R.string.setup_method_overlay_desc),
                    granted = overlayGranted,
                    isEnglish = isEnglish,
                    onRequest = onRequestOverlay,
                )
                PermissionDivider()
                SupportingPermissionRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.setup_method_notifications),
                    description = stringResource(R.string.setup_method_notifications_desc),
                    granted = notificationsGranted,
                    isEnglish = isEnglish,
                    onRequest = onRequestNotifications,
                )
            }
        }
    }
}

@Composable
private fun SupportingPermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    isEnglish: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(19.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    text = when {
                        granted && isEnglish -> "Granted"
                        granted -> "Sudah diizinkan"
                        isEnglish -> "Not granted"
                        else -> "Belum diizinkan"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) MaterialTheme.colorScheme.primary else TextMuted,
                )
            }
            if (!granted) {
                TextButton(onClick = onRequest, modifier = Modifier.padding(start = 0.dp)) {
                    Text(if (isEnglish) "Allow" else "Atur", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PermissionDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

@Composable
private fun BatteryOptimizationNote(isEnglish: Boolean, onRequestExemption: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_battery_optimization_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(stringResource(R.string.setup_battery_optimization_desc), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            TextButton(onClick = onRequestExemption) {
                Text(stringResource(R.string.setup_battery_optimization_action))
            }
        }
    }
}

@Composable
private fun SafetyNote(isEnglish: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(
            text = if (isEnglish) {
                "Change CPU, GPU, or kernel settings carefully. Unsupported values may affect stability, temperature, and battery life."
            } else {
                "Ubah pengaturan CPU, GPU, atau kernel dengan hati-hati. Nilai yang tidak sesuai dapat memengaruhi stabilitas, suhu, dan baterai."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SetupIcon(imageVector: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(38.dp).background(color = tint.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun SetupBottomBar(
    isEnglish: Boolean,
    rootGranted: Boolean,
    requireAccessToContinue: Boolean,
    onContinue: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (requireAccessToContinue && !rootGranted) {
                Text(
                    text = if (isEnglish) {
                        "Root can be configured later. Root-only features stay locked until access is granted."
                    } else {
                        "Root bisa diatur nanti. Fitur khusus root tetap terkunci sampai akses diberikan."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = when {
                        !requireAccessToContinue -> if (isEnglish) "Close" else stringResource(R.string.permission_setup_kembali)
                        !rootGranted -> if (isEnglish) "Continue in limited mode" else "Masuk mode terbatas"
                        else -> if (isEnglish) "Open AetherX" else "Buka AetherX"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
