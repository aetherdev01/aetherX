package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * REWORK TOTAL PERMISSION (lihat perintah rework — "cara pairingnya juga
 * sama seperti shizuku pakai wireless adb"): kartu pairing ADB tertanam,
 * mengikuti alur wireless debugging Android 11+ yang SAMA persis dengan
 * yang ditampilkan referensi AxManager/Shizuku Manager:
 *
 * 1. Pengguna membuka Pengaturan > Opsi Developer > Wireless debugging >
 *    "Pasangkan perangkat dengan kode pairing" — dialog itu menampilkan
 *    IP:PORT PAIRING dan kode 6 digit.
 * 2. Pengguna memasukkan IP, PORT PAIRING, dan kode 6-digit itu ke form
 *    ini, PLUS port koneksi (dari layar utama Wireless debugging,
 *    biasanya port BEDA dari port pairing) untuk sesi shell setelahnya.
 * 3. Tombol "Mulai Penyandingan" memicu [onPair] — hasilnya
 *    (sukses/gagal) datang lewat [PrivilegeManager.events] yang sudah
 *    ditangani di level [PermissionSetupScreen] (Snackbar), bukan di sini.
 *
 * Kartu ini punya mode tampilan berbeda dari [PermissionMethodCard] biasa
 * karena butuh field input, bukan cuma tombol satu-klik.
 */
@Composable
fun AdbPairingCard(
    connected: Boolean,
    paired: Boolean,
    isBusy: Boolean,
    locked: Boolean,
    lockedHint: String?,
    onOpenWirelessDebugging: () -> Unit,
    onPair: (host: String, pairingPort: String, code: String, connectPort: String) -> Unit,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }

    val contentAlpha = if (locked) 0.5f else 1f

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
            modifier = Modifier
                .padding(18.dp)
                .let { if (locked) it else it }, // alpha diterapkan per-elemen di bawah, bukan seluruh Column (form tetap harus terbaca kalau tidak locked)
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "ADB Tertanam (Wireless Debugging)", style = MaterialTheme.typography.titleMedium)
                }
                if (connected) {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = "Aktifkan Wireless debugging di Opsi Developer, lalu sandingkan AetherX seperti menyandingkan Shizuku — tanpa perlu memasang aplikasi tambahan apa pun.",
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
                    // Sudah pernah pairing tapi sesi shell belum aktif (mis.
                    // baru buka app, atau Wireless debugging sempat mati) —
                    // TIDAK perlu form pairing lagi, cukup tombol sambungkan
                    // ulang. Ini yang membuat koneksi "tidak gampang
                    // ter-reset": pengguna tidak diminta mengetik ulang
                    // host/port/kode setiap kali.
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
                    TextButton(onClick = onForget) { Text("Pairing ulang / ganti perangkat") }
                }
                else -> {
                    if (!showForm) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenWirelessDebugging) {
                                Text("Buka Pengaturan Wireless Debugging")
                            }
                            Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Mulai Penyandingan")
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Buka \"Pasangkan perangkat dengan kode pairing\" di Wireless debugging, lalu isi sesuai yang tampil di sana.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("Alamat IP") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = pairingPort,
                                    onValueChange = { pairingPort = it },
                                    label = { Text("Port Pairing") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = connectPort,
                                    onValueChange = { connectPort = it },
                                    label = { Text("Port Koneksi") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text("Kode Pairing (6 digit)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showForm = false }, enabled = !isBusy) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = { onPair(host.trim(), pairingPort.trim(), code.trim(), connectPort.trim()) },
                                    enabled = !isBusy && host.isNotBlank() && pairingPort.isNotBlank() && code.isNotBlank() && connectPort.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Text(text = "Menyandingkan…", modifier = Modifier.padding(start = 8.dp))
                                    } else {
                                        Text("Sandingkan")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
