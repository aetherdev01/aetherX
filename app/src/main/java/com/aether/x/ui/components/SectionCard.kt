package com.aether.x.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared AetherX surface. Every feature screen uses this surface so cards,
 * section headers and controls keep one consistent visual rhythm.
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .clipToBounds()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.055f),
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.18f),
                                primary.copy(alpha = 0.035f),
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(top = 2.dp)
            )

            if (watermarkIcon != null) {
                Icon(
                    imageVector = watermarkIcon,
                    contentDescription = null,
                    tint = primary.copy(alpha = 0.035f),
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .padding(18.dp),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                content()
            }
        }
    }
}
