package com.aether.x.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.ui.theme.Spacing

@Composable
private fun SectionCardWatermark(icon: ImageVector? = null) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = Spacing.xs, start = Spacing.xs)
                .size(84.dp),
        )
    } else {
        Icon(
            painter = painterResource(id = R.drawable.ic_aetherx_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = Spacing.xs, start = Spacing.xs)
                .size(84.dp),
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark(icon = watermarkIcon)
            Column(
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
            }
        }
    }
}

@Composable
fun SectionCard(
    title: Nothing? = null,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark(icon = watermarkIcon)
            Column(
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                content()
            }
        }
    }
}
