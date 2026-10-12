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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.theme.LocalCardStyle
import com.aether.x.ui.theme.LocalGlassIntensity
import com.aether.x.data.CardStylePref

/**
 * Surface kartu bersama AetherX. Semua layar fitur memakai ini supaya
 * radius, border, padding, dan jarak teks identik.
 *
 * v3.6:
 *  - [accent] — tiap kartu boleh punya warna sendiri (default = primary/cyan)
 *    yang dipakai untuk chip ikon judul, garis highlight atas, dan border.
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
    val cardStyle = LocalCardStyle.current
    val glassIntensity = LocalGlassIntensity.current
    val shape = when (cardStyle) {
        CardStylePref.GLASS -> RoundedCornerShape(26.dp)
        CardStylePref.SOFT -> RoundedCornerShape(20.dp)
        CardStylePref.OUTLINED -> RoundedCornerShape(14.dp)
    }
    val surface = MaterialTheme.colorScheme.surface
    val container = when (cardStyle) {
        CardStylePref.GLASS -> surface.copy(alpha = 1f - glassIntensity * 0.08f)
        CardStylePref.SOFT -> surface
        CardStylePref.OUTLINED -> MaterialTheme.colorScheme.background
    }
    val borderColor = when (cardStyle) {
        CardStylePref.GLASS -> accent.copy(alpha = 0.12f + glassIntensity * 0.22f)
        CardStylePref.SOFT -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        CardStylePref.OUTLINED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (cardStyle == CardStylePref.SOFT) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box {
            // Highlight warna di sisi atas kartu.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .blur((glassIntensity * 14f).dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = if (cardStyle == CardStylePref.GLASS) 0.04f + glassIntensity * 0.12f else 0.06f), Color.Transparent),
                        ),
                    ),
            )

            Column(
                modifier = Modifier.padding(Spacing.card),
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
                                    .background(accent.copy(alpha = 0.16f)),
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
    }
}
