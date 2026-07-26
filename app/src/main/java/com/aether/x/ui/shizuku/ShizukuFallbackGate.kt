package com.aether.x.ui.shizuku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shizuku.ShizukuConnectionState
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim

/**
 * Pop up global untuk kasus: pengguna sebelumnya sudah memilih Shizuku
 * sebagai backend ([PrivilegeStatus.preferredBackend] == SHIZUKU), tapi
 * binder Shizuku sedang mati/belum diizinkan sehingga
 * [PrivilegeStatus.activeBackend] otomatis jatuh ke NONE (No Root) —
 * lihat fallback di [com.aether.x.core.permission.PrivilegeStatus.activeBackend].
 *
 * Tanpa gate ini, penurunan ke No Root terjadi diam-diam: tweak dan fitur
 * lain yang butuh privilege langsung berhenti bekerja tanpa penjelasan ke
 * pengguna. Gate ini muncul di root Compose tree (dipasang sejajar dengan
 * [com.aether.x.ui.maintenance.MaintenanceGate] / [com.aether.x.ui.update.UpdateGate]
 * di `MainActivity`) supaya terdeteksi di layar mana pun, lalu menawarkan
 * jalan pintas ke Shizuku Manager atau minta izin ulang — persis 2 aksi
 * yang sudah ada di [com.aether.x.ui.components.ShizukuCard].
 *
 * Dismiss bersifat per-sesi (state lokal, bukan disimpan ke preferences):
 * sengaja TIDAK permanen, supaya pengguna tidak lupa kalau fitur tweak
 * sedang tidak berfungsi. Begitu Shizuku hidup lagi, gate otomatis hilang
 * sendiri (dan otomatis muncul lagi kalau nanti mati lagi).
 */
@Composable
fun ShizukuFallbackGate() {
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var dismissed by remember { mutableStateOf(false) }

    val shouldShow = status.preferredBackend == PrivilegeBackend.SHIZUKU &&
        status.activeBackend == PrivilegeBackend.NONE

    // Reset dismiss begitu kondisi hilang, supaya kalau nanti Shizuku mati
    // lagi di kemudian hari, pop up bisa tampil lagi alih-alih ke-suppress
    // selamanya oleh state lama.
    if (!shouldShow && dismissed) {
        dismissed = false
    }

    if (!shouldShow || dismissed) return

    val state = status.shizukuState

    Dialog(
        onDismissRequest = { dismissed = true },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentBlueDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(32.dp),
                )
            }

            Text(
                text = "Shizuku Terputus",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = shizukuFallbackMessage(state),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Selama Shizuku belum aktif lagi, AetherX berjalan tanpa akses khusus (No Root) dan sebagian fitur tweak tidak akan berfungsi.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val primaryAction: () -> Unit = when (state) {
                ShizukuConnectionState.PermissionNotGranted,
                ShizukuConnectionState.PermissionDenied,
                -> {
                    { PrivilegeManager.requestShizukuPermission() }
                }
                else -> {
                    { PrivilegeManager.openShizukuManager(context) }
                }
            }
            val primaryLabel = when (state) {
                ShizukuConnectionState.PermissionNotGranted,
                ShizukuConnectionState.PermissionDenied,
                -> "Izinkan Shizuku"
                ShizukuConnectionState.NotInstalled -> "Instal Shizuku Manager"
                else -> "Buka Shizuku Manager"
            }

            Button(
                onClick = primaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null)
                    Text(text = primaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(onClick = { PrivilegeManager.refreshShizuku() }) {
                Text(text = "Sudah aktifkan, cek ulang", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TextButton(onClick = { dismissed = true }) {
                Text(text = "Nanti saja", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shizukuFallbackMessage(state: ShizukuConnectionState): String = when (state) {
    ShizukuConnectionState.NotInstalled ->
        "Sebelumnya kamu memakai Shizuku, tapi app Shizuku Manager sudah tidak terpasang di perangkat ini."
    ShizukuConnectionState.ServiceNotRunning ->
        "Sebelumnya kamu memakai Shizuku, tapi service-nya sekarang tidak berjalan. Start ulang lewat app Shizuku Manager."
    ShizukuConnectionState.PermissionNotGranted ->
        "Service Shizuku sudah aktif, tapi AetherX belum diberi izin memakainya."
    ShizukuConnectionState.PermissionDenied ->
        "Izin Shizuku untuk AetherX sebelumnya ditolak. Izinkan ulang untuk mengaktifkan kembali fitur tweak."
    ShizukuConnectionState.Connected ->
        "Sebelumnya kamu memakai Shizuku. Aktifkan ulang untuk kembali memakai fitur yang butuh akses khusus."
}
