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
 */
class AppManagerRepository {

    suspend fun freeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm disable-user --user 0 $packageName")
    }

    suspend fun unfreeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm enable $packageName")
    }
}
