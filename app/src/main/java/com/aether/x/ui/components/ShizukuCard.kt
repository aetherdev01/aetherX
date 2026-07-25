package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aether.x.core.shizuku.ShizukuConnectionState

/**
 * ROLLBACK — pengganti `AdbPairingCard` (DIHAPUS). Lihat KDoc
 * [com.aether.x.core.shizuku.ShizukuManager] untuk konteks lengkap.
 *
 * Jauh lebih sederhana dari kartu ADB sebelumnya: tidak ada form
 * host/port/kode pairing sama sekali di sini — proses pairing/start
 * service SEPENUHNYA terjadi di app Shizuku Manager eksternal, di luar
 * AetherX. Kartu ini murni menampilkan STATUS ([ShizukuConnectionState])
 * dan menyediakan 2 aksi: buka Shizuku Manager (untuk start service /
 * pairing di sana), dan refresh manual (untuk kasus "saya sudah start
 * servicenya, tapi AetherX belum update statusnya" — mis. kalau listener
 * binder Shizuku terlewat karena race kecil).
 */
@Composable
fun ShizukuCard(
    state: ShizukuConnectionState,
    locked: Boolean,
    lockedHint: String?,
    onOpenShizukuManager: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state == ShizukuConnectionState.Connected
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (connected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Shizuku", style = MaterialTheme.typography.titleMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (connected) {
                        Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Refresh status Shizuku")
                    }
                }
            }
            Text(
                text = "Butuh app Shizuku Manager terpisah — start service-nya di sana (ADB wireless/USB sekali jalan, root, atau modul Sui), lalu izinkan AetherX di sini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                locked -> {
                    Text(
                        text = lockedHint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state == ShizukuConnectionState.Connected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = "Terhubung",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            dotColor = null,
                        )
                        TextButton(onClick = onOpenShizukuManager) { Text("Buka Shizuku Manager") }
                    }
                }
                state == ShizukuConnectionState.PermissionNotGranted || state == ShizukuConnectionState.PermissionDenied -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = if (state == ShizukuConnectionState.PermissionDenied) "Izin ditolak" else "Service aktif, belum diizinkan",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            dotColor = null,
                        )
                        Button(onClick = onRequestPermission) { Text("Izinkan") }
                    }
                }
                state == ShizukuConnectionState.NotInstalled -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Shizuku Manager belum terinstal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenShizukuManager, modifier = Modifier.fillMaxWidth()) {
                            Text("Instal Shizuku Manager")
                        }
                    }
                }
                else -> {
                    // ServiceNotRunning
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Service Shizuku belum berjalan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenShizukuManager, modifier = Modifier.fillMaxWidth()) {
                            Text("Buka Shizuku Manager")
                        }
                    }
                }
            }
        }
    }
}
