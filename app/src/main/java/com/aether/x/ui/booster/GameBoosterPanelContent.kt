package com.aether.x.ui.booster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.core.booster.GameBoosterMetrics
import com.aether.x.core.booster.GameBoosterSession
import com.aether.x.core.booster.RecentAppEntry
import com.aether.x.data.GameMode
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

@Composable
fun GameBoosterPanelContent(
    session: GameBoosterSession,
    actions: GameBoosterActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgVoid)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameBoosterAppRail(
            recentApps = session.recentApps,
            onAppClick = actions.onOpenApp ?: {},
            modifier = Modifier.fillMaxHeight(),
        )
        GameBoosterMainPanel(
            session = session,
            actions = actions,
            modifier = Modifier.width(360.dp),
        )
    }
}

@Composable
private fun GameBoosterAppRail(
    recentApps: List<RecentAppEntry>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardAlt)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(recentApps, key = { it.packageName }) { app ->
                RailAppIcon(app = app, onClick = { onAppClick(app.packageName) })
            }
        }
    }
}

@Composable
private fun RailAppIcon(app: RecentAppEntry, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = app.label,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private enum class GameBoosterTab { GAMING_TOOLS, GAMES }

@Composable
private fun GameBoosterMainPanel(
    session: GameBoosterSession,
    actions: GameBoosterActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(GameBoosterTab.GAMING_TOOLS) }
    val configuration = LocalConfiguration.current
    val maxPanelHeight = (configuration.screenHeightDp * 0.85f).dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCardAlt)
            .heightIn(max = maxPanelHeight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PanelHeader(onMinimize = actions.onMinimize, onEndSession = actions.onEndSession)

        GameBoosterTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

        when (selectedTab) {
            GameBoosterTab.GAMING_TOOLS -> GamingToolsTab(session = session, actions = actions)
            GameBoosterTab.GAMES -> GamesTab(session = session, onLaunchGame = actions.onLaunchGame)
        }
    }
}

@Composable
private fun PanelHeader(onMinimize: (() -> Unit)?, onEndSession: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(R.string.game_booster_panel_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {

            onMinimize?.let { minimize ->
                HeaderIconButton(icon = Icons.Outlined.Remove, contentDescription = stringResource(R.string.game_booster_panel_minimize), onClick = minimize)
            }
            HeaderIconButton(icon = Icons.Outlined.Close, contentDescription = stringResource(R.string.game_booster_panel_end_session), onClick = onEndSession, tint = AccentRed)
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = TextMuted) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(SurfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun GameBoosterTabRow(selectedTab: GameBoosterTab, onTabSelected: (GameBoosterTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabChip(
            label = stringResource(R.string.game_booster_panel_tab_tools),
            selected = selectedTab == GameBoosterTab.GAMING_TOOLS,
            onClick = { onTabSelected(GameBoosterTab.GAMING_TOOLS) },
            modifier = Modifier.weight(1f),
        )
        TabChip(
            label = stringResource(R.string.game_booster_panel_tab_games),
            selected = selectedTab == GameBoosterTab.GAMES,
            onClick = { onTabSelected(GameBoosterTab.GAMES) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) SurfaceCardAlt else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextMuted,
        )
    }
}

@Composable
private fun GamingToolsTab(session: GameBoosterSession, actions: GameBoosterActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonitoringGraphCard(metrics = session.metrics)
        ModeSelector(currentMode = session.mode, onModeChange = actions.onModeChange)
        SettingsTileRow(actions = actions)
        QuickToolsGrid(session = session, actions = actions)
    }
}

@Composable
private fun MonitoringGraphCard(metrics: GameBoosterMetrics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceRaised)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = stringResource(
                        R.string.game_booster_panel_monitor_average_jitter,
                        metrics.fps ?: 0,
                        calculateJitter(metrics.fpsHistory),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Outlined.FiberManualRecord,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(8.dp),
                )
                Text(
                    text = stringResource(R.string.game_booster_panel_monitor_realtime, metrics.fps ?: 0),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                )
            }
        }

        if (metrics.fps == null) {
            Text(
                text = stringResource(R.string.game_booster_panel_monitor_fps_unavailable_hint),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        } else {
            FpsHistoryGraph(history = metrics.fpsHistory)
        }
    }
}

@Composable
private fun FpsHistoryGraph(history: List<Int>) {
    val maxFps = (history.maxOrNull() ?: 60).coerceAtLeast(30)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {

        val gridColor = Color.White.copy(alpha = 0.06f)
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = size.height * (1f - fraction)
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.5f)
        }
        if (history.size < 2) return@Canvas
        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val points = history.mapIndexed { index, fps ->
            Offset(
                x = index * stepX,
                y = size.height - (fps.toFloat() / maxFps.toFloat()) * size.height,
            )
        }
        for (i in 0 until points.size - 1) {
            drawLine(color = AccentGreen, start = points[i], end = points[i + 1], strokeWidth = 3f)
        }
    }
}

