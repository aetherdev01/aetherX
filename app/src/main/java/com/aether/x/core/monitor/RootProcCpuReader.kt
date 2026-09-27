package com.aether.x.core.monitor

import com.aether.x.core.shell.RootShellExecutor
import kotlin.math.roundToInt

/**
 * Root fallback CPU reader.
 *
 * Some Android builds restrict the app UID from reading /proc/stat even though
 * the device is rooted. The native reader is still preferred for speed, but
 * this reader uses the already-authorized root shell as a fallback.
 */
class RootProcCpuReader(
    private val shell: RootShellExecutor = RootShellExecutor(),
) {
    private var previousTotal = -1L
    private var previousIdle = -1L
    private val previousCores = mutableMapOf<Int, Pair<Long, Long>>()

    fun reset() {
        previousTotal = -1L
        previousIdle = -1L
        previousCores.clear()
    }

    suspend fun read(): CpuLoadSnapshot? {
        val result = runCatching {
            shell.exec("cat /proc/stat 2>/dev/null")
        }.getOrNull() ?: return null

        if (!result.success && result.output.isEmpty()) return null

        val aggregate = parseCpuLine(result.output.firstOrNull { it.trimStart().startsWith("cpu ") })
            ?: return null

        val currentTotal = aggregate.first
        val currentIdle = aggregate.second
        val aggregatePercent = percentage(currentTotal, currentIdle, previousTotal, previousIdle)

        previousTotal = currentTotal
        previousIdle = currentIdle

        val perCore = result.output
            .asSequence()
            .mapNotNull { line ->
                val label = line.substringBefore(' ', missingDelimiterValue = "")
                val index = label.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
                val current = parseCpuLine(line) ?: return@mapNotNull null
                index to current
            }
            .sortedBy { it.first }
            .map { (index, current) ->
                val previous = previousCores[index]
                previousCores[index] = current
                percentage(current.first, current.second, previous?.first ?: -1L, previous?.second ?: -1L)
            }
            .toList()

        return CpuLoadSnapshot(
            aggregatePercent = aggregatePercent,
            perCorePercent = perCore,
        )
    }

    private fun percentage(
        total: Long,
        idle: Long,
        previousTotal: Long,
        previousIdle: Long,
    ): Float {
        if (previousTotal < 0L || previousIdle < 0L) return -1f

        val totalDelta = total - previousTotal
        val idleDelta = (idle - previousIdle).coerceAtLeast(0L)
        if (totalDelta <= 0L) return -1f

        return (((totalDelta - idleDelta).toFloat() / totalDelta) * 100f)
            .coerceIn(0f, 100f)
            .roundToInt()
            .toFloat()
    }

    private fun parseCpuLine(line: String?): Pair<Long, Long>? {
        if (line.isNullOrBlank()) return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null

        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        if (values.size < 4) return null

        val total = values.sum()
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        return total to idle
    }
}
