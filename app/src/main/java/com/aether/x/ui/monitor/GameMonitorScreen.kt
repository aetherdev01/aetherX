package com.aether.x.ui.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.monitor.GameSession
import com.aether.x.core.monitor.GameSessionRepository
import com.aether.x.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GameMonitorScreen(
    rootGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(context.applicationContext) {
        GameSessionRepository.initialize(context.applicationContext)
    }
    val sessions by GameSessionRepository.sessions.collectAsStateWithLifecycle()
    var expandedGame by remember { mutableStateOf<String?>(null) }
    var expandedSession by remember { mutableStateOf<String?>(null) }
    val active = sessions.firstOrNull { it.endedAtMs == null }
    val groupedSessions = sessions.groupBy { it.packageName }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "Game Monitor Otomatis", watermarkIcon = Icons.Outlined.MonitorHeart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (rootGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = if (rootGranted) "ROOT TERSEDIA" else "ROOT DIBUTUHKAN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = if (active != null) "Sedang merekam sesi game" else "Menunggu game dibuka",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (rootGranted) {
                    "Game yang dikenali dari katalog akan direkam otomatis di latar belakang. Sampling dibuat berkala agar tetap ringan."
                } else {
                    "Berikan akses Root melalui Magisk, KernelSU, atau APatch agar AetherX bisa mendeteksi game yang sedang aktif dan merekam statistik otomatis. Riwayat lama tetap dapat dilihat."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active != null) {
                Text(
                    text = "Game aktif: ${active.gameName} · ${formatDuration(active.durationMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatsGrid(session = active)
            }
        }

        if (active != null && active.samples.isNotEmpty()) {
            SectionCard(title = "FPS sesi aktif", watermarkIcon = Icons.Outlined.Speed) {
                val fpsPoints = active.samples.mapNotNull { it.fps?.toFloat() }
                if (fpsPoints.size >= 2) {
                    RealtimeLineChart(
                        values = fpsPoints,
                        maxValue = maxOf(60f, fpsPoints.maxOrNull() ?: 60f),
                        lineColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text("Grafik FPS akan muncul setelah beberapa sampel tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "Rata-rata ${valueOrDash(active.averageFps, " FPS")} · Terendah ${valueOrDash(active.minFps, " FPS")} · Sampel FPS di bawah 30: ${active.lowFpsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Riwayat game", watermarkIcon = Icons.Outlined.History) {
            if (groupedSessions.isEmpty()) {
                Text(
                    text = if (rootGranted) "Belum ada riwayat. Buka game yang ada di katalog AetherX; sesi akan masuk ke daftar ini otomatis." else "Riwayat masih kosong. Setelah akses Root aktif, sesi game akan tersimpan otomatis di sini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                groupedSessions.forEach { (packageName, gameSessions) ->
                    val latest = gameSessions.first()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGame = if (expandedGame == packageName) null else packageName
                                    expandedSession = null
                                }
                                .padding(vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = latest.gameName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (expandedGame == packageName) "Tutup ↑" else "Detail ↓",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "${gameSessions.size} sesi · terakhir ${formatDate(latest.startedAtMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "FPS rata-rata ${valueOrDash(latest.averageFps, "")}  ·  Min ${valueOrDash(latest.minFps, "")}  ·  Suhu maks ${valueOrDash(latest.maxTemperatureC?.let { it.roundToInt() }, "°C")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expandedGame == packageName) {
                            gameSessions.forEach { session ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedSession = if (expandedSession == session.id) null else session.id
                                            }
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = formatDate(session.startedAtMs),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = "${formatDuration(session.durationMs)} · ${if (session.endedAtMs == null) "Sedang berjalan" else "Selesai"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                text = if (expandedSession == session.id) "Sembunyikan" else "Lihat data",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Text(
                                            text = "Avg ${valueOrDash(session.averageFps, " FPS")}  ·  Min ${valueOrDash(session.minFps, " FPS")}  ·  Max ${valueOrDash(session.maxFps, " FPS")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (expandedSession == session.id) {
                                            StatsGrid(session = session)
                                            val fpsPoints = session.samples.mapNotNull { it.fps?.toFloat() }
                                            if (fpsPoints.size >= 2) {
                                                Text("Grafik FPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                                RealtimeLineChart(
                                                    values = fpsPoints,
                                                    maxValue = maxOf(60f, fpsPoints.maxOrNull() ?: 60f),
                                                    lineColor = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            val tempPoints = session.samples.mapNotNull { it.temperatureC }
                                            if (tempPoints.size >= 2) {
                                                Text("Perubahan suhu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                                RealtimeLineChart(
                                                    values = tempPoints,
                                                    maxValue = maxOf(45f, tempPoints.maxOrNull() ?: 45f),
                                                    lineColor = MaterialTheme.colorScheme.tertiary,
                                                )
                                            }
                                            val cpuPoints = session.samples.mapNotNull { it.cpuPercent?.toFloat() }
                                            if (cpuPoints.size >= 2) {
                                                Text("Beban CPU", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                                RealtimeLineChart(
                                                    values = cpuPoints,
                                                    maxValue = 100f,
                                                    lineColor = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            val gpuPoints = session.samples.mapNotNull { it.gpuPercent?.toFloat() }
                                            if (gpuPoints.size >= 2) {
                                                Text("Beban GPU", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                                RealtimeLineChart(
                                                    values = gpuPoints,
                                                    maxValue = 100f,
                                                    lineColor = MaterialTheme.colorScheme.secondary,
                                                )
                                            }
                                            Text(
                                                text = "Paket: ${session.packageName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Catatan: FPS, GPU, dan suhu bergantung pada sensor serta akses yang disediakan perangkat. Tanda — berarti data tidak dapat dibaca; FPS dari gfxinfo tidak didukung oleh semua game native. Suhu yang tampil adalah sensor suhu terbaik yang tersedia, bukan jaminan suhu inti CPU/GPU.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsGrid(session: GameSession) {
    val currentOrLast = if (session.endedAtMs == null) "sekarang" else "akhir"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("FPS $currentOrLast / rata-rata", "${valueOrDash(session.currentFps, "")} / ${valueOrDash(session.averageFps, "")} FPS", Modifier.weight(1f))
            MetricTile("FPS min / maks", "${valueOrDash(session.minFps, "")} / ${valueOrDash(session.maxFps, "")} FPS", Modifier.weight(1f))
            MetricTile("FPS di bawah 30", "${session.lowFpsCount} sampel", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("CPU $currentOrLast / avg / maks", "${valueOrDash(session.currentCpuPercent, "")} / ${valueOrDash(session.averageCpuPercent, "")} / ${valueOrDash(session.maxCpuPercent, "")}%", Modifier.weight(1f))
            MetricTile("GPU terakhir / avg / maks", "${valueOrDash(session.currentGpuPercent, "")} / ${valueOrDash(session.averageGpuPercent, "")} / ${valueOrDash(session.maxGpuPercent, "")}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("GPU clock terakhir", valueOrDash(session.currentGpuFreqMhz?.roundToInt(), " MHz"), Modifier.weight(1f))
            MetricTile("RAM sistem sekarang / maks", "${formatGb(session.currentRamUsedMb)} / ${formatGb(session.maxRamUsedMb)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Suhu awal / $currentOrLast", "${valueOrDash(session.temperatureStartC?.roundToInt(), "°C")} / ${valueOrDash(session.currentTemperatureC?.roundToInt(), "°C")}", Modifier.weight(1f))
            MetricTile("Suhu maks / kenaikan", "${valueOrDash(session.maxTemperatureC?.roundToInt(), "°C")} / ${valueOrDash(session.temperatureRiseC?.roundToInt(), "°C")}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("RAM sistem rata-rata", formatGb(session.averageRamUsedMb), Modifier.weight(1f))
            MetricTile("Total sampel", "${session.sampleCount}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun valueOrDash(value: Int?, suffix: String): String = value?.let { "$it$suffix" } ?: "—"
private fun formatGb(valueMb: Long?): String = valueMb?.let { String.format(Locale.getDefault(), "%.1f GB", it / 1024f) } ?: "—"
private fun formatDate(time: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(time))
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}j ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}d"
        else -> "${seconds}d"
    }
}
