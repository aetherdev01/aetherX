package com.aether.x.core.shell

import com.aether.x.core.adb.AdbConnectionManager
import com.aether.x.core.adb.AdbConnectionState

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

    /**
     * FIX ("Perintah gagal dijalankan di perangkat ini" muncul terus
     * walau status Izin Akses menunjukkan Terhubung): sebelumnya satu kali
     * kegagalan I/O (mis. socket sudah putus diam-diam di background)
     * langsung dilaporkan sebagai gagal permanen ke pengguna, walau
     * koneksinya sebenarnya bisa dipulihkan otomatis lewat pairing yang
     * masih tersimpan (TANPA perlu pairing ulang dari nol — lihat KDoc
     * [AdbConnectionManager.autoReconnect]). Sekarang: kegagalan I/O
     * pertama menandai koneksi putus + memicu reconnect otomatis lewat
     * [AdbConnectionManager.markStreamFailureAndReconnect], lalu perintah
     * yang SAMA dicoba ulang SATU KALI setelah reconnect selesai — kalau
     * reconnect berhasil (kasus paling umum: port TCP basi setelah layar
     * mati lama), pengguna tidak pernah melihat toast gagal sama sekali,
     * tweak langsung berhasil di percobaan kedua yang transparan ini.
     *
     * FIX KEDUA (bug "toast 'Perintah gagal dijalankan' padahal Wireless
     * debugging aktif, cuma butuh waktu lebih", lihat laporan pengguna):
     * SEBELUMNYA setelah memicu reconnect, di sini hanya `delay(1500)`
     * TETAP lalu mengecek [AdbConnectionManager.isConnected] sekali —
     * reconnect asli bisa memakan waktu jauh lebih lama dari 1.5 detik
     * kalau sampai perlu rediscovery mDNS (bisa puluhan detik total),
     * sehingga command SERING dianggap gagal padahal reconnect-nya
     * sendiri masih berjalan dan akan berhasil sesaat lagi. Sekarang
     * [AdbConnectionManager.markStreamFailureAndReconnect] adalah
     * `suspend fun` yang benar-benar DITUNGGU sampai reconnect selesai
     * (baik berhasil maupun gagal final) — retry command kedua baru
     * dicoba SETELAH hasil sungguhan diketahui, bukan tebakan waktu.
     */
    override suspend fun exec(command: String): ShellResult {
        val first = execOnce(command)
        if (first != null) return first

        // Percobaan pertama gagal karena I/O, ATAU koneksi memang belum
        // aktif sama sekali saat dicoba (mis. baru saja putus diam-diam
        // sebelum tombol ditekan) — kedua kasus ini kandidat retry yang
        // sama. Ditunggu sampai reconnect BENAR-BENAR selesai (bukan
        // delay tetap yang bisa terlalu pendek untuk rediscovery mDNS).
        val reconnectResult = AdbConnectionManager.markStreamFailureAndReconnect()

        if (reconnectResult !is AdbConnectionState.Connected) {
            return ShellResult.failure("Belum terhubung ke ADB tertanam AetherX.")
        }
        return execOnce(command) ?: ShellResult.failure(
            "Koneksi ADB tertanam terputus di tengah eksekusi dan gagal disambungkan ulang.",
        )
    }

    /** Satu percobaan eksekusi mentah. Null berarti kandidat retry (belum
     *  terhubung ATAU gagal I/O di tengah eksekusi); bukan null berarti
     *  perintah benar-benar selesai dieksekusi (sukses ATAUPUN exit code gagal). */
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
