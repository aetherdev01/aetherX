package com.aether.x.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.SectionCard

/** Compact two-column action grid to keep the dashboard aligned and easy to scan. */
@Composable
fun DashboardQuickActions(
    rootGranted: Boolean,
    onOpenTweaks: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenKernelManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Aksi cepat",
        watermarkIcon = Icons.Outlined.Tune,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Buka fitur utama tanpa mencari di menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Tune,
                    title = "Tweak",
                    description = "Atur respons dan performa",
                    onClick = onOpenTweaks,
                )
                DashboardActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.SportsEsports,
                    title = "Profil game",
                    description = "Konfigurasi per game",
                    onClick = onOpenProfiles,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.MonitorHeart,
                    title = "Monitor",
                    description = if (rootGranted) "Statistik perangkat" else "Perlu akses root",
                    enabled = rootGranted,
                    onClick = onOpenMonitor,
                )
                DashboardActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.DeveloperBoard,
                    title = "Kernel",
                    description = if (rootGranted) "Kontrol CPU dan GPU" else "Perlu akses root",
                    enabled = rootGranted,
                    onClick = onOpenKernelManager,
                )
            }
        }
    }
}

@Composable
private fun DashboardActionTile(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.52f else 0.26f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.65f else 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.13f else 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.42f),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
