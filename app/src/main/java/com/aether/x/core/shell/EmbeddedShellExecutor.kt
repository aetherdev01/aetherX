package com.aether.x.core.shell

import com.aether.x.core.adb.AdbConnectionManager

/**
 * REWORK TOTAL PERMISSION (lihat perintah rework — "buatkan sistem
 * seperti shizuku langsung tertanam dalam aplikasinya... untuk shell
 * tweak berjalan normal layaknya shizuku"): menggantikan
 * ShizukuShellExecutor (DIHAPUS) sepenuhnya.
 *
 * Menjalankan perintah shell lewat koneksi ADB tertanam AetherX
 * ([AdbConnectionManager], dibangun di atas library `libadb-android`) —
 * secara fungsional PERSIS seperti ShizukuShellExecutor sebelumnya (UID
 * `shell`, akses ke perintah seperti `settings put`, `wm`, `cmd`, `pm`,
 * dll yang tidak butuh root), bedanya koneksi ADB-nya dikelola langsung
 * oleh AetherX sendiri tanpa bergantung app Shizuku eksternal.
 *
 * Semua caller yang sebelumnya memakai [ShellExecutor] (interface yang
 * SAMA, tidak berubah) lewat `PrivilegeManager.getExecutor()` tidak perlu
 * ubah kode apa pun — abstraksi ShellExecutor inilah yang membuat rework
 * backend ini tidak menyentuh seluruh kode tweak yang sudah ada.
 */
class EmbeddedShellExecutor : ShellExecutor {

    override val backendName: String = "ADB"

    override suspend fun exec(command: String): ShellResult {
        if (!AdbConnectionManager.isConnected()) {
            return ShellResult.failure("Belum terhubung ke ADB tertanam AetherX.")
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
            ShellResult.failure(t.message ?: "Gagal menjalankan perintah lewat ADB tertanam")
        }
    }
}
