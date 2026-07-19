package com.aether.x.core.shell

import com.aether.x.core.adb.AdbConnectionManager
import kotlinx.coroutines.delay

class EmbeddedShellExecutor : ShellExecutor {

    override val backendName: String = "ADB"

    override suspend fun exec(command: String): ShellResult {
        val first = execOnce(command)
        if (first != null) return first

        AdbConnectionManager.markStreamFailureAndReconnect()
        delay(1500)

        if (!AdbConnectionManager.isConnected()) {
            return ShellResult.failure("Belum terhubung ke ADB tertanam AetherX.")
        }
        return execOnce(command) ?: ShellResult.failure(
            "Koneksi ADB tertanam terputus di tengah eksekusi dan gagal disambungkan ulang.",
        )
    }

    private suspend fun execOnce(command: String): ShellResult? {
        if (!AdbConnectionManager.isConnected()) {
            return null
        }
        return try {
            val (exitCode, output) = AdbConnectionManager.execShell(command)
            val lines = output.lineSequence().filter { it.isNotEmpty() }.toList()
            ShellResult(
                success = exitCode == 0,
                output = lines,
                error = if (exitCode != 0) lines else emptyList(),
            )
        } catch (t: Throwable) {
            null
        }
    }
}
