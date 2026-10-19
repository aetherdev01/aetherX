package com.aether.x.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.SectionCard

/** Functional shortcuts for common destinations; root-only destinations stay disabled without root. */
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
            text = "Langsung buka fitur yang paling sering dipakai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onOpenTweaks, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                androidx.compose.material3.Icon(Icons.Outlined.Tune, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Tweak", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                androidx.compose.material3.Icon(Icons.Outlined.SportsEsports, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Profil game", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onOpenMonitor,
                enabled = rootGranted,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.MonitorHeart, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Monitor", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onOpenKernelManager,
                enabled = rootGranted,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.DeveloperBoard, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Kernel", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!rootGranted) {
            Text(
                text = "Monitor dan Kernel Manager memerlukan izin root.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
