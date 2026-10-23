package com.aether.x.core.shell

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aether.x.core.locale.str
import com.aether.x.R

class RootShellExecutor : ShellExecutor {

    override val backendName: String = "Root"

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {

            if (!Shell.getShell().isRoot) {
                return@withContext ShellResult.failure(
                    str(R.string.root_shell_executor_akses_root_tidak_lagi_tersedia),
                )
            }
            val result = Shell.cmd(command).exec()
            ShellResult(success = result.isSuccess, output = result.out, error = result.err)
        } catch (t: Throwable) {
            ShellResult.failure(t.message ?: str(R.string.root_shell_executor_gagal_menjalankan_perintah_lewat_root))
        }
    }
}