private fun calculateJitter(history: List<Int>): Int {
    if (history.size < 2) return 0
    val deltas = history.zipWithNext { a, b -> kotlin.math.abs(a - b) }
    return deltas.average().toInt()
}

@Composable
private fun ModeSelector(currentMode: GameMode, onModeChange: (GameMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ModeUnderlineOption(
            label = stringResource(R.string.game_booster_panel_mode_balanced),
            selected = currentMode == GameMode.MID,
            onClick = { onModeChange(GameMode.MID) },
        )
        ModeUnderlineOption(
            label = stringResource(R.string.game_booster_panel_mode_performance),
            selected = currentMode == GameMode.BOOST,
            onClick = { onModeChange(GameMode.BOOST) },
        )
    }
}

@Composable
private fun ModeUnderlineOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AccentBlue else TextMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 28.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) AccentBlue else Color.Transparent),
        )
    }
}

@Composable
private fun SettingsTileRow(actions: GameBoosterActions) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsTile(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.game_booster_panel_tile_settings_title),
            subtitle = stringResource(R.string.game_booster_panel_tile_settings_subtitle),
            onClick = actions.onOpenPerformanceSettings ?: {},
            modifier = Modifier.weight(1f),
        )
        SettingsTile(
            icon = Icons.Outlined.CleaningServices,
            title = stringResource(R.string.game_booster_panel_tile_clear_memory_title),
            subtitle = stringResource(R.string.game_booster_panel_tile_clear_memory_subtitle),
            onClick = actions.onClearMemory ?: {},
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(AccentBlue.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickToolsGrid(session: GameBoosterSession, actions: GameBoosterActions) {
    val tiles = quickTiles(session, actions)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(tiles) { tile ->
            QuickTile(tile = tile)
        }
    }
}

private data class QuickToolTile(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val tint: Color = TextSecondary,
    val onClick: () -> Unit,
)

@Composable
private fun quickTiles(session: GameBoosterSession, actions: GameBoosterActions): List<QuickToolTile> = listOf(
    QuickToolTile(icon = Icons.Outlined.RecordVoiceOver, label = stringResource(R.string.game_booster_panel_quick_voice_changer), onClick = actions.onVoiceChanger ?: {}),
    QuickToolTile(icon = Icons.Outlined.ScreenshotMonitor, label = stringResource(R.string.game_booster_panel_quick_screenshot), onClick = actions.onScreenshot),
    QuickToolTile(icon = Icons.Outlined.FiberManualRecord, label = stringResource(R.string.game_booster_panel_quick_record), onClick = actions.onRecord ?: {}),
    QuickToolTile(
        icon = Icons.Outlined.NotificationsOff,
        label = stringResource(R.string.game_booster_panel_quick_dnd),
        active = session.dndEnabled,
        tint = if (session.dndEnabled) AccentGreen else TextSecondary,
        onClick = { actions.onDndToggle(!session.dndEnabled) },
    ),
    QuickToolTile(
        icon = Icons.Outlined.GridView,
        label = stringResource(R.string.game_booster_panel_quick_on_screen),
        active = session.rotationLocked,
        tint = if (session.rotationLocked) AccentGreen else TextSecondary,
        onClick = { actions.onRotationLockToggle?.invoke(!session.rotationLocked) },
    ),
    QuickToolTile(icon = Icons.Outlined.Brightness6, label = stringResource(R.string.game_booster_panel_quick_brightness), onClick = actions.onBrightness ?: {}),
    QuickToolTile(icon = Icons.Outlined.Wifi, label = stringResource(R.string.game_booster_panel_quick_wifi), onClick = actions.onWifi ?: {}),
    QuickToolTile(icon = Icons.Outlined.Memory, label = stringResource(R.string.game_booster_panel_quick_more_tools), onClick = actions.onMoreTools ?: {}),
)

@Composable
private fun QuickTile(tile: QuickToolTile) {
    Column(
        modifier = Modifier
            .clickable(onClick = tile.onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (tile.active) AccentGreen.copy(alpha = 0.16f) else SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = tile.icon, contentDescription = tile.label, tint = tile.tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GamesTab(session: GameBoosterSession, onLaunchGame: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCardAlt),
            contentAlignment = Alignment.Center,
        ) {
            val icon = session.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
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
        if (onLaunchGame != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentBlue)
                    .clickable(onClick = onLaunchGame)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(text = stringResource(R.string.game_booster_panel_open_game), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
