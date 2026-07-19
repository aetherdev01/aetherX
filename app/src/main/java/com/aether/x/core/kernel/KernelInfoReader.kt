package com.aether.x.core.kernel

import com.aether.x.core.shell.ShellExecutor
import kotlin.math.roundToInt

class KernelInfoReader {

    suspend fun readCpuCores(executor: ShellExecutor): List<CpuCoreInfo> {

        val countResult = executor.exec(
            "ls -d /sys/devices/system/cpu/cpu[0-9]*/cpufreq 2>/dev/null | wc -l",
        )
        val coreCount = countResult.outputText.trim().toIntOrNull() ?: 0
        if (coreCount <= 0) return emptyList()

        val script = buildString {
            for (i in 0 until coreCount) {
                val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
                appendLine("echo ===CORE_$i===")
                appendLine("cat $base/scaling_cur_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_min_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_max_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_available_frequencies 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_governor 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_available_governors 2>/dev/null")
            }
        }
        val result = executor.exec(script)
        return parseCoreBlocks(result.output, coreCount)
    }

    private fun parseCoreBlocks(lines: List<String>, coreCount: Int): List<CpuCoreInfo> {

        val cores = mutableListOf<CpuCoreInfo>()
        var currentIndex = -1
        var fields = mutableListOf<MutableList<String>>()

        fun flush() {
            if (currentIndex < 0) return

            val curFreq = fields.getOrNull(0)?.firstOrNull()?.trim()?.toIntOrNull()
            val minFreq = fields.getOrNull(1)?.firstOrNull()?.trim()?.toIntOrNull()
            val maxFreq = fields.getOrNull(2)?.firstOrNull()?.trim()?.toIntOrNull()
            val availFreq = fields.getOrNull(3)
                ?.joinToString(" ")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
                .distinct()
                .sorted()
            val governor = fields.getOrNull(4)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val availGovernors = fields.getOrNull(5)
                ?.joinToString(" ")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .distinct()

            cores += CpuCoreInfo(
                coreIndex = currentIndex,
                currentFreqKhz = curFreq,
                minFreqKhz = minFreq,
                maxFreqKhz = maxFreq,
                availableFrequenciesKhz = availFreq,
                currentGovernor = governor,
                availableGovernors = availGovernors,
            )
        }

        var fieldIndex = 0
        for (raw in lines) {
            val markerMatch = Regex("^===CORE_(\\d+)===$").find(raw.trim())
            if (markerMatch != null) {
                flush()
                currentIndex = markerMatch.groupValues[1].toInt()
                fields = MutableList(6) { mutableListOf() }
                fieldIndex = 0
                continue
            }
            if (raw.trim() == "---") {
                fieldIndex = (fieldIndex + 1).coerceAtMost(5)
                continue
            }
            if (currentIndex >= 0 && fieldIndex < fields.size) {
                fields[fieldIndex].add(raw)
            }
        }
        flush()

        val byIndex = cores.associateBy { it.coreIndex }
        return (0 until coreCount).map { i ->
            byIndex[i] ?: CpuCoreInfo(
                coreIndex = i,
                currentFreqKhz = null,
                minFreqKhz = null,
                maxFreqKhz = null,
                availableFrequenciesKhz = emptyList(),
                currentGovernor = null,
                availableGovernors = emptyList(),
            )
        }
    }

    suspend fun readGpuInfo(executor: ShellExecutor): GpuInfo {
        val script = """
            for p in /sys/class/kgsl/kgsl-3d0/devfreq /sys/devices/platform/*/kgsl-3d0/devfreq \
                     /sys/class/devfreq/*mali* /sys/devices/platform/*/devfreq/*mali*; do
              if [ -d "${'$'}p" ]; then
                echo "===PATH===${'$'}p"
                cat "${'$'}p/cur_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/min_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/max_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/available_frequencies" 2>/dev/null
                echo ---
                cat "${'$'}p/governor" 2>/dev/null
                echo ---
                cat "${'$'}p/available_governors" 2>/dev/null
                break
              fi
            done
        """.trimIndent()
        val result = executor.exec(script)
        return parseGpuBlock(result.output)
    }

