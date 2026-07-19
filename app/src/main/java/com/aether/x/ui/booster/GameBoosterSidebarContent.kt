package com.aether.x.ui.booster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.booster.GameBoosterMetrics
import com.aether.x.core.booster.GameBoosterSession
import com.aether.x.data.GameMode
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.SurfaceCard
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

data class GameBoosterActions(
    val onModeChange: (GameMode) -> Unit,
    val onDndToggle: (Boolean) -> Unit,
    val onFpsOverlayToggle: (Boolean) -> Unit,
    val onScreenshot: () -> Unit,
    val onEndSession: () -> Unit,

    val onClose: (() -> Unit)? = null,

    val onLaunchGame: (() -> Unit)? = null,

    val onMinimize: (() -> Unit)? = null,

    val onOpenApp: ((String) -> Unit)? = null,

    val onOpenPerformanceSettings: (() -> Unit)? = null,

    val onClearMemory: (() -> Unit)? = null,
    val onVoiceChanger: (() -> Unit)? = null,
    val onRecord: (() -> Unit)? = null,
    val onRotationLockToggle: ((Boolean) -> Unit)? = null,
    val onBrightness: (() -> Unit)? = null,
    val onWifi: (() -> Unit)? = null,
    val onMoreTools: (() -> Unit)? = null,
)

@Composable
fun GameBoosterSidebarContent(
    session: GameBoosterSession,
    actions: GameBoosterActions,
    modifier: Modifier = Modifier,
) {

    val configuration = LocalConfiguration.current
    val maxPanelHeight = (configuration.screenHeightDp * 0.85f).dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard)
            .heightIn(max = maxPanelHeight)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.game_booster_sidebar_header),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Outlined.BatteryChargingFull,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(16.dp),
                )
                actions.onClose?.let { onClose ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.game_booster_sidebar_close),
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SidebarSideMetric(
                icon = Icons.Outlined.Thermostat,
                value = session.metrics.temperatureCelsius?.let { "%.1f°".format(it) } ?: "-",
                label = stringResource(R.string.game_booster_metrics_temp),
                tint = AccentAmber,
                modifier = Modifier.weight(0.75f),
            )
            GameLaunchCard(
                session = session,
                onLaunchGame = actions.onLaunchGame,
                modifier = Modifier.weight(1.5f),
            )
            SidebarSideMetric(
                icon = Icons.Outlined.Speed,
                value = session.metrics.gpuLoadPercent?.let { "$it%" } ?: "-",
                label = stringResource(R.string.game_booster_metrics_gpu),
                tint = severityColor(session.metrics.gpuLoadPercent),
                modifier = Modifier.weight(0.75f),
            )
        }

        ModeSelector(currentMode = session.mode, onModeChange = actions.onModeChange)

        GameBoosterMenuToggleRow(
            icon = Icons.Outlined.Speed,
            label = stringResource(R.string.game_booster_menu_fps),
            checked = session.fpsOverlayEnabled,
            onCheckedChange = actions.onFpsOverlayToggle,
        )
        GameBoosterMenuToggleRow(
            icon = Icons.Outlined.NotificationsOff,
            label = stringResource(R.string.game_booster_menu_dnd),
            checked = session.dndEnabled,
            onCheckedChange = actions.onDndToggle,
        )

        GameBoosterActionRow(
            icon = Icons.Outlined.PhotoCamera,
            label = stringResource(R.string.game_booster_menu_screenshot),
            onClick = actions.onScreenshot,
        )

        GameBoosterMetricsPanel(metrics = session.metrics)

        GameBoosterActionRow(
            icon = Icons.Outlined.Bolt,
            label = stringResource(R.string.game_booster_sidebar_end_session),
            onClick = actions.onEndSession,
            tint = AccentRed,
        )
    }
}

@Composable
private fun SidebarSideMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GameLaunchCard(
    session: GameBoosterSession,
    onLaunchGame: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                val icon = session.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = session.gameLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (onLaunchGame != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue)
                    .clickable(onClick = onLaunchGame)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.game_booster_sidebar_launch),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(currentMode: GameMode, onModeChange: (GameMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ModeSelectorOption(
            label = stringResource(R.string.game_booster_mode_hemat),
            icon = Icons.Outlined.BatteryChargingFull,
            selected = currentMode == GameMode.LOW,
            onClick = { onModeChange(GameMode.LOW) },
            modifier = Modifier.weight(1f),
        )
        ModeSelectorOption(
            label = stringResource(R.string.game_booster_mode_normal),
            icon = Icons.Outlined.SportsEsports,
            selected = currentMode == GameMode.MID,
            onClick = { onModeChange(GameMode.MID) },
            modifier = Modifier.weight(1f),
        )
        ModeSelectorOption(
            label = stringResource(R.string.game_booster_mode_boost),
            icon = Icons.Outlined.Bolt,
            selected = currentMode == GameMode.BOOST,
            onClick = { onModeChange(GameMode.BOOST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSelectorOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) AccentBlue else TextMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun GameBoosterMenuToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun GameBoosterActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AccentBlue,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(SurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun GameBoosterMetricsPanel(metrics: GameBoosterMetrics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.game_booster_metrics_title),
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricValue(label = stringResource(R.string.game_booster_metrics_fps), value = metrics.fps?.toString() ?: "-", color = AccentBlue)
            MetricValue(label = stringResource(R.string.game_booster_metrics_cpu), value = metrics.cpuLoadPercent?.let { "$it%" } ?: "-", color = severityColor(metrics.cpuLoadPercent))
            MetricValue(label = stringResource(R.string.game_booster_metrics_gpu), value = metrics.gpuLoadPercent?.let { "$it%" } ?: "-", color = severityColor(metrics.gpuLoadPercent))
            MetricValue(label = stringResource(R.string.game_booster_metrics_temp), value = metrics.temperatureCelsius?.let { "${it.toInt()}°" } ?: "-", color = AccentAmber)
        }

        if (metrics.fps == null) {
            Text(
                text = stringResource(R.string.game_booster_metrics_fps_unavailable_hint),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        } else if (metrics.fpsHistory.size >= 2) {
            FpsHistoryGraph(history = metrics.fpsHistory)
        }
    }
}

@Composable
private fun MetricValue(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

private fun severityColor(percent: Int?): Color = when {
    percent == null -> TextPrimary
    percent < 60 -> AccentGreen
    percent < 85 -> AccentAmber
    else -> AccentRed
}

@Composable
private fun FpsHistoryGraph(history: List<Int>) {
    val maxFps = (history.maxOrNull() ?: 60).coerceAtLeast(30)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val points = history.mapIndexed { index, fps ->
            Offset(
                x = index * stepX,
                y = size.height - (fps.toFloat() / maxFps.toFloat()) * size.height,
            )
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = AccentBlue,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f,
            )
        }
    }
}
