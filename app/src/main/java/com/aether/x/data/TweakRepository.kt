package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

enum class CpuGovernor(val sysfsName: String?) {
    SCHEDUTIL("schedutil"),
    PERFORMANCE("performance"),
    ONDEMAND("ondemand"),
    POWERSAVE("powersave"),
    UNIVERSAL(null),
}

class TweakRepository {

    suspend fun applyDensity(executor: ShellExecutor, dpi: Int): ShellResult =
        executor.exec("wm density $dpi")

    suspend fun resetDensity(executor: ShellExecutor): ShellResult =
        executor.exec("wm density reset")

    suspend fun applySize(executor: ShellExecutor, widthPx: Int, heightPx: Int): ShellResult =
        executor.exec("wm size ${widthPx}x${heightPx}")

    suspend fun resetSize(executor: ShellExecutor): ShellResult =
        executor.exec("wm size reset")

    suspend fun applyPointerSpeed(executor: ShellExecutor, speed: Int): ShellResult =
        executor.exec("settings put system pointer_speed $speed")

    suspend fun applyTouchBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val value = if (enabled) 1 else 0
        return executor.exec("settings put secure touch_sensitivity_enable $value")
    }

    suspend fun applyRefreshRate(executor: ShellExecutor, enabled: Boolean, maxHz: Float): ShellResult {
        return if (enabled) {
            executor.exec("settings put system peak_refresh_rate $maxHz; settings put system min_refresh_rate $maxHz")
        } else {
            executor.exec("settings delete system peak_refresh_rate; settings delete system min_refresh_rate")
        }
    }

    suspend fun applyRotationLock(executor: ShellExecutor, locked: Boolean): ShellResult {
        val value = if (locked) 0 else 1
        return executor.exec("settings put system accelerometer_rotation $value")
    }

    suspend fun applyGameMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val mode = if (enabled) "priority" else "off"
        val zenValue = if (enabled) 2 else 0
        val primary = executor.exec("cmd notification set_dnd $mode")
        if (primary.success) return primary

        return executor.exec("settings put global zen_mode $zenValue")
    }

    suspend fun applyCpuGovernor(executor: ShellExecutor, governor: CpuGovernor): ShellResult {
        val script = if (governor == CpuGovernor.UNIVERSAL) {
            """
            for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
              avail_file="${'$'}(dirname ${'$'}g)/scaling_available_governors"
              avail="${'$'}(cat ${'$'}avail_file 2>/dev/null)"
              chosen="schedutil"
              for candidate in schedutil interactive walt ondemand conservative; do
                case " ${'$'}avail " in
                  *" ${'$'}candidate "*) chosen="${'$'}candidate"; break ;;
                esac
              done
              echo ${'$'}chosen > ${'$'}g 2>/dev/null
            done
            """.trimIndent()
        } else {
            val name = governor.sysfsName
            """
            for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
              echo $name > ${'$'}g 2>/dev/null
            done
            """.trimIndent()
        }
        return executor.exec(script)
    }

    suspend fun applyRamPriority(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val value = if (enabled) 10 else 60
        return executor.exec("echo $value > /proc/sys/vm/swappiness 2>/dev/null")
    }

    suspend fun applyThermalThrottleOverride(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                """
                for z in /sys/class/thermal/thermal_zone*/trip_point_0_temp; do
                  echo 90000 > ${'$'}z 2>/dev/null
                done
                """.trimIndent(),
            )
        } else {
            executor.exec("echo 'thermal override dimatikan, restart perangkat untuk memulihkan batas asli vendor'")
        }
    }

    suspend fun applyGpuPerformanceMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val devfreqGovernor = if (enabled) "performance" else "simple_ondemand"
        val misGovernor = if (enabled) "performance" else "default"
        val gedBlock = if (enabled) {
            """
            if [ -f /sys/module/ged/parameters/gpu_freq_bound ] && [ -f /proc/gpufreq/gpufreq_opp_freq ]; then
              max_freq="${'$'}(head -n1 /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | awk '{print ${'$'}1}')"
              [ -n "${'$'}max_freq" ] && echo ${'$'}max_freq > /sys/module/ged/parameters/gpu_freq_bound 2>/dev/null
            fi
            """.trimIndent()
        } else {
            """
            [ -f /sys/module/ged/parameters/gpu_freq_bound ] && echo 0 > /sys/module/ged/parameters/gpu_freq_bound 2>/dev/null
            """.trimIndent()
        }
        val script = """
            # Qualcomm/Adreno
            for g in /sys/class/kgsl/kgsl-3d0/devfreq/governor /sys/devices/platform/*/kgsl-3d0/devfreq/governor; do
              [ -f ${'$'}g ] && echo $devfreqGovernor > ${'$'}g 2>/dev/null
            done
            # MediaTek/Mali & Exynos/Mali — skema devfreq umum
            for g in /sys/class/devfreq/*mali*/governor /sys/devices/platform/*/devfreq/*mali*/governor; do
              [ -f ${'$'}g ] && echo $devfreqGovernor > ${'$'}g 2>/dev/null
            done
            # Mali generik lewat node misc
            for g in /sys/class/misc/mali0/device/dvfs_governor; do
              [ -f ${'$'}g ] && echo $misGovernor > ${'$'}g 2>/dev/null
            done
            # MediaTek GED (Dimensity modern) — kunci/lepas batas frekuensi opp tertinggi
            $gedBlock
        """.trimIndent()
        return executor.exec(script)
    }

    suspend fun applyGpuRenderingPriority(
        executor: ShellExecutor,
        packageName: String,
        enabled: Boolean,
    ): ShellResult {
        val script = if (enabled) {
            """
            # 1) SurfaceFlinger (compositor sistem, satu proses untuk seluruh sistem)
            sf_pid="${'$'}(pgrep -x surfaceflinger 2>/dev/null | head -n1)"
            [ -n "${'$'}sf_pid" ] && chrt -f -p 50 ${'$'}sf_pid 2>/dev/null

            # 2) RenderThread milik game (dicari lewat TID, bukan PID utama)
            game_pid="${'$'}(pgrep -x $packageName 2>/dev/null | head -n1)"
            if [ -n "${'$'}game_pid" ]; then
              for t in /proc/${'$'}game_pid/task/*; do
                tid="${'$'}(basename ${'$'}t)"
                name="${'$'}(cat ${'$'}t/comm 2>/dev/null)"
                if [ "${'$'}name" = "RenderThread" ]; then
                  chrt -f -p 50 ${'$'}tid 2>/dev/null
                fi
              done
            fi
            """.trimIndent()
        } else {
            """
            sf_pid="${'$'}(pgrep -x surfaceflinger 2>/dev/null | head -n1)"
            [ -n "${'$'}sf_pid" ] && chrt -o -p 0 ${'$'}sf_pid 2>/dev/null

            game_pid="${'$'}(pgrep -x $packageName 2>/dev/null | head -n1)"
            if [ -n "${'$'}game_pid" ]; then
              for t in /proc/${'$'}game_pid/task/*; do
                tid="${'$'}(basename ${'$'}t)"
                name="${'$'}(cat ${'$'}t/comm 2>/dev/null)"
                if [ "${'$'}name" = "RenderThread" ]; then
                  chrt -o -p 0 ${'$'}tid 2>/dev/null
                fi
              done
            fi
            """.trimIndent()
        }
        return executor.exec(script)
    }

    suspend fun applyDozeDisable(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val mode = if (enabled) "disable" else "enable"
        return executor.exec("dumpsys deviceidle $mode")
    }

    suspend fun applyIoSchedulerBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                "for q in /sys/block/*/queue/scheduler; do " +
                    "(echo kyber > \$q 2>/dev/null || echo bfq > \$q 2>/dev/null); done",
            )
        } else {
            executor.exec(
                "for q in /sys/block/*/queue/scheduler; do " +
                    "echo mq-deadline > \$q 2>/dev/null; done",
            )
        }
    }

    suspend fun applyKillBackgroundApps(executor: ShellExecutor, enabled: Boolean): ShellResult {
        if (!enabled) return ShellResult(success = true)
        return executor.exec(
            "for p in \$(pm list packages -3 | sed 's/package://'); do am kill \$p; done",
        )
    }

    suspend fun applyVmHeapBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                "setprop dalvik.vm.heapgrowthlimit 512m; setprop dalvik.vm.heapsize 1024m",
            )
        } else {
            executor.exec(
                "setprop dalvik.vm.heapgrowthlimit 256m; setprop dalvik.vm.heapsize 512m",
            )
        }
    }

    suspend fun resetAll(executor: ShellExecutor): List<ShellResult> = listOf(
        resetDensity(executor),
        resetSize(executor),
        applyPointerSpeed(executor, 0),
        applyTouchBoost(executor, false),
        applyRefreshRate(executor, enabled = false, maxHz = 60f),
        applyGameMode(executor, enabled = false),
        applyCpuGovernor(executor, CpuGovernor.UNIVERSAL),
        applyRamPriority(executor, enabled = false),
        applyThermalThrottleOverride(executor, enabled = false),
        applyGpuPerformanceMode(executor, enabled = false),
        applyIoSchedulerBoost(executor, enabled = false),
        applyVmHeapBoost(executor, enabled = false),
        applyDozeDisable(executor, enabled = false),
    )

    suspend fun applyGameProfile(
        executor: ShellExecutor,
        profile: GameProfile,
    ): List<ShellResult> = listOf(
        applyCpuGovernor(
            executor,
            if (profile.cpuPerformanceMode) CpuGovernor.PERFORMANCE else CpuGovernor.UNIVERSAL,
        ),
        applyRamPriority(executor, profile.ramPriorityMode),
        applyThermalThrottleOverride(executor, profile.thermalThrottleOverride),
        applyGpuPerformanceMode(executor, profile.gpuPerformanceMode),
        applyIoSchedulerBoost(executor, profile.ioSchedulerBoost),
        applyVmHeapBoost(executor, profile.vmHeapBoost),
        applyGpuRenderingPriority(executor, profile.packageName, profile.gpuRenderingPriority),
    )

    suspend fun resetRootTweaksOnly(executor: ShellExecutor, packageName: String? = null): List<ShellResult> = buildList {
        add(applyCpuGovernor(executor, CpuGovernor.UNIVERSAL))
        add(applyRamPriority(executor, enabled = false))
        add(applyThermalThrottleOverride(executor, enabled = false))
        add(applyGpuPerformanceMode(executor, enabled = false))
        add(applyIoSchedulerBoost(executor, enabled = false))
        add(applyVmHeapBoost(executor, enabled = false))
        if (packageName != null) {
            add(applyGpuRenderingPriority(executor, packageName, enabled = false))
        }
    }
}
