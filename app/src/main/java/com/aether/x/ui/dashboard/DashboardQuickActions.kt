package com.aether.x.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.aether.x.core.locale.str
import com.aether.x.R

/** Compact action grid: smaller cards with consistent icon, title, and detail hierarchy. */
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
        title = str(R.string.dashboard_quick_actions_aksi_cepat),
        watermarkIcon = Icons.Outlined.Tune,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardActionTile(
                    modifier = Modifier.weight(1f), icon = Icons.Outlined.Tune,
                    title = "Tweak", description = str(R.string.dashboard_quick_actions_performa_respons),
                    onClick = onOpenTweaks,
                )
                DashboardActionTile(
                    modifier = Modifier.weight(1f), icon = Icons.Outlined.SportsEsports,
                    title = str(R.string.game_profile_profil_game), description = str(R.string.dashboard_quick_actions_atur_tiap_game),
                    onClick = onOpenProfiles,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardActionTile(
                    modifier = Modifier.weight(1f), icon = Icons.Outlined.MonitorHeart,
                    title = "Monitor", description = if (rootGranted) str(R.string.dashboard_quick_actions_statistik_perangkat) else str(R.string.dashboard_quick_actions_perlu_izin_root),
                    enabled = rootGranted, onClick = onOpenMonitor,
                )
                DashboardActionTile(
                    modifier = Modifier.weight(1f), icon = Icons.Outlined.DeveloperBoard,
                    title = "Kernel", description = if (rootGranted) str(R.string.dashboard_quick_actions_cpu_gpu) else str(R.string.dashboard_quick_actions_perlu_izin_root),
                    enabled = rootGranted, onClick = onOpenKernelManager,
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
    val shape = RoundedCornerShape(15.dp)
    val contentAlpha = if (enabled) 1f else 0.58f
    Row(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.42f else 0.22f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.58f else 0.24f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.13f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
