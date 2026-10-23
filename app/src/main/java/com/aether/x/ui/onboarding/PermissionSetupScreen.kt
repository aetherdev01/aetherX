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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BorderStroke
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
 * Access setup is intentionally non-blocking: users without root can enter the
 * app, while controls that require elevated privileges remain unavailable.
 * Supporting permissions are optional and can be granted later from this page.
 */
@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
) {
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val isEnglish = remember(context) {
        LocaleManager.current(context) == AppLanguage.ENGLISH
    }
    val snackbarHostState = remember { SnackbarHostState() }

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
                ignoringBatteryOptimizations =
                    PrivilegeManager.isIgnoringBatteryOptimizations(context)
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
                    verticalArrangement = Arrangement.spacedBy(18.dp),
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

                    if (!status.rootGranted) {
                        RestrictedModeCard(isEnglish = isEnglish)
                    }

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

                    if (status.rootGranted) {
                        SafetyNote(isEnglish = isEnglish)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupIntro(isEnglish: Boolean) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                        shape = RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "AetherX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = if (isEnglish) "ACCESS SETUP" else "PENYIAPAN AKSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        Text(
            text = if (isEnglish) "Access and permissions" else "Akses dan izin",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = if (isEnglish) {
                "Root enables system controls and performance tweaks. You can still open AetherX without root; root-dependent features will stay locked until access is available."
            } else {
                "Root diperlukan untuk kontrol sistem dan optimasi performa. AetherX tetap bisa dibuka tanpa root, tetapi fitur yang memerlukannya akan terkunci sampai akses tersedia."
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (rootGranted) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                SetupIcon(
                    imageVector = Icons.Outlined.Shield,
                    tint = if (rootGranted) colors.primary else colors.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.setup_page_access_title),
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
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.setup_method_root_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            if (!rootGranted) {
                Text(
                    text = if (isEnglish) {
                        "Grant the request in your root manager (such as Magisk, KernelSU, or APatch), then return to this page."
                    } else {
                        "Setujui permintaan melalui pengelola root seperti Magisk, KernelSU, atau APatch, lalu kembali ke halaman ini."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Button(
                    onClick = onRequestRoot,
                    enabled = !checkingRoot,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
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
                            text = if (isEnglish) "Checking…" else "Memeriksa…",
                            modifier = Modifier.padding(start = 9.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text(
                            text = if (isEnglish) "Request root access" else "Minta akses root",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Text(
                    text = if (isEnglish) {
                        "Root access is available. System features can run when their required supporting permissions are also granted."
                    } else {
                        "Akses root tersedia. Fitur sistem dapat dijalankan setelah izin pendukung yang diperlukan juga diberikan."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                TextButton(
                    onClick = onRefreshRoot,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (isEnglish) "Check again" else "Periksa ulang")
                }
            }
        }
    }
}

@Composable
private fun RestrictedModeCard(isEnglish: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.65f)),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (isEnglish) "Limited mode — root unavailable" else "Mode terbatas — root belum tersedia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Text(
                text = if (isEnglish) {
                    "You can enter the app, browse its available screens, and set permissions. Root-only controls remain locked and will not apply system changes."
                } else {
                    "Kamu tetap bisa masuk aplikasi, melihat halaman yang tersedia, dan mengatur izin. Kontrol khusus root tetap terkunci dan tidak akan menerapkan perubahan sistem."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            LockedFeatureLine(
                text = if (isEnglish) "CPU/GPU and kernel controls" else "Kontrol CPU/GPU dan kernel",
            )
            LockedFeatureLine(
                text = if (isEnglish) "System tweaks, terminal, and app management" else "Tweak sistem, terminal, dan pengelola aplikasi",
            )
            LockedFeatureLine(
                text = if (isEnglish) "Automatic monitoring that requires root" else "Pemantauan otomatis yang membutuhkan root",
            )
        }
    }
}

@Composable
private fun LockedFeatureLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
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
                text = if (isEnglish) {
                    "These permissions do not replace root. Grant them only for the features that need them; you can change them later."
                } else {
                    "Izin ini tidak menggantikan root. Berikan sesuai kebutuhan fitur dan kamu bisa mengubahnya nanti."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(19.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = if (granted) {
                        if (isEnglish) "Granted" else "Sudah diizinkan"
                    } else {
                        if (isEnglish) "Not granted" else "Belum diizinkan"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) MaterialTheme.colorScheme.primary else TextMuted,
                )
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
            }
        }
    }
}

@Composable
private fun PermissionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun BatteryOptimizationNote(
    isEnglish: Boolean,
    onRequestExemption: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.BatteryAlert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(19.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.setup_battery_optimization_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
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
}

@Composable
private fun SafetyNote(isEnglish: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        Text(
            text = if (isEnglish) {
                "Change CPU, GPU, or kernel settings carefully. Unsupported values can affect stability, temperature, and battery life."
            } else {
                "Ubah pengaturan CPU, GPU, atau kernel secara hati-hati. Nilai yang tidak sesuai dapat memengaruhi stabilitas, suhu, dan daya tahan baterai."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SetupIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                color = tint.copy(alpha = 0.10f),
                shape = RoundedCornerShape(11.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
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
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (requireAccessToContinue && !rootGranted) {
                Text(
                    text = if (isEnglish) {
                        "You can set up root later from Access settings. Root-only features will stay locked until then."
                    } else {
                        "Akses root bisa diatur nanti melalui menu Akses. Fitur khusus root tetap terkunci sampai akses diberikan."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
            }
        }
    }
}
