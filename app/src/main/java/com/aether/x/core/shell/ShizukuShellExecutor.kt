package com.aether.x.core.shell

import com.aether.x.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * ROLLBACK dari ADB tertanam ke Shizuku murni — pengganti
 * `EmbeddedShellExecutor` (DIHAPUS). Lihat KDoc [ShizukuManager] untuk
 * konteks lengkap.
 *
 * Menjalankan perintah shell lewat [Shizuku.newProcess] — proses baru yang
 * dibuat oleh service Shizuku (berjalan dengan UID `shell`, PERSIS sama
 * seperti proses ADB shell biasa, sehingga akses ke perintah seperti
 * `settings put`, `wm`, `cmd`, `pm`, dll tidak berubah sama sekali
 * dibanding sebelumnya — hanya CARA mendapatkan shell UID ini yang beda).
 *
 * Semua caller yang memakai [ShellExecutor] (interface yang SAMA, tidak
 * berubah) lewat `PrivilegeManager.getExecutor()` tidak perlu ubah kode
 * apa pun — abstraksi inilah yang membuat rollback backend ini tidak
 * menyentuh seluruh kode tweak yang sudah ada.
 */
class ShizukuShellExecutor : ShellExecutor {

    override val backendName: String = "Shizuku"

    /**
     * Beda dari [EmbeddedShellExecutor] (yang perlu retry+reconnect
     * manual untuk port TCP yang bisa basi), koneksi Shizuku TIDAK PERNAH
     * "basi" selama service-nya sendiri masih hidup — binder Android
     * otomatis mati bersih (lewat [Shizuku.addBinderDeadListener], sudah
     * ditangani di [ShizukuManager]) begitu proses Shizuku berhenti,
     * bukan diam-diam gagal di tengah jalan seperti socket TCP. Karena
     * itu, executor ini TIDAK butuh retry/reconnect kompleks seperti versi
     * ADB tertanam sebelumnya — cukup cek [ShizukuManager.isServiceRunning]
     * & [ShizukuManager.hasPermission] sekali di awal.
     */
    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isServiceRunning()) {
            return@withContext ShellResult.failure("Service Shizuku tidak berjalan. Buka Shizuku Manager dan start dulu.")
        }
        if (!ShizukuManager.hasPermission()) {
            return@withContext ShellResult.failure("AetherX belum diberi izin Shizuku.")
        }

        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val outputLines = output.lineSequence().filter { it.isNotEmpty() }.toList()
            val errorLines = errorOutput.lineSequence().filter { it.isNotEmpty() }.toList()

            ShellResult(
                success = exitCode == 0,
                output = outputLines,
                error = if (exitCode != 0) (errorLines.ifEmpty { outputLines }) else errorLines,
            )
        } catch (t: Throwable) {
            ShellResult.failure("Perintah Shizuku gagal dieksekusi: ${t.message ?: "kesalahan tidak diketahui"}")
        }
    }
}
