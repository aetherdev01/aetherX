package com.aether.x.core.monitor

import android.os.SystemClock
import com.aether.x.core.shell.ShellExecutor
import kotlin.math.roundToInt

/**
 * Reads rendered FPS from the most recent frame timestamps.
 *
 * SurfaceFlinger is attempted first because many games render through native
 * OpenGL/Vulkan surfaces that do not publish useful HWUI gfxinfo framestats.
 * gfxinfo remains as a fallback for apps/devices that expose it more reliably.
 */
class GfxInfoFpsReader(private val packageName: String) {

    companion object {
        private const val FRAME_COMPLETED_COLUMN_INDEX = 13
        private const val EXPECTED_MIN_COLUMNS = 14
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val FRAME_WINDOW = 45
        private const val MAX_SAMPLE_AGE_NS = 3_500_000_000L
    }

    private var cachedSurfaceLayers: List<String>? = null
    private var lastLayerSearchAtMs: Long = 0L

    suspend fun readFps(executor: ShellExecutor): Int? {
        readSurfaceFlingerFps(executor)?.let { return it }
        return readGfxInfoFps(executor)
    }

    private suspend fun readSurfaceFlingerFps(executor: ShellExecutor): Int? {
        var layers = cachedSurfaceLayers
        val nowMs = SystemClock.elapsedRealtime()
        val shouldRefreshEmptyLayerList = layers != null && layers.isEmpty() && nowMs - lastLayerSearchAtMs >= 15_000L
        if (layers == null || shouldRefreshEmptyLayerList) {
            lastLayerSearchAtMs = nowMs
            val listResult = executor.exec("dumpsys SurfaceFlinger --list")
            if (!listResult.success) return null
            layers = listResult.output
                .map(String::trim)
                .filter { it.isNotEmpty() && it.contains(packageName, ignoreCase = true) }
                .distinct()
                .sortedBy { layer ->
                    when {
                        layer.contains("SurfaceView", ignoreCase = true) -> 0
                        layer.contains("BLAST", ignoreCase = true) -> 1
                        layer.contains("BufferQueue", ignoreCase = true) -> 2
                        layer.contains("Starting", ignoreCase = true) || layer.contains("Splash", ignoreCase = true) -> 4
                        else -> 3
                    }
                }
                .take(8)
            cachedSurfaceLayers = layers
        }

        for (layer in layers) {
            val escapedLayer = layer.replace("'", "'\\''")
            val result = executor.exec("dumpsys SurfaceFlinger --latency '$escapedLayer'")
            if (!result.success) continue
            parseSurfaceFlingerLatency(result.output)?.let { return it }
        }
        return null
    }

    private fun parseSurfaceFlingerLatency(lines: List<String>): Int? {
        // SurfaceFlinger --latency starts with the display refresh period,
        // followed by rows: desiredPresentTime actualPresentTime frameReadyTime.
        val presented = lines.mapNotNull { line ->
            val columns = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (columns.size < 3) return@mapNotNull null
            columns[1].toLongOrNull()?.takeIf { it > 0L }
        }.distinct().sorted()
        if (presented.size < 2) return null

        val latest = presented.last()
        val age = SystemClock.elapsedRealtimeNanos() - latest
        if (age < 0L || age > MAX_SAMPLE_AGE_NS) return null

        val recent = presented.takeLast(FRAME_WINDOW)
        if (recent.size < 2) return null
        val elapsedNanos = recent.last() - recent.first()
        if (elapsedNanos <= 0L) return null
        val fps = ((recent.size - 1) * NANOS_PER_SECOND) / elapsedNanos.toDouble()
        if (fps.isNaN() || fps.isInfinite() || fps !in 1.0..360.0) return null
        return fps.roundToInt()
    }

    private suspend fun readGfxInfoFps(executor: ShellExecutor): Int? {
        val result = executor.exec("dumpsys gfxinfo $packageName framestats")
        if (!result.success) return null

        val timestampsNanos = parseFrameCompletedTimestamps(result.output)
        if (timestampsNanos.size < 2) return null

        val latest = timestampsNanos.last()
        val age = SystemClock.elapsedRealtimeNanos() - latest
        if (age < 0L || age > MAX_SAMPLE_AGE_NS) return null

        val recent = timestampsNanos.takeLast(FRAME_WINDOW)
        if (recent.size < 2) return null
        val elapsedNanos = recent.last() - recent.first()
        if (elapsedNanos <= 0L) return null

        val fps = ((recent.size - 1) * NANOS_PER_SECOND) / elapsedNanos.toDouble()
        if (fps.isNaN() || fps.isInfinite() || fps !in 1.0..360.0) return null
        return fps.roundToInt().coerceIn(1, 360)
    }

    private fun parseFrameCompletedTimestamps(lines: List<String>): List<Long> {
        val timestamps = mutableListOf<Long>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed[0].isDigit()) continue

            val columns = trimmed.split(',')
            if (columns.size < EXPECTED_MIN_COLUMNS || columns.size <= FRAME_COMPLETED_COLUMN_INDEX) continue

            val completedNanos = columns[FRAME_COMPLETED_COLUMN_INDEX].trim().toLongOrNull() ?: continue
            if (completedNanos <= 0L) continue
            timestamps.add(completedNanos)
        }
        return timestamps.distinct().sorted()
    }
}
