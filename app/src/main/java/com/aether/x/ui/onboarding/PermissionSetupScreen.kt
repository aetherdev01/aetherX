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
import androidx.compose.foundation.layout.heightIn
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
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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

/** Page heading rather than a large hero card: less wasted space and a clearer hierarchy. */
@Composable
private fun SetupIntro(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(21.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("AetherX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (isEnglish) "ACCESS & PERMISSIONS" else "AKSES & PERIZINAN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isEnglish) "Set up your access" else "Siapkan akses aplikasi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = if (isEnglish) {
                "Enable what you need. Root is optional, and you can change permissions later."
            } else {
                "Aktifkan izin yang diperlukan. Root bersifat opsional dan izin bisa diatur lagi nanti."
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (rootGranted) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                SetupIcon(
                    imageVector = Icons.Outlined.Shield,
                    tint = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (isEnglish) "Root access" else "Akses root", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = if (rootGranted) {
                            if (isEnglish) "Ready to use" else "Siap digunakan"
                        } else if (checkingRoot) {
                            if (isEnglish) "Checking status…" else "Memeriksa status…"
                        } else rootStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rootGranted) colors.primary else TextSecondary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = (if (rootGranted) colors.primary else colors.onSurfaceVariant).copy(alpha = 0.10f),
                ) {
                    Text(
                        text = if (rootGranted) (if (isEnglish) "ACTIVE" else "AKTIF") else (if (isEnglish) "OPTIONAL" else "OPSIONAL"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (isEnglish) {
                    "Unlock CPU, GPU, kernel, and other system controls. Approve the prompt in your root manager."
                } else {
                    "Buka kontrol CPU, GPU, kernel, dan sistem lainnya. Setujui permintaan melalui pengelola root."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (!rootGranted) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onRequestRoot,
                    enabled = !checkingRoot,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                ) {
                    if (checkingRoot) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
                        Text(if (isEnglish) "Checking…" else "Memeriksa…", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(if (isEnglish) "Grant root access" else "Minta akses root", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                TextButton(onClick = onRefreshRoot, modifier = Modifier.align(Alignment.End)) {
                    Text(if (isEnglish) "Refresh status" else "Perbarui status")
                }
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
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isEnglish) "Optional permissions" else "Izin tambahan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(if (isEnglish) "For features that need extra access" else "Untuk fitur tertentu saja", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text(
                text = if (isEnglish) "OPTIONAL" else "OPSIONAL",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp)) {
                SupportingPermissionRow(Icons.Outlined.Tune, stringResource(R.string.setup_method_write_settings), stringResource(R.string.setup_method_write_settings_desc), writeSettingsGranted, isEnglish, onRequestWriteSettings)
                PermissionDivider()
                SupportingPermissionRow(Icons.Outlined.Layers, stringResource(R.string.setup_method_overlay), stringResource(R.string.setup_method_overlay_desc), overlayGranted, isEnglish, onRequestOverlay)
                PermissionDivider()
                SupportingPermissionRow(Icons.Outlined.NotificationsActive, stringResource(R.string.setup_method_notifications), stringResource(R.string.setup_method_notifications_desc), notificationsGranted, isEnglish, onRequestNotifications)
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
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(
                color = (if (granted) colors.primary else colors.onSurfaceVariant).copy(alpha = 0.09f),
                shape = RoundedCornerShape(11.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (granted) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Text(
                text = when {
                    granted && isEnglish -> "Granted"
                    granted -> "Sudah diizinkan"
                    else -> description
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) colors.primary else TextSecondary,
                maxLines = 2,
            )
        }
        if (granted) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = if (isEnglish) "Granted" else "Diizinkan", tint = colors.primary, modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onRequest, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp)) {
                Text(if (isEnglish) "Set up" else "Atur", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermissionDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)))
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
