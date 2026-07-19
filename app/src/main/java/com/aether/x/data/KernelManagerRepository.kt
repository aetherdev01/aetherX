package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

class KernelManagerRepository {

    suspend fun setCoreFrequency(
        executor: ShellExecutor,
        coreIndex: Int,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val base = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq"
        val commands = buildList {

            if (maxKhz != null) add("echo $maxKhz > $base/scaling_max_freq 2>/dev/null")
            if (minKhz != null) add("echo $minKhz > $base/scaling_min_freq 2>/dev/null")
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    suspend fun setCoreGovernor(executor: ShellExecutor, coreIndex: Int, governorName: String): ShellResult {
        val base = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq"
        return executor.exec("echo $governorName > $base/scaling_governor 2>/dev/null")
    }

    suspend fun setAllCoresFrequency(
        executor: ShellExecutor,
        coreCount: Int,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val commands = (0 until coreCount).flatMap { i ->
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
            buildList {
                if (maxKhz != null) add("echo $maxKhz > $base/scaling_max_freq 2>/dev/null")
                if (minKhz != null) add("echo $minKhz > $base/scaling_min_freq 2>/dev/null")
            }
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    suspend fun setGpuFrequency(
        executor: ShellExecutor,
        devfreqPath: String,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val commands = buildList {
            if (maxKhz != null) add("echo $maxKhz > $devfreqPath/max_freq 2>/dev/null")
            if (minKhz != null) add("echo $minKhz > $devfreqPath/min_freq 2>/dev/null")
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    suspend fun setGpuGovernor(executor: ShellExecutor, devfreqPath: String, governorName: String): ShellResult {
        return executor.exec("echo $governorName > $devfreqPath/governor 2>/dev/null")
    }
}
