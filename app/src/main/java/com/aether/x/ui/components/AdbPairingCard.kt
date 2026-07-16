package com.aether.x.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * REWORK TOTAL — Auto-Pairing (lihat perintah rework: "jadikan sistem
 * pairing AetherX ... tinggal klik Start lalu ada notifikasi mengambang
 * Searching for Pairing, lalu buka opsi developer pilih Debug Nirkabel
 * lalu muncul notifikasi Pairing found ... tidak perlu isi alamat ip dll
 * secara manual").
 *
 * UI kartu ini sekarang HANYA punya satu tombol aksi ("Mulai Penyandingan"
 * / "Start") — TIDAK ADA field IP, port pairing, atau port koneksi sama
 * sekali. Host+port didapat otomatis lewat mDNS (lihat
 * [com.aether.x.core.adb.AdbAutoPairingDiscovery]); satu-satunya input
 * manual yang tersisa adalah kode 6-digit yang tampil di dialog Android
 * sendiri, diminta lewat [AdbAutoPairingCodeDialog] begitu service pairing
 * terdeteksi.
 *
 * Notifikasi mengambang "Searching for Pairing…" / "Pairing found"
 * ditampilkan oleh [AdbAutoPairingFloatingNotice] — dipanggil terpisah
 * dari layar pemanggil (mis. Box di root PermissionSetupScreen) supaya
 * bisa mengambang DI ATAS seluruh konten layar, bukan terkurung di dalam
 * kartu ini.
 */
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
                    // TIDAK perlu pairing ulang, cukup tombol sambungkan ulang.
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
                    // Sama persis dengan referensi UI: satu link teks untuk
                    // membuka Pengaturan, lalu satu tombol besar "Mulai
                    // Penyandingan" — TIDAK ADA form IP/port apa pun di sini.
                    // Begitu ditekan, seluruh proses (cari service pairing,
                    // notifikasi mengambang, dialog kode) ditangani otomatis
                    // lewat AdbAutoPairingFloatingNotice di root layar.
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

/**
 * Notifikasi mengambang ala referensi (kartu gelap kecil, melayang di atas
 * konten layar) yang menemani seluruh proses auto-pairing:
 *
 * - [AdbAutoPairingPhase.Searching] -> "Searching for Pairing…" + tombol Batal.
 * - [AdbAutoPairingPhase.Found]     -> "Pairing found" (transisi singkat
 *   sebelum [onCodeNeeded] otomatis membuka dialog kode).
 *
 * Ditempatkan sebagai overlay terpisah (bukan bagian dari [AdbPairingCard])
 * supaya melayang DI ATAS seluruh layar, bukan hanya di dalam kartu —
 * PERSIS seperti notifikasi mengambang pada referensi ("Searching for
 * Pairing" muncul sebagai bubble, bukan menempel di kartu).
 */
enum class AdbAutoPairingPhase { SEARCHING, FOUND }

@Composable
fun AdbAutoPairingFloatingNotice(
    phase: AdbAutoPairingPhase?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = phase != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (phase) {
                    AdbAutoPairingPhase.SEARCHING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Searching for Pairing…", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Buka Opsi Developer > Wireless debugging",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onCancel) { Text("Batal") }
                    }
                    AdbAutoPairingPhase.FOUND -> {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = "Pairing found", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    null -> Unit
                }
            }
        }
    }
}

/**
 * Dialog kode pairing — SATU-SATUNYA input manual yang tersisa di seluruh
 * alur auto-pairing. Muncul otomatis begitu service pairing terdeteksi
 * (state [com.aether.x.core.adb.AdbConnectionState.PairingFound]), diisi
 * kode 6-digit dari dialog "Sambungkan perangkat dengan kode penyambungan"
 * milik Android sendiri.
 */
@Composable
fun AdbAutoPairingCodeDialog(
    isBusy: Boolean,
    onConfirm: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Masukkan Kode Pairing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Perangkat sudah terdeteksi. Masukkan kode 6-digit dari \"Sambungkan perangkat dengan kode penyambungan\" di Wireless debugging.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { input -> code = input.filter(Char::isDigit).take(6) },
                    label = { Text("Kode Pairing") },
                    singleLine = true,
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code.trim()) },
                enabled = !isBusy && code.trim().length == 6,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text(text = "Menyandingkan…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Sandingkan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Batal") }
        },
    )
}
