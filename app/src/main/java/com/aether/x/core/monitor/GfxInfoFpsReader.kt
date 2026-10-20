package com.aether.x.core.monitor

import android.os.SystemClock
import com.aether.x.core.shell.ShellExecutor
import kotlin.math.roundToInt

/**
 * Reads presented-frame cadence for a foreground game.
 *
 * SurfaceFlinger is preferred for native OpenGL/Vulkan games; gfxinfo is the
 * fallback for apps using HWUI. The frame timestamps returned by Android can
 * use either MONOTONIC or BOOTTIME depending on the source, so age validation
 * accepts either clock domain instead of accidentally discarding every frame.
 */
class GfxInfoFpsReader(private val packageName: String) {

    companion object {
        private const val FRAME_COMPLETED_COLUMN_INDEX = 13
        private const val EXPECTED_MIN_COLUMNS = 14
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val FRAME_WINDOW = 45
        private const val MAX_SAMPLE_AGE_NS = 6_000_000_000L
        private const val LAYER_REFRESH_MS = 15_000L
    }

    private var cachedSurfaceLayers: List<String>? = null
    private var lastLayerSearchAtMs: Long = 0L
    private var timestatsEnabled = false
    private val previousLayerFrames = mutableMapOf<String, Pair<Long, Long>>()

    private data class LayerTimestats(
        val name: String,
        val packageName: String?,
        val totalFrames: Long,
        val averageFps: Double?,
    )

    /** Start SurfaceFlinger's game-layer counters as a fallback for Android 15/16 devices where --latency is unreliable. */
    suspend fun startCapture(executor: ShellExecutor) {
        if (timestatsEnabled) return
        val result = executor.exec("dumpsys SurfaceFlinger --timestats -clear -enable")
        timestatsEnabled = result.success
        previousLayerFrames.clear()
    }

    /** Disable the global counter when this game session ends; never leave it enabled intentionally. */
    suspend fun stopCapture(executor: ShellExecutor) {
        if (timestatsEnabled) {
            runCatching { executor.exec("dumpsys SurfaceFlinger --timestats -disable") }
        }
        timestatsEnabled = false
        previousLayerFrames.clear()
    }

    suspend fun readFps(executor: ShellExecutor): Int? {
        readSurfaceFlingerFps(executor)?.let { return it }
        readGfxInfoFps(executor)?.let { return it }
        return readTimestatsFps(executor)
    }

    private suspend fun readSurfaceFlingerFps(executor: ShellExecutor): Int? {
        val nowMs = SystemClock.elapsedRealtime()
        val cached = cachedSurfaceLayers
        if (cached == null || nowMs - lastLayerSearchAtMs >= LAYER_REFRESH_MS) {
            // Refresh even a non-empty cache: games often recreate their
            // SurfaceView after rotating or changing resolution/fullscreen mode.
            val listResult = executor.exec("dumpsys SurfaceFlinger --list")
            if (listResult.success) {
                cachedSurfaceLayers = listResult.output
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
                    // Keep sampling lightweight; the top matching layers usually
                    // include the game's actual SurfaceView/BLAST queue.
                    .take(4)
                lastLayerSearchAtMs = nowMs
            }
        }

        val layers = cachedSurfaceLayers.orEmpty()
        for (layer in layers) {
            val escapedLayer = layer.replace("'", "'\\''")
            val result = executor.exec("dumpsys SurfaceFlinger --latency '$escapedLayer'")
            if (!result.success) continue
            parseSurfaceFlingerLatency(result.output)?.let { return it }
        }
        return null
    }

    private fun parseSurfaceFlingerLatency(lines: List<String>): Int? {
        // Format: refresh-period header followed by desiredPresentTime,
        // actualPresentTime and frameReadyTime. The actual-present column is 1.
        val presented = lines.mapNotNull { line ->
            val columns = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (columns.size < 3) return@mapNotNull null
            val timestamp = columns[1].toLongOrNull() ?: return@mapNotNull null
            timestamp.takeIf { isRecentTimestamp(it) }
        }.distinct().sorted()
        return calculateFps(presented)
    }

    private suspend fun readGfxInfoFps(executor: ShellExecutor): Int? {
        val result = executor.exec("dumpsys gfxinfo $packageName framestats")
        if (!result.success) return null

        val timestampsNanos = parseFrameCompletedTimestamps(result.output)
            .filter(::isRecentTimestamp)
        return calculateFps(timestampsNanos)
    }

    private fun parseFrameCompletedTimestamps(lines: List<String>): List<Long> {
        val timestamps = mutableListOf<Long>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed[0].isDigit()) continue

            val columns = trimmed.split(',')
            if (columns.size < EXPECTED_MIN_COLUMNS || columns.size <= FRAME_COMPLETED_COLUMN_INDEX) continue

