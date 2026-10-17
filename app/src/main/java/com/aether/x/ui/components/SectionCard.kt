package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.Spacing

/**
 * Surface kartu bersama AetherX. Semua layar fitur memakai ini supaya
 * radius, border, padding, dan jarak teks identik.
 *
 * v3.6:
 *  - [accent] — tiap kartu boleh punya warna sendiri (default = warna aksen
 *    tema) yang dipakai untuk chip ikon judul (dan garis tepi gaya OUTLINE).
 *  - Gaya permukaan mengikuti pilihan user (kaca/solid/garis/lembut) lewat
 *    [aetherCardSurface]; warna card sengaja netral/kalem, bukan gradasi
 *    berwarna di tiap card.
 *  - Jarak konsisten dari token [Spacing]: padding dalam [Spacing.card],
 *    judul -> isi [Spacing.cardGap].
 *  - Dekorasi (gradient + ikon watermark) dibuat sesederhana mungkin agar
 *    ringan saat banyak kartu di-scroll.
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = shape, accent = accent)
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (watermarkIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = watermarkIcon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        content()
    }
}
