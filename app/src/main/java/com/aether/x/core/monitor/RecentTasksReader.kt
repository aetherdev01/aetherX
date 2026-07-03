package com.aether.x.core.monitor

import com.aether.x.core.shell.ShellExecutor

/**
 * Mendeteksi apakah sebuah package MASIH punya task yang hidup di stack
 * recent apps (task terbaru sistem) — dipakai [com.aether.x.core.monitor.GameProfileMonitorService]
 * untuk membedakan dua kondisi yang terlihat mirip dari `ForegroundAppReader`
 * saja:
 *
 * 1. Pengguna menekan tombol Home / pindah app sebentar (game masih hidup di
 *    background, task-nya MASIH ada di recent apps) — tweak profil TIDAK
 *    boleh direset, karena pengguna kemungkinan besar akan kembali bermain.
 * 2. Pengguna menutup/swipe game dari layar recent apps, ATAU game
 *    force-close/crash (task-nya HILANG dari recent apps) — inilah saatnya
 *    tweak profil direset ke default.
 *
 * Sumber data: `dumpsys activity activities`, yang mencantumkan SEMUA task
 * yang diketahui ActivityTaskManager (bukan cuma yang resumed/foreground),
 * masing-masing sebagai blok `TaskRecord{... A=<package> ...}` atau `* Task{...}`
 * tergantung versi Android. Ini perintah resmi yang sama dipakai `adb shell
 * dumpsys activity recents`.
 */
class RecentTasksReader {

    /**
     * true kalau [packageName] MASIH punya task hidup di recent apps (baik
     * sedang di foreground maupun sekadar di-minimize/background), false
     * kalau task-nya sudah tidak ditemukan sama sekali (ditutup dari recent
     * apps / force-stop / crash), atau null kalau perintah shell-nya sendiri
     * gagal dieksekusi (jangan disimpulkan sebagai "sudah ditutup" —
     * pemanggil sebaiknya tidak mengambil aksi reset kalau hasilnya null,
     * supaya kegagalan perintah shell sementara tidak salah mereset tweak
     * yang sebenarnya masih dipakai).
     */
    suspend fun isPackageInRecentTasks(executor: ShellExecutor, packageName: String): Boolean? {
        val result = executor.exec("dumpsys activity activities")
        if (!result.success) return null
        return containsTaskForPackage(result.output, packageName)
    }

    companion object {
        // Menangkap baris task dari beberapa format yang dipakai berbagai
        // versi/vendor Android, semuanya mencantumkan package/component di
        // dalam blok task:
        //   "  * TaskRecord{... A=com.dts.freefireth U=0 ...}"
        //   "  * Task{... A=com.dts.freefireth U=0 ...}"
        //   "    realActivity=com.dts.freefireth/.MainActivity"
        private fun taskLineRegexFor(packageName: String): Regex {
            val escaped = Regex.escape(packageName)
            return Regex("""(A=$escaped\b)|(realActivity=$escaped/)""")
        }

        internal fun containsTaskForPackage(lines: List<String>, packageName: String): Boolean {
            val regex = taskLineRegexFor(packageName)
            return lines.any { regex.containsMatchIn(it) }
        }
    }
}