            val completedNanos = columns[FRAME_COMPLETED_COLUMN_INDEX].trim().toLongOrNull() ?: continue
            if (completedNanos <= 0L || completedNanos == Long.MAX_VALUE) continue
            timestamps.add(completedNanos)
        }
        return timestamps.distinct().sorted()
    }

    /**
     * Reads the per-layer counters recommended by Android for game frame-rate
     * monitoring. The first result uses the layer's average; later readings use
     * the frame-count delta over the actual sample interval for a live estimate.
     */
    private suspend fun readTimestatsFps(executor: ShellExecutor): Int? {
        if (!timestatsEnabled) return null
        val result = executor.exec("dumpsys SurfaceFlinger --timestats -dump")
        if (!result.success || result.output.isEmpty()) return null

        val nowMs = SystemClock.elapsedRealtime()
        val candidates = parseLayerTimestats(result.output)
            .filter {
                it.name.contains(packageName, ignoreCase = true) ||
                    it.packageName.equals(packageName, ignoreCase = true)
            }
            .sortedWith(
                compareBy<LayerTimestats> { layerPriority(it.name) }
                    .thenByDescending { it.totalFrames },
            )
        if (candidates.isEmpty()) return null

        val deltas = candidates.mapNotNull { layer ->
            val previous = previousLayerFrames[layer.name]
            previousLayerFrames[layer.name] = layer.totalFrames to nowMs
            if (previous == null || nowMs <= previous.second) return@mapNotNull null
            val frameDelta = layer.totalFrames - previous.first
            if (frameDelta < 0L) return@mapNotNull null // Layer was reset/recreated.
            val elapsedMs = (nowMs - previous.second).coerceAtLeast(1L)
            val fps = (frameDelta * 1000.0 / elapsedMs).roundToInt()
            fps.takeIf { it in 0..360 }?.let { layer.name to it }
        }
        // Different games can expose several surfaces. Pick the layer that is
        // actually advancing, rather than letting an idle overlay force FPS=0.
        deltas.maxByOrNull { it.second }?.second?.takeIf { it > 0 }?.let { return it }
        if (deltas.isNotEmpty()) return 0 // All matching layers were idle this window.

        return candidates.mapNotNull { layer ->
            layer.averageFps?.takeIf { it.isFinite() && it in 1.0..360.0 }?.roundToInt()
        }.maxOrNull()
    }

    private fun parseLayerTimestats(lines: List<String>): List<LayerTimestats> {
        val parsed = mutableListOf<LayerTimestats>()
        var layerName: String? = null
        var packageNameInDump: String? = null
        var totalFrames: Long? = null
        var averageFps: Double? = null

        fun flush() {
            val name = layerName
            val frames = totalFrames
            if (!name.isNullOrBlank() && frames != null && frames >= 0L) {
                parsed += LayerTimestats(name, packageNameInDump, frames, averageFps)
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("layerName", ignoreCase = true) -> {
                    flush()
                    layerName = line.substringAfter('=', "").trim().trim('"', '\'')
                    packageNameInDump = null
                    totalFrames = null
                    averageFps = null
                }
                line.startsWith("packageName", ignoreCase = true) -> {
                    packageNameInDump = line.substringAfter('=', "").trim().trim('"', '\'')
                }
                line.startsWith("totalFrames", ignoreCase = true) -> {
                    totalFrames = line.substringAfter('=', "").trim().trimEnd(',', ';').toLongOrNull()
                }
                line.startsWith("averageFPS", ignoreCase = true) -> {
                    averageFps = line.substringAfter('=', "").trim().trimEnd(',', ';').toDoubleOrNull()
                }
            }
        }
        flush()
        return parsed
    }

    private fun layerPriority(name: String): Int = when {
        name.contains("Starting", ignoreCase = true) || name.contains("Splash", ignoreCase = true) -> 4
        name.contains("SurfaceView", ignoreCase = true) -> 0
        name.contains("BLAST", ignoreCase = true) -> 1
        name.contains("BufferQueue", ignoreCase = true) -> 2
        else -> 3
    }

    /** Returns true if timestamp is recent in MONOTONIC or BOOTTIME clock domain. */
    private fun isRecentTimestamp(timestampNanos: Long): Boolean {
        if (timestampNanos <= 0L || timestampNanos == Long.MAX_VALUE) return false
        val nowCandidates = longArrayOf(System.nanoTime(), SystemClock.elapsedRealtimeNanos())
        return nowCandidates.any { now ->
            val age = now - timestampNanos
            age in 0L..MAX_SAMPLE_AGE_NS
        }
    }

    private fun calculateFps(sortedTimestamps: List<Long>): Int? {
        if (sortedTimestamps.size < 2) return null
        val recent = sortedTimestamps.takeLast(FRAME_WINDOW)
        if (recent.size < 2) return null
        val elapsedNanos = recent.last() - recent.first()
        if (elapsedNanos <= 0L) return null

        val fps = ((recent.size - 1) * NANOS_PER_SECOND) / elapsedNanos.toDouble()
        if (fps.isNaN() || fps.isInfinite() || fps !in 1.0..360.0) return null
        return fps.roundToInt().coerceIn(1, 360)
    }
}
