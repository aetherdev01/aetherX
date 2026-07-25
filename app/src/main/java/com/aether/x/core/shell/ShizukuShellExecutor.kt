package com.aether.x.core.shell

import com.aether.x.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

/**
 * ROLLBACK dari ADB tertanam ke Shizuku murni — pengganti
 * `EmbeddedShellExecutor` (DIHAPUS). Lihat KDoc [ShizukuManager] untuk
 * konteks lengkap.
 *
 * Menjalankan perintah shell lewat `Shizuku.newProcess` — proses baru yang
 * dibuat oleh service Shizuku (berjalan dengan UID `shell`, PERSIS sama
 * seperti proses ADB shell biasa, sehingga akses ke perintah seperti
 * `settings put`, `wm`, `cmd`, `pm`, dll tidak berubah sama sekali
 * dibanding sebelumnya — hanya CARA mendapatkan shell UID ini yang beda).
 *
 * Semua caller yang memakai [ShellExecutor] (interface yang SAMA, tidak
 * berubah) lewat `PrivilegeManager.getExecutor()` tidak perlu ubah kode
 * apa pun — abstraksi inilah yang membuat rollback backend ini tidak
 * menyentuh seluruh kode tweak yang sudah ada.
 *
 * FIX BUILD — `Shizuku.newProcess(String[], String[], String)` DIBUAT
 * PRIVATE di shizuku-api 13.x (pengembang Shizuku sedang mendorong migrasi
 * ke UserService, lihat README resmi RikkaApps/Shizuku-API), padahal masih
 * satu-satunya cara sederhana untuk "jalankan 1 command shell dan baca
 * outputnya" tanpa membangun AIDL UserService terpisah. Method tsb TIDAK
 * DIHAPUS dari bytecode (masih ada, cuma visibility-nya diturunkan), jadi
 * dipanggil lewat reflection + `isAccessible = true` — pola ini eksplisit
 * dianjurkan komunitas Shizuku sendiri untuk kasus persis ini (lihat
 * https://github.com/RikkaApps/Shizuku-API/issues/276). Kelas hasil
 * kembaliannya, [ShizukuRemoteProcess], TETAP public (extends
 * java.lang.Process biasa) sehingga TIDAK perlu reflection tambahan untuk
 * membaca `inputStream`/`errorStream`/`waitFor()`.
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
            val process = newProcessViaReflection(arrayOf("sh", "-c", command))
                ?: return@withContext ShellResult.failure("Tidak bisa membuat proses shell lewat Shizuku (API tidak kompatibel).")

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

    private companion object {
        // Cache reflection Method supaya lookup mahal ini hanya dilakukan
        // sekali per proses aplikasi, bukan setiap kali exec() dipanggil.
        @Volatile
        private var cachedMethod: Method? = null

        fun newProcessViaReflection(command: Array<String>): ShizukuRemoteProcess? {
            val method = cachedMethod ?: resolveMethod()?.also { cachedMethod = it } ?: return null
            return method.invoke(null, command, null, null) as? ShizukuRemoteProcess
        }

        private fun resolveMethod(): Method? = runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.getOrNull()
    }
}
