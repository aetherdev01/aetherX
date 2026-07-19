package com.aether.x.core.monitor

import com.aether.x.core.shell.ShellExecutor
import kotlin.math.roundToInt

class GfxInfoFpsReader(private val packageName: String) {

    companion object {

        private const val FRAME_COMPLETED_COLUMN_INDEX = 13
        private const val EXPECTED_MIN_COLUMNS = 14
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val FRAME_WINDOW = 30
    }

    suspend fun readFps(executor: ShellExecutor): Int? {
        val result = executor.exec("dumpsys gfxinfo $packageName framestats")
        if (!result.success) return null

        val timestampsNanos = parseFrameCompletedTimestamps(result.output)
        if (timestampsNanos.size < 2) return null

        val recent = timestampsNanos.takeLast(FRAME_WINDOW)
        if (recent.size < 2) return null

        val elapsedNanos = recent.last() - recent.first()
        if (elapsedNanos <= 0) return null

        val frameIntervals = recent.size - 1
        val fps = (frameIntervals * NANOS_PER_SECOND) / elapsedNanos
        if (fps.isNaN() || fps.isInfinite()) return null

        return fps.roundToInt().coerceIn(0, 240)
    }

    private fun parseFrameCompletedTimestamps(lines: List<String>): List<Long> {
        val timestamps = mutableListOf<Long>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed[0].isDigit()) continue

            val columns = trimmed.split(',')
            if (columns.size <= FRAME_COMPLETED_COLUMN_INDEX) continue
            if (columns.size < EXPECTED_MIN_COLUMNS) continue

            val completedNanos = columns[FRAME_COMPLETED_COLUMN_INDEX].trim().toLongOrNull() ?: continue
            if (completedNanos <= 0) continue
            timestamps.add(completedNanos)
        }
        return timestamps.sorted()
    }
}
