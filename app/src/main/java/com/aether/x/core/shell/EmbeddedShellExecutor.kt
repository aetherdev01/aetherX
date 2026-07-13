package com.aether.x.core.shell

import com.aether.x.core.adb.AdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * REWORK TOTAL PERMISSION (lihat perintah rework — "buatkan sistem
 * seperti shizuku langsung tertanam dalam aplikasinya... untuk shell
 * tweak berjalan normal layaknya shizuku"): menggantikan
 * ShizukuShellExecutor (DIHAPUS) sepenuhnya.
 *
 * Menjalankan perintah shell lewat koneksi ADB tertanam AetherX
 * ([AdbConnectionManager]) — secara fungsional PERSIS seperti
 * ShizukuShellExecutor sebelumnya (UID `shell`, akses ke perintah seperti
 * `settings put`, `wm`, `cmd`, `pm`, dll yang tidak butuh root), bedanya
 * koneksi socket ADB-nya dikelola langsung oleh AetherX sendiri tanpa
 * bergantung app Shizuku eksternal.
 *
 * Semua caller yang sebelumnya memakai [ShellExecutor] (interface yang
 * SAMA, tidak berubah) lewat `PrivilegeManager.getExecutor()` tidak perlu
 * ubah kode apa pun — abstraksi ShellExecutor inilah yang membuat rework
 * backend ini tidak menyentuh seluruh kode tweak yang sudah ada.
 */
class EmbeddedShellExecutor : ShellExecutor {

    override val backendName: String = "ADB"

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val dadb = AdbConnectionManager.currentDadb()
            ?: return@withContext ShellResult.failure("Belum terhubung ke ADB tertanam AetherX.")
        try {
            val response = dadb.shell(command)
            ShellResult(
                success = response.exitCode == 0,
                output = response.output.lineSequence().filter { it.isNotEmpty() }.toList(),
                error = if (response.exitCode != 0) {
                    response.output.lineSequence().filter { it.isNotEmpty() }.toList()
                } else {
                    emptyList()
                },
            )
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "Gagal menjalankan perintah lewat ADB tertanam")
        }
    }
}
