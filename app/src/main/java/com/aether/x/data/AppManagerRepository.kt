package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

class AppManagerRepository {

    suspend fun freeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm disable-user --user 0 $packageName")
    }

    suspend fun unfreeze(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("pm enable $packageName")
    }

    suspend fun forceStop(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("am force-stop $packageName")
    }

    suspend fun clearCache(executor: ShellExecutor, packageName: String): ShellResult {
        return executor.exec("rm -rf /data/data/$packageName/cache/* 2>/dev/null || true")
    }
}
