package com.aether.x.core.monitor

import com.aether.x.core.shell.RootShellExecutor
import kotlin.math.roundToInt

/**
 * Device-independent GPU reader using root-readable devfreq/KGSL nodes.
 *
 * It discovers common Adreno, Mali/Xclipse and generic devfreq GPU nodes at
 * runtime instead of depending on one SoC-specific absolute path.
 */
class RootGpuSysfsReader(
    private val shell: RootShellExecutor = RootShellExecutor(),
) {
    private var previousBusy: Long = -1L
    private var previousTotal: Long = -1L
    private var previousCounterPath: String? = null

    fun reset() {
        previousBusy = -1L
        previousTotal = -1L
        previousCounterPath = null
    }

    suspend fun read(): GpuLoadSnapshot? {
        val dollar = '$'
        val command = buildString {
            append("for d in /sys/class/devfreq/* /sys/class/kgsl/kgsl-3d0; do ")
            append("[ -d \"${dollar}d\" ] || continue; ")
            append("case \"${dollar}d\" in *gpu*|*GPU*|*mali*|*MALI*|*xclipse*|*XCLIPSE*|*kgsl*|*KGSL*|*3d*|*3D*|*sgpu*|*SGPU*) ;; *) continue ;; esac; ")
            append("for f in gpu_busy_percentage gpu_busy busy_percent load utilization cur_freq busy_time total_time; do ")
            append("[ -r \"${dollar}d/${dollar}f\" ] && printf '%s=%s\\n' \"${dollar}d/${dollar}f\" \"$(cat \"${dollar}d/${dollar}f\" 2>/dev/null)\"; ")
            append("done; done")
        }

        val result = runCatching { shell.exec(command) }.getOrNull() ?: return null
        if (!result.success && result.output.isEmpty()) return null

        val values = result.output.mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) return@mapNotNull null
            line.substring(0, split) to line.substring(split + 1).trim()
        }

        val directLoad = values.firstNotNullOfOrNull { (path, raw) ->
            if (!path.endsWith("gpu_busy_percentage") &&
                !path.endsWith("gpu_busy") &&
                !path.endsWith("busy_percent") &&
                !path.endsWith("/load") &&
                !path.endsWith("/utilization")
            ) return@firstNotNullOfOrNull null
            extractNumber(raw)?.takeIf { it in 0f..100f }
        }

        val counterPath = values.firstOrNull { (path, _) -> path.endsWith("/busy_time") }?.first
        val busy = values.firstNotNullOfOrNull { (path, raw) ->
            if (path == counterPath) extractLong(raw) else null
        }
        val totalPath = counterPath?.removeSuffix("/busy_time")?.plus("/total_time")
        val total = values.firstNotNullOfOrNull { (path, raw) ->
            if (path == totalPath) extractLong(raw) else null
        }

        val counterLoad = if (busy != null && total != null && total > 0L && busy >= 0L) {
            val validPrevious = previousCounterPath == counterPath && previousBusy >= 0L && previousTotal >= 0L
            val value = if (validPrevious) {
                val busyDelta = busy - previousBusy
                val totalDelta = total - previousTotal
                if (busyDelta >= 0L && totalDelta > 0L) {
                    (busyDelta.toDouble() / totalDelta.toDouble() * 100.0)
                        .coerceIn(0.0, 100.0)
                        .toFloat()
                } else -1f
            } else -1f

            previousBusy = busy
            previousTotal = total
            previousCounterPath = counterPath
            value
        } else {
            -1f
        }

        val load = directLoad ?: counterLoad.takeIf { it >= 0f }
        val freq = values.firstNotNullOfOrNull { (path, raw) ->
            if (!path.endsWith("/cur_freq")) return@firstNotNullOfOrNull null
            extractNumber(raw)?.takeIf { it > 0f }
        }?.let { normalizeFrequencyMhz(it) }

        if (load == null && freq == null) return null
        return GpuLoadSnapshot(
            loadPercent = load?.roundToInt()?.toFloat()?.coerceIn(0f, 100f),
            freqMhz = freq,
        )
    }

    private fun extractNumber(text: String): Float? =
        Regex("-?\\d+(?:\\.\\d+)?").find(text)?.value?.toFloatOrNull()

    private fun extractLong(text: String): Long? =
        Regex("\\d+").find(text)?.value?.toLongOrNull()

    private fun normalizeFrequencyMhz(raw: Float): Float = when {
        raw >= 1_000_000f -> raw / 1_000_000f
        raw >= 10_000f -> raw / 1_000f
        else -> raw
    }
}