    private fun parseGpuBlock(lines: List<String>): GpuInfo {
        if (lines.isEmpty() || !lines.first().startsWith("===PATH===")) {
            return GpuInfo(null, null, null, null, emptyList(), null, emptyList())
        }
        val path = lines.first().removePrefix("===PATH===").trim()
        val fields = MutableList(6) { mutableListOf<String>() }
        var fieldIndex = 0
        for (raw in lines.drop(1)) {
            if (raw.trim() == "---") {
                fieldIndex = (fieldIndex + 1).coerceAtMost(5)
                continue
            }
            if (fieldIndex < fields.size) fields[fieldIndex].add(raw)
        }
        val curFreq = fields[0].firstOrNull()?.trim()?.toIntOrNull()
        val minFreq = fields[1].firstOrNull()?.trim()?.toIntOrNull()
        val maxFreq = fields[2].firstOrNull()?.trim()?.toIntOrNull()
        val availFreq = fields[3].joinToString(" ").trim()
            .split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.distinct().sorted()
        val governor = fields[4].firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val availGovernors = fields[5].joinToString(" ").trim()
            .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

        return GpuInfo(
            devfreqPath = path.takeIf { it.isNotBlank() },
            currentFreqKhz = curFreq,
            minFreqKhz = minFreq,
            maxFreqKhz = maxFreq,
            availableFrequenciesKhz = availFreq,
            currentGovernor = governor,
            availableGovernors = availGovernors,
        )
    }

    suspend fun readThermalZones(executor: ShellExecutor): List<ThermalZoneInfo> {
        val script = """
            for z in /sys/class/thermal/thermal_zone*; do
              idx=${'$'}(basename "${'$'}z" | tr -dc '0-9')
              type=${'$'}(cat "${'$'}z/type" 2>/dev/null)
              temp=${'$'}(cat "${'$'}z/temp" 2>/dev/null)
              echo "===ZONE_${'$'}{idx}===${'$'}{type}===${'$'}{temp}"
            done
        """.trimIndent()
        val result = executor.exec(script)
        return result.output.mapNotNull { line ->
            val match = Regex("^===ZONE_(\\d+)===(.*)===(-?\\d+)$").find(line.trim()) ?: return@mapNotNull null
            val (idxStr, type, tempStr) = match.destructured
            val rawTemp = tempStr.toIntOrNull() ?: return@mapNotNull null

            val celsius = if (rawTemp in 0..150) rawTemp.toFloat() else rawTemp / 1000f
            ThermalZoneInfo(zoneIndex = idxStr.toInt(), type = type.trim(), temperatureCelsius = celsius)
        }.sortedBy { it.zoneIndex }
    }

    suspend fun readKernelVersion(executor: ShellExecutor): String? {
        val result = executor.exec("uname -r")
        return result.output.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun readCpuLoadPercent(executor: ShellExecutor): Int? {
        val script = """
            cat /proc/stat | head -1
            sleep 0.3
            cat /proc/stat | head -1
        """.trimIndent()
        val result = executor.exec(script)
        val lines = result.output.map { it.trim() }.filter { it.startsWith("cpu ") || it.startsWith("cpu\t") }
        if (lines.size < 2) return null

        fun parse(line: String): Pair<Long, Long>? {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0] != "cpu") return null
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return null
            val idle = values[3] + (values.getOrNull(4) ?: 0L)
            val total = values.sum()
            return total to idle
        }

        val (total1, idle1) = parse(lines[0]) ?: return null
        val (total2, idle2) = parse(lines[1]) ?: return null
        val totalDelta = total2 - total1
        val idleDelta = idle2 - idle1
        if (totalDelta <= 0) return null
        val busyPercent = ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
        return busyPercent.roundToIntClamped()
    }

    suspend fun readGpuBusyPercent(executor: ShellExecutor): Int? {
        val script = """
            for p in /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage \
                     /sys/kernel/gpu/gpu_busy \
                     /sys/class/devfreq/gpufreq/gpu_busy; do
              if [ -r "${'$'}p" ]; then
                cat "${'$'}p"
                break
              fi
            done
        """.trimIndent()
        val result = executor.exec(script)
        val text = result.outputText.trim()
        val numeric = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: return null
        return numeric.coerceIn(0, 100)
    }

    private fun Float.roundToIntClamped(): Int = this.roundToInt().coerceIn(0, 100)

    suspend fun readSnapshot(executor: ShellExecutor): KernelSnapshot = KernelSnapshot(
        cpuCores = readCpuCores(executor),
        gpu = readGpuInfo(executor),
        thermalZones = readThermalZones(executor),
        kernelVersion = readKernelVersion(executor),
    )
}
