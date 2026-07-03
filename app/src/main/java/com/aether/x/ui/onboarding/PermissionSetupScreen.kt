package com.aether.x.ui.onboarding

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"

/**
 * Layar izin akses — REWORK TOTAL.
 *
 * Versi sebelumnya cuma `Scaffold` polos memakai warna default Material
 * (bukan skema gelap AetherX), tanpa hero/header, kartu-kartu ditumpuk
 * berurutan tanpa jeda visual yang jelas antara "wajib" dan "pendukung".
 * Rework ini:
 *  - Memakai token warna AetherX (BgVoid, TextPrimary, AccentBlue, dst.)
 *    dan bukan lagi `MaterialTheme.colorScheme` default supaya konsisten
 *    dengan layar lain (Tweak, Membership).
 *  - Header ikon perisai + judul + subjudul yang menjelaskan KENAPA izin
 *    ini dibutuhkan, bukan langsung lompat ke daftar kartu.
 *  - Banner status dipertegas: hijau solid saat siap, merah saat belum,
 *    dengan ikon & bobot teks lebih jelas.
 *  - Bagian "Izin Pendukung" divisualkan lebih redup (label kecil + garis
 *    pemisah) supaya jelas ini opsional dan tidak mengganggu hierarki
 *    dengan bagian wajib (Shizuku/Root) di atasnya.
 *  - CTA bawah dibuat sticky look dengan sedikit elevasi warna, bukan lagi
 *    menyatu polos dengan background.
 */
@Composable
fun PermissionSetupScreen(
    onContinue: () -> Unit,
    requireAccessToContinue: Boolean = true,
) {
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val canContinue = !requireAccessToContinue || status.hasAccess

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    // Cek ulang izin overlay/write-settings/notifikasi/Shizuku tiap kali
    // layar ini kembali ke foreground (mis. setelah pengguna kembali dari
    // halaman pengaturan sistem atau app Shizuku).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshSupportingPermissions(context)
                PrivilegeManager.refreshShizuku()
                // Jaring pengaman: kalau ternyata sudah ada backend granted
                // (mis. pengguna baru saja approve dialog Shizuku/su di luar
                // app, atau ini pengguna lama sebelum fitur pemisahan ada)
                // tapi preferensi belum sempat ter-set, adopsi otomatis di
                // sini supaya kartu yang lain langsung terkunci begitu
                // layar ini terlihat lagi.
                PrivilegeManager.adoptExistingGrantIfNoPreference(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        PrivilegeManager.refreshSupportingPermissions(context)
    }

    // Sama seperti jaring pengaman di atas, tapi untuk saat layar ini
    // PERTAMA kali ditampilkan (bukan cuma saat resume dari background) —
    // mis. pengguna lama yang baru buka layar Izin Akses pertama kali
    // setelah update app dan status Shizuku/Root granted-nya sudah terisi
    // duluan sebelum status.preferredBackend sempat diadopsi dari init().
    LaunchedEffect(status.shizukuGranted, status.rootGranted) {
        PrivilegeManager.adoptExistingGrantIfNoPreference(context)
    }

    Scaffold(containerColor = BgVoid) { padding ->
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

                    // Shizuku dan Root TIDAK BOLEH aktif bersamaan (lihat
                    // PrivilegeStatus.preferredBackend) — begitu salah satu
                    // dipilih pengguna, kartu yang lain terkunci (redup +
                    // tombol nonaktif) sampai pengguna menekan "Ganti metode"
                    // di bawah. Ini mencegah dua backend privilese berjalan
                    // berbarengan yang bisa saling menimpa hasil tweak.
                    val shizukuLocked = status.preferredBackend == PrivilegeBackend.ROOT
                    val rootLocked = status.preferredBackend == PrivilegeBackend.SHIZUKU

                    PermissionMethodCard(
                        title = stringResource(R.string.setup_method_shizuku),
                        description = stringResource(R.string.setup_method_shizuku_desc),
                        statusText = when {
                            !status.shizukuAvailable -> stringResource(R.string.setup_status_not_installed)
                            status.shizukuGranted -> stringResource(R.string.setup_status_granted)
                            else -> stringResource(R.string.setup_status_not_granted)
                        },
                        granted = status.shizukuGranted && !shizukuLocked,
                        locked = shizukuLocked,
                        lockedHint = stringResource(R.string.setup_locked_by_root_hint),
                        actionLabel = when {
                            !status.shizukuAvailable -> stringResource(R.string.setup_action_install_shizuku)
                            status.shizukuGranted -> stringResource(R.string.setup_action_open_shizuku)
                            else -> stringResource(R.string.setup_action_request)
                        },
                        onAction = {
                            when {
                                !status.shizukuAvailable -> openShizukuStorePage(context)
                                status.shizukuGranted -> openShizukuApp(context)
                                else -> PrivilegeManager.requestShizukuPermission(context)
                            }
                        },
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
                        lockedHint = stringResource(R.string.setup_locked_by_shizuku_hint),
                        actionLabel = stringResource(R.string.setup_action_request),
                        onAction = { PrivilegeManager.requestRoot(context) },
                    )

                    // Muncul begitu pengguna sudah memilih salah satu metode
                    // (kartu lain jadi terkunci) — satu-satunya jalan untuk
                    // beralih ke metode lain tanpa perlu uninstall/copot izin
                    // manual dari Magisk/Shizuku Manager.
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

private fun openShizukuApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        openShizukuStorePage(context)
    }
}

private fun openShizukuStorePage(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PLAY_STORE_URL)))
    }
}
