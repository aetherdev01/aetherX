package com.aether.x.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.monitor.GameSessionRepository
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.theme.Spacing
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import com.aether.x.core.locale.str
import com.aether.x.R

@Composable
fun RecentlyPlayedGamesSection(
    games: List<InstalledGameEntry>,
    loading: Boolean,
    lastPlayedPackage: String?,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessions = GameSessionRepository.sessions.collectAsStateWithLifecycle().value
    LaunchedEffect(context.applicationContext) {
        GameSessionRepository.initialize(context.applicationContext)
    }
    val latestSessionByPackage = remember(sessions) {
        sessions.groupBy { it.packageName }.mapValues { (_, records) ->
            records.maxByOrNull { it.startedAtMs }
        }
    }

    SectionCard(
        title = str(R.string.dashboard_game_cards_baru_dimainkan),
        watermarkIcon = Icons.Outlined.History,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = str(R.string.dashboard_game_cards_akses_cepat_ke_game_terakhir),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = str(R.string.dashboard_game_cards_memuat_game_yang_tersedia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            games.isEmpty() -> Text(
                text = str(R.string.dashboard_game_cards_game_yang_dibuka_lewat_aetherx),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(games, key = { it.packageName }) { game ->
                    val isLastPlayed = game.packageName == lastPlayedPackage
                    val session = latestSessionByPackage[game.packageName]
                    Column(
                        modifier = Modifier
                            .width(156.dp)
                            .height(178.dp)
                            .aetherCardSurface(shape = RoundedCornerShape(18.dp))
                            .clickable { onGameClick(game.packageName) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ) {
                                Image(
                                    bitmap = game.icon,
                                    contentDescription = game.label,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (isLastPlayed) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                ) {
                                    Text(
                                        text = str(R.string.label_latest),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Text(
                            text = game.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isLastPlayed) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sessionDetail = session?.let {
                            buildString {
                                append(formatSessionDuration(it.durationMs))
                                append(str(R.string.dashboard_game_cards_sesi))
                                it.averageFps?.let { fps -> append(str(R.string.dashboard_game_cards_avg_fps, fps)) }
                            }
                        }
                        Text(
                            text = sessionDetail ?: if (isLastPlayed) str(R.string.dashboard_game_cards_terakhir_dibuka) else str(R.string.dashboard_game_cards_siap_dimainkan),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardGameMonitorSection(
    onOpenMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessions = GameSessionRepository.sessions.collectAsStateWithLifecycle().value
    LaunchedEffect(context) {
        GameSessionRepository.initialize(context.applicationContext)
    }
    val latest = sessions.maxByOrNull { it.startedAtMs }

    SectionCard(
        title = str(R.string.tweak_monitor_game),
        watermarkIcon = Icons.Outlined.MonitorHeart,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (latest == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(str(R.string.dashboard_game_cards_belum_ada_sesi_game), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        str(R.string.dashboard_game_cards_fps_dan_grafik_akan_muncul),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenMonitor, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(str(R.string.dashboard_game_cards_buka_monitor_game))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Pakai packageName dari sesi monitor, bukan mencocokkan nama tampilan.
                // Cara ini mengambil ikon aplikasi yang benar dan menghindari fallback
                // gamepad ketika label game tidak sama persis dengan nama aplikasi.
                val gameIcon = remember(context, latest.packageName) {
                    runCatching {
                        context.packageManager
                            .getApplicationIcon(latest.packageName)
                            .toBitmap(96, 96)
                            .asImageBitmap()
                    }.getOrNull()
                }
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    if (gameIcon != null) {
                        Image(bitmap = gameIcon, contentDescription = latest.gameName, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        latest.gameName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (latest.endedAtMs == null) str(R.string.dashboard_game_cards_sedang_direkam, formatSessionDuration(latest.durationMs))
                        else str(R.string.dashboard_game_cards_sesi_terakhir, formatSessionDuration(latest.durationMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenMonitor, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(str(R.string.dashboard_game_cards_detail))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            MiniFpsGraph(
                values = latest.samples.mapNotNull { it.fps?.toFloat() }.takeLast(36),
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(78.dp),
            )
            if (latest.samples.none { it.fps != null }) {
                Text(
                    str(R.string.dashboard_game_cards_menunggu_sampel_fps_yang_valid),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonitorSummary(
                    label = "AVG FPS",
                    value = latest.averageFps?.let { "$it" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MonitorSummary(
                    label = str(R.string.dashboard_game_cards_terendah),
                    value = latest.minFps?.let { "$it" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MonitorSummary(
                    label = str(R.string.dashboard_game_cards_tertinggi),
                    value = latest.maxFps?.let { "$it" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonitorSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .aetherCardSurface(shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MiniFpsGraph(values: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Canvas(
        modifier = modifier
            .aetherCardSurface(shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        val grid = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = grid)
        }
        if (values.size < 2) return@Canvas
        val high = max(60f, ((values.maxOrNull() ?: 60f) / 30f).roundToInt() * 30f)
        val path = Path()
        values.forEachIndexed { index, fps ->
            val x = size.width * index / (values.lastIndex.coerceAtLeast(1)).toFloat()
            val y = size.height - (fps.coerceIn(0f, high) / high) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun formatSessionDuration(durationMs: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.coerceAtLeast(0L))
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}
