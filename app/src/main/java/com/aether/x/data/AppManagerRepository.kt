package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

/**
 * Menerapkan (tulis) status freeze/unfreeze aplikasi lewat `pm`, dipakai
 * App Manager (khusus backend Root — freeze app lain butuh privilese yang
 * tidak diberikan Shizuku/adb shell biasa di sebagian besar perangkat,
 * sama seperti alasan [KernelManagerRepository] khusus Root).
 *
 * FREEZE, BUKAN UNINSTALL: `pm disable-user` SELALU bisa dibalik dengan
 * `pm enable` — TIDAK ADA aksi uninstall di repository ini secara sengaja
 * (lihat perintah pengguna soal cakupan fitur ini). Freeze menghentikan
 * app berjalan dan menyembunyikannya dari launcher tanpa menghapus data
 * atau APK-nya, jadi jauh lebih aman untuk fitur yang menyasar pengguna
 * awam sekalipun (App Manager ini menampilkan app pihak ketiga + whitelist
 * bloatware, bukan sembarang app sistem — lihat KDoc
 * [com.aether.x.core.appmanager.AppManagerCatalog]).
 *
 * `--user 0` merujuk profil pengguna utama/pemilik perangkat (bukan
 * profil kerja/tamu) — konsisten dengan asumsi single-user yang sama
 * dipakai di seluruh app ini (mis. [com.aether.x.data.LicenseRepository]
 * tidak menangani multi-profile Android baik).
 *
 * FITUR BARU (lihat perintah rework — "tambahkan opsi baru selain
 * Nonaktifkan Aplikasi"): [forceStop] dan [clearCache] — KEDUANYA TETAP
 * MENGIKUTI FILOSOFI YANG SAMA seperti freeze/unfreeze di atas (reversibel/
 * tidak merusak data secara permanen sejauh mungkin), TETAP TIDAK ADA
 * uninstall:
 * - [forceStop]: `am force-stop` menghentikan proses app yang sedang
 *   berjalan — TIDAK PUNYA efek permanen apa pun (app akan mulai lagi
 *   normal begitu dibuka ulang), aman dipakai tanpa dialog konfirmasi.
 * - [clearCache]: HANYA menghapus ISI DIREKTORI CACHE app (folder
 *   "cache" milik app, bukan foldernya sendiri) — lewat akses shell root
 *   langsung — SENGAJA TIDAK memakai `pm clear`
 *   (yang menghapus SELURUH data app termasuk save/preferensi, terlalu
 *   destruktif untuk fitur "bersihkan cache" yang pengguna harapkan cuma
 *   membebaskan storage, bukan reset total aplikasi). Karena tetap
 *   menghapus sesuatu yang tidak bisa "dibatalkan" (walau dampaknya minor
 *   dibanding uninstall/pm clear), UI WAJIB menampilkan dialog konfirmasi
 *   sebelum memanggil fungsi ini (lihat AppManagerScreen.kt).
 */
class AppManagerRepository {

    suspend fun freeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm disable-user --user 0 $packageName")
    }

    suspend fun unfreeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm enable $packageName")
    }

    /** Hentikan proses app yang sedang berjalan — tidak permanen, app bisa dibuka lagi normal setelahnya. */
    suspend fun forceStop(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("am force-stop $packageName")
    }

    /**
     * Bersihkan HANYA isi direktori cache app (bukan seluruh data/save
     * seperti `pm clear`). `-f` pada `rm` supaya tidak gagal kalau
     * direktori cache kosong/belum pernah dibuat, dan `|| true` di akhir
     * memastikan ShellResult tetap dianggap sukses walau tidak ada apa pun
     * untuk dihapus (bukan error sungguhan).
     */
    suspend fun clearCache(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("rm -rf /data/data/$packageName/cache/* 2>/dev/null || true")
    }
}
