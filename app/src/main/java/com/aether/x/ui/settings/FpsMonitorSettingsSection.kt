package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.FpsMonitorStyle
import com.aether.x.ui.components.TweakSwitch

private data class FpsStyleOption(
    val style: FpsMonitorStyle,
    val labelRes: Int,
    val descRes: Int,
)

private val fpsStyleOptions = listOf(
    FpsStyleOption(FpsMonitorStyle.ROG, R.string.fps_monitor_style_rog, R.string.fps_monitor_style_rog_desc),
    FpsStyleOption(FpsMonitorStyle.CLASSIC, R.string.fps_monitor_style_classic, R.string.fps_monitor_style_classic_desc),
)

private const val FPS_MONITOR_FEATURE_UNLOCKED = false

@Composable
fun FpsMonitorSettingsSection(
    enabled: Boolean,
    style: FpsMonitorStyle,
    overlayPermissionGranted: Boolean,
    hasShellAccess: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStyleChange: (FpsMonitorStyle) -> Unit,
) {

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TweakSwitch(
            label = stringResource(R.string.fps_monitor_enable),
            description = stringResource(R.string.fps_monitor_locked_desc),
            checked = FPS_MONITOR_FEATURE_UNLOCKED && enabled,
            enabled = FPS_MONITOR_FEATURE_UNLOCKED,
            onCheckedChange = { checked ->
                if (checked && !overlayPermissionGranted) {
                    onRequestOverlayPermission()
                } else {
                    onEnabledChange(checked)
                }
            },
        )

        if (FPS_MONITOR_FEATURE_UNLOCKED && !overlayPermissionGranted) {
            Text(
                text = stringResource(R.string.fps_monitor_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (FPS_MONITOR_FEATURE_UNLOCKED && enabled) {
            Column {
                Text(
                    text = stringResource(R.string.fps_monitor_style_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fpsStyleOptions.forEach { option ->
                        FpsStyleRow(
                            label = stringResource(option.labelRes),
                            description = stringResource(option.descRes),
                            selected = option.style == style,
                            onClick = { onStyleChange(option.style) },
                        )
                    }
                }
            }

            Text(
                text = if (style == FpsMonitorStyle.CLASSIC) {
                    stringResource(R.string.fps_monitor_position_locked_hint)
                } else {
                    stringResource(R.string.fps_monitor_position_draggable_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.fps_monitor_accuracy_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = if (hasShellAccess) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            Text(
                text = stringResource(R.string.fps_monitor_gpu_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FpsStyleRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
