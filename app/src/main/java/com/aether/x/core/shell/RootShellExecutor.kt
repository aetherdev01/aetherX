package com.aether.x.core.shell

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menjalankan perintah shell lewat root (su), didukung oleh libsu — kompatibel
 * dengan Magisk, KernelSU, maupun APatch karena semuanya menyediakan binary
 * `su` standar yang dicari libsu lewat PATH.
 */
class RootShellExecutor : ShellExecutor {

    override val backendName: String = "Root"

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            // BUG FIX (lihat perintah rework — "user no root sekarang bisa
            // pakai opsi App Manager"): SEBELUMNYA executor ini langsung
            // Shell.cmd(command).exec() tanpa verifikasi ulang bahwa shell
            // global libsu benar-benar root TEPAT SEBELUM command
            // dijalankan — hanya mengandalkan hasil isRoot yang sudah
            // diperiksa SEKALI sebelumnya oleh
            // PrivilegeManager.checkRootSilently()/requestRoot() (yang
            // menentukan status.activeBackend, satu-satunya syarat
            // RootShellExecutor() ini dipilih sama sekali oleh
            // getExecutor()). Kalau shell singleton libsu itu berubah
            // status di antara kedua titik waktu itu (mis. root dicabut
            // via Magisk Manager pertengahan sesi tanpa AetherX ditutup,
            // atau — device tertentu — shell fallback non-root yang
            // dikembalikan tetap bisa "berhasil" menjalankan sebagian
            // command pm/am non-destruktif sebagai no-op dan salah
            // dianggap sukses), command tetap akan dicoba dijalankan.
            // Sekarang gagal-tertutup (fail closed): re-verifikasi isRoot
            // eksplisit tepat sebelum eksekusi, tolak dengan pesan jelas
            // kalau ternyata BUKAN root, daripada diam-diam melanjutkan.
            if (!Shell.getShell().isRoot) {
                return@withContext ShellResult.failure(
                    "Akses root tidak lagi tersedia — perintah dibatalkan (lihat Halaman Izin Akses).",
                )
            }
            val result = Shell.cmd(command).exec()
            ShellResult(success = result.isSuccess, output = result.out, error = result.err)
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: "Gagal menjalankan perintah lewat root")
        }
    }
}
