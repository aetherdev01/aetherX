package com.aether.x.core.booster

import android.os.Environment
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.CpuGovernor
import com.aether.x.data.GameMode
import com.aether.x.data.TweakRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameBoosterActionHandler(private val repository: TweakRepository = TweakRepository()) {

    suspend fun applyMode(executor: ShellExecutor, mode: GameMode) {
        val governor = when (mode) {
            GameMode.LOW -> CpuGovernor.POWERSAVE
            GameMode.MID -> CpuGovernor.UNIVERSAL
            GameMode.BOOST -> CpuGovernor.PERFORMANCE
        }
        repository.applyCpuGovernor(executor, governor)
        repository.applyGpuPerformanceMode(executor, enabled = mode == GameMode.BOOST)
    }

    suspend fun applyDnd(executor: ShellExecutor, enabled: Boolean) {
        repository.applyGameMode(executor, enabled)
    }

    suspend fun applyRotationLock(executor: ShellExecutor, locked: Boolean) {
        repository.applyRotationLock(executor, locked)
    }

    suspend fun applyTouchBoost(executor: ShellExecutor, enabled: Boolean) {
        repository.applyTouchBoost(executor, enabled)
    }

    suspend fun applyMaxRefreshRate(executor: ShellExecutor, maxHz: Float) {
        repository.applyRefreshRate(executor, enabled = true, maxHz = maxHz)
    }

    suspend fun takeScreenshot(executor: ShellExecutor): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val screenshotDir = "${Environment.getExternalStorageDirectory().path}/Pictures/Screenshots"
        val filePath = "$screenshotDir/AetherX_$timestamp.png"
        val script = """
            mkdir -p "$screenshotDir"
            screencap -p "$filePath"
        """.trimIndent()
        val result = executor.exec(script)
        return if (result.success) filePath else null
    }
}
