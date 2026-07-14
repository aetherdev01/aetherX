package com.aether.x.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.Spacing

/**
 * Pill kecil gaya AetherX (mis. "ID-76957", "Disconnected"): latar gelap,
 * sudut penuh, teks bold kecil, dengan titik indikator warna opsional.
 *
 * [leadingIcon] (FITUR BARU — lihat perintah rework: "untuk badge user
 * Membership ada Logo VIP di sisi kiri badge ID dan itu real icon"):
 * ikon nyata (bukan bentuk/placeholder) yang digambar di ujung kiri pill,
 * SEBELUM dot (kalau ada) dan teks. Dipakai badge ID pengguna di
 * [com.aether.x.ui.tweak.TweakHeader.TweakHeader] untuk menampilkan logo
 * mahkota/VIP saat status membership pengguna aktif. `null` (default)
 * berarti tidak ada ikon sama sekali — perilaku lama tidak berubah untuk
 * semua pemanggilan StatusPill lain yang sudah ada (mis. status Game
 * Profile aktif/nonaktif).
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotColor: Color? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = contentColor,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingIconTint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
