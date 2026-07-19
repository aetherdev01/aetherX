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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
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
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

/**
 * Layar izin akses — REWORK TOTAL PERMISSION, lalu REWORK AUTO-PAIRING
 * (lihat perintah rework: "jadikan sistem pairing AetherX ... tinggal
 * klik Start lalu ada notifikasi mengambang Searching for Pairing ...
 * tidak perlu isi alamat ip dll secara manual").
 *
 * Kartu Shizuku lama DIGANTIKAN TOTAL oleh [AdbPairingCard] — sekarang
 * hanya satu tombol "Mulai Penyandingan" tanpa field apa pun. Host+port
 * pairing & koneksi didapat OTOMATIS lewat mDNS/NSD (lihat
 * [com.aether.x.core.adb.AdbAutoPairingDiscovery]).
 *
 * REWORK — notifikasi "Searching for Pairing…" dan input kode 6-digit
 * TIDAK LAGI berupa dialog/bubble Compose yang terikat ke layar ini, dan
 * TIDAK LAGI berupa window overlay (permintaan: "bukan pakai floating
 * window/dialog mengambang, tetapi pakai notifikasi sistem dengan
 * notifikasi mengambang"). Keduanya sekarang ditangani oleh
 * [com.aether.x.core.notification.AdbPairingNotifier] — notifikasi sistem
 * heads-up biasa dengan aksi **Balas** (RemoteInput) tertanam untuk kode
 * 6-digit, sehingga pengguna bisa membuka Wireless debugging dan mengisi
 * kode pairing langsung dari notification tray tanpa pernah berpindah
 * balik ke AetherX, tanpa window overlay, dan tanpa izin tambahan apa
 * pun. Layar ini hanya perlu menekan tombol "Mulai Penyandingan"; sisanya
 * otomatis lewat PrivilegeManager -> AdbPairingNotifier.
 *
 * Kartu Root tidak berubah strukturnya (masih [PermissionMethodCard]
 * biasa, satu tombol), hanya field yang dibaca dari [PrivilegeStatus]
 * disesuaikan (adbState/adbGranted menggantikan shizukuAvailable/
 * shizukuGranted).
 */
@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
) {
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val canContinue = !requireAccessToContinue || status.hasAccess

    // Setiap hasil aksi permintaan izin (gagal ATAU berhasil) SELALU
    // ditampilkan lewat Snackbar di sini, mengonsumsi PrivilegeManager.events
    // — tidak ada lagi kegagalan yang diam-diam tidak memberi tahu pengguna.
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

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    // Cek ulang izin overlay/write-settings/notifikasi/ADB/root tiap kali
    // layar ini kembali ke foreground (mis. setelah pengguna kembali dari
    // halaman Pengaturan Wireless debugging).
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

    LaunchedEffect(status.adbGranted, status.rootGranted) {
        PrivilegeManager.adoptExistingGrantIfNoPreference(context)
    }

    // Notifikasi mengambang "Searching for Pairing…" / aksi Balas kode
    // pairing SEKARANG ditangani oleh AdbPairingNotifier (notifikasi sistem
    // heads-up biasa, bukan window overlay) — lihat PrivilegeManager.init
    // untuk penerjemahan AdbConnectionState -> notifikasi yang sesuai.
    // Layar ini tidak lagi perlu state/dialog Compose sendiri untuk fase
    // pairing.

    Scaffold(
        containerColor = BgVoid,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PermissionHeader()

                if (requireAccessToContinue) {
                    ReadinessBanner(canContinue = canContinue)
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(text = stringResource(R.string.setup_required_header))

                    // ADB Tertanam dan Root TIDAK BOLEH aktif bersamaan
                    // (lihat PrivilegeStatus.preferredBackend) — begitu
                    // salah satu dipilih pengguna, kartu yang lain terkunci
                    // (redup + tombol nonaktif) sampai pengguna menekan
                    // "Ganti metode" di bawah.
                    val adbLocked = status.preferredBackend == PrivilegeBackend.ROOT
                    val rootLocked = status.preferredBackend == PrivilegeBackend.ADB

                    AdbPairingCard(
                        connected = status.adbGranted && !adbLocked,
                        // PairingFound/SearchingForPairing TETAP dianggap
                        // "belum paired" di sisi kartu utama (kartu tidak
                        // berubah tampilan jadi status "paired" dulu) —
                        // progresnya sepenuhnya ditampilkan lewat notifikasi
                        // mengambang + dialog kode di bawah, bukan di kartu ini.
                        paired = status.adbState.let {
                            it != AdbConnectionState.NotPaired &&
                                it !is AdbConnectionState.SearchingForPairing &&
                                it !is AdbConnectionState.PairingFound
                        },
                        isBusy = status.adbRequestState == RequestState.REQUESTING,
                        locked = adbLocked,
                        lockedHint = stringResource(R.string.setup_locked_by_root_hint),
                        onOpenWirelessDebugging = { PrivilegeManager.openWirelessDebuggingSettings(context) },
                        onStartAutoPairing = { PrivilegeManager.startAutoPairAdb(context) },
                        onReconnect = { PrivilegeManager.reconnectAdb(context) },
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

                    // Muncul begitu pengguna sudah memilih salah satu metode
                    // (kartu lain jadi terkunci) — satu-satunya jalan untuk
                    // beralih ke metode lain tanpa perlu mencabut izin
                    // manual dari Magisk/Pengaturan sistem.
                    AnimatedVisibility(
                        visible = status.preferredBackend != PrivilegeBackend.NONE,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.setup_switch_method),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentBlue,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { PrivilegeManager.clearBackendPreference(context) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(vertical = 4.dp)
                        .background(StrokeSubtle),
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(text = stringResource(R.string.setup_supporting_header))
                    Text(
                        text = stringResource(R.string.setup_supporting_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )

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
                        onAction = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }

            // --- CTA bawah: sedikit elevasi warna supaya terlihat menempel
            // di dasar layar (sticky look) tanpa benar-benar overlay/shadow. ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCardAlt)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = canContinue,
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
                        text = stringResource(R.string.setup_action_continue),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                AnimatedVisibility(visible = !canContinue, enter = fadeIn(), exit = fadeOut()) {
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
}

@Composable
private fun PermissionHeader() {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCardAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ReadinessBanner(canContinue: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (canContinue) AccentGreenContainer else SurfaceCardAlt)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (canContinue) AccentGreen else AccentRed),
        )
        Text(
            text = if (canContinue) {
                stringResource(R.string.setup_ready_hint)
            } else {
                stringResource(R.string.setup_required_banner)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (canContinue) AccentGreen else TextSecondary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
    )
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
