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

    /**
     * FITUR BARU (rework total Game Booster — rail "quick app" di sisi
     * kiri panel: lihat perintah rework "sisi kiri list untuk quick app
     * atau membuka apps"): mengembalikan package name SEMUA task yang
     * MASIH hidup di recent apps (baik foreground maupun background),
     * diurutkan dari yang PALING BARU ke PALING LAMA — urutan ini
     * mengikuti urutan kemunculan baris `TaskRecord`/`Task` di output
     * `dumpsys activity activities`, yang SUDAH terurut begitu oleh sistem
     * sendiri (task teratas/terbaru selalu dicetak lebih dulu), jadi tidak
     * perlu sorting tambahan.
     *
     * [excludingPackage] dikecualikan dari hasil (dipakai untuk selalu
     * menyingkirkan AetherX sendiri dari rail-nya sendiri — tidak ada
     * gunanya menampilkan AetherX sebagai "quick app" untuk membuka
     * AetherX dari dalam Game Booster milik AetherX).
     *
     * Duplikat package (task yang sama muncul lebih dari sekali, atau
     * beberapa Activity dari package yang sama) DIHILANGKAN — rail ini
     * menampilkan APLIKASI, bukan task individual, jadi satu app hanya
     * boleh muncul sekali walau attribute punya beberapa task terbuka.
     *
     * Null kalau perintah shell-nya sendiri gagal (lihat kontrak yang
     * sama dengan [isPackageInRecentTasks] di atas).
     */
    suspend fun listRecentPackages(executor: ShellExecutor, excludingPackage: String? = null): List<String>? {
        val result = executor.exec("dumpsys activity activities")
        if (!result.success) return null
        return parseRecentPackagesInOrder(result.output)
            .filter { it != excludingPackage }
            .distinct()
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

        // Sama seperti taskLineRegexFor tapi menangkap NAMA package apa pun
        // (bukan mencocokkan satu package tertentu) — dipakai
        // [parseRecentPackagesInOrder] untuk membangun daftar, bukan cuma
        // cek keberadaan satu package seperti taskLineRegexFor.
        private val ANY_TASK_PACKAGE_REGEX =
            Regex("""(?:A=|realActivity=)([a-zA-Z0-9_][a-zA-Z0-9_.]*[a-zA-Z0-9_])(?:\s|/)""")

        internal fun containsTaskForPackage(lines: List<String>, packageName: String): Boolean {
            val regex = taskLineRegexFor(packageName)
            return lines.any { regex.containsMatchIn(it) }
        }

        internal fun parseRecentPackagesInOrder(lines: List<String>): List<String> {
            val result = mutableListOf<String>()
            for (line in lines) {
                ANY_TASK_PACKAGE_REGEX.find(line)?.let { match ->
                    val pkg = match.groupValues[1]
                    if (pkg.isNotBlank()) result.add(pkg)
                }
            }
            return result
        }
    }
}
