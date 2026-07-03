package com.aether.x.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Kartu satu metode akses privilese (Shizuku atau Root) di layar Izin Akses.
 *
 * [locked] = true saat metode LAIN sudah dipilih pengguna (lihat
 * PrivilegeManager.selectBackend) — supaya Shizuku dan Root tidak pernah
 * bisa diaktifkan berbarengan dari layar ini. Saat terkunci, kartu tampil
 * redup, tombol aksi dinonaktifkan, dan [lockedHint] ditampilkan
 * menggantikan tombol untuk menjelaskan kenapa (mis. "Matikan Root dulu
 * untuk pakai Shizuku").
 *
 * Saat [granted] = true (dan tidak [locked]): pill status + tombol aksi
 * ("Aktif" / "Izinkan" dsb.) SENGAJA tidak ditampilkan lagi — kartu yang
 * sudah beres tidak perlu terus "berteriak" dengan label dan tombol yang
 * sudah tidak relevan. Ikon centang di header sudah cukup jadi checklist
 * bahwa metode ini aktif, tanpa elemen tambahan yang mengganggu.
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
                locked && lockedHint != null -> {
                    Text(
                        text = lockedHint,
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
                        Button(onClick = onAction, enabled = !locked) { Text(actionLabel) }
                    }
                }
            }
        }
    }
}
