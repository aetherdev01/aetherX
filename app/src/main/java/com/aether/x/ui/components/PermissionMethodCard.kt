package com.aether.x.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Kartu satu metode akses privilese (ADB tertanam atau Root) di layar Izin Akses.
 *
 * [locked] = true saat metode LAIN sudah dipilih pengguna (lihat
 * PrivilegeManager.selectBackend) — supaya Shizuku dan Root tidak pernah
 * bisa diaktifkan berbarengan dari layar ini. Saat terkunci, kartu tampil
 * redup, tombol aksi dinonaktifkan, dan [lockedHint] ditampilkan
 * menggantikan tombol untuk menjelaskan kenapa (mis. "Matikan Root dulu
 * untuk pakai ADB").
 *
 * Saat [granted] = true (dan tidak [locked]): pill status + tombol aksi
 * ("Aktif" / "Izinkan" dsb.) SENGAJA tidak ditampilkan lagi — kartu yang
 * sudah beres tidak perlu terus "berteriak" dengan label dan tombol yang
 * sudah tidak relevan. Ikon centang di header sudah cukup jadi checklist
 * bahwa metode ini aktif, tanpa elemen tambahan yang mengganggu.
 *
 * BUG FIX (rework permission — lihat perintah rework "terkadang bug tidak
 * bisa di pencet"): SEBELUMNYA kalau [locked] true TAPI [lockedHint] null,
 * tidak ada cabang `when` yang cocok sama sekali — kartu jadi tidak
 * menampilkan tombol MAUPUN hint, membuat pengguna mengira kartu "macet/
 * rusak". Sekarang [lockedHint] punya default string kosong yang tetap
 * masuk cabang locked (tidak pernah jatuh ke celah kosong), dan tombol aksi
 * di cabang non-locked SELALU dirender terlepas granted/tidak — supaya
 * tombol tidak pernah hilang tak terduga.
 *
 * [isRequesting] = true SELAMA proses permintaan izin berlangsung (lihat
 * PrivilegeStatus.adbRequestState/rootRequestState) — menampilkan
 * spinner kecil + label "Meminta…" di tombol, feedback visual bahwa tap
 * pengguna BENAR-BENAR terdaftar dan sedang diproses, bukan diam begitu
 * saja seperti sebelumnya.
 */
@Composable
fun PermissionMethodCard(
    title: String,
    description: String,
    statusText: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    lockedHint: String? = null,
    isRequesting: Boolean = false,
    requestingLabel: String? = null,
) {
    val contentAlpha = if (locked) 0.5f else 1f
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = if (granted) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp).alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (granted) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                // BUG FIX: locked SELALU masuk cabang ini sekarang (lockedHint
                // tidak pernah dibiarkan null tanpa fallback) — tidak ada lagi
                // celah "locked tapi tidak render apa-apa".
                locked -> {
                    Text(
                        text = lockedHint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                granted -> {
                    // Sudah aktif: tidak ada pill "Aktif", teks status,
                    // maupun tombol "Izinkan" lagi — semua itu tidak
                    // relevan begitu izin sudah diberikan dan cuma bikin
                    // ramai. Ikon centang di header (di atas) sudah cukup
                    // jadi penanda checklist bahwa metode ini aktif.
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = statusText,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            dotColor = null,
                        )
                        // BUG FIX: tombol SELALU enabled di cabang ini (locked
                        // sudah ditangani cabang terpisah di atas, jadi tidak
                        // perlu enabled = !locked lagi di sini) — hanya
                        // dinonaktifkan sesaat SELAMA isRequesting supaya
                        // tidak bisa double-tap, bukan karena alasan lain
                        // yang tidak terlihat pengguna.
                        Button(onClick = onAction, enabled = !isRequesting) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(
                                    text = requestingLabel ?: actionLabel,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            } else {
                                Text(actionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
