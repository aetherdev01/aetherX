package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdbPairingCard(
    connected: Boolean,
    paired: Boolean,
    isBusy: Boolean,
    locked: Boolean,
    lockedHint: String?,
    onOpenWirelessDebugging: () -> Unit,
    onStartAutoPairing: () -> Unit,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    Icon(imageVector = Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Wireless Debugging (Beta)", style = MaterialTheme.typography.titleMedium)
                }
                if (connected) {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = "Aktifkan Wireless debugging di Opsi Developer, isi kode pairing lalu sandingkan AetherX",
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
                connected -> {
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
                        TextButton(onClick = onForget) { Text("Lupakan perangkat") }
                    }
                }
                paired -> {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = "Belum tersambung",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            dotColor = null,
                        )
                        Button(onClick = onReconnect, enabled = !isBusy) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Text(text = "Menyambungkan…", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("Sambungkan")
                            }
                        }
                    }
                    TextButton(onClick = onForget) { Text("Pairing") }
                }
                else -> {

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOpenWirelessDebugging) {
                            Text("Buka Pengaturan Wireless Debugging")
                        }
                        Button(
                            onClick = onStartAutoPairing,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Text(text = "Mencari perangkat…", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("Mulai Penyandingan")
                            }
                        }
                    }
                }
            }
        }
    }
}
