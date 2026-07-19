package com.aether.x.core.shell

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootShellExecutor : ShellExecutor {

    override val backendName: String = "Root"

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {

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
