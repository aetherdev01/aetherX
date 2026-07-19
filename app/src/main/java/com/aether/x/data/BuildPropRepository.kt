package com.aether.x.data

import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropPartition
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

class BuildPropRepository {

    companion object {
        private const val BACKUP_DIR = "/data/adb/aetherx_backup/buildprop"
    }

    suspend fun backup(executor: ShellExecutor, partition: BuildPropPartition): Result<BuildPropBackup> {
        val timestamp = System.currentTimeMillis()
        val backupPath = "$BACKUP_DIR/${partition.name.lowercase()}_$timestamp.bak"
        val result = executor.exec(
            "mkdir -p $BACKUP_DIR && cp \"${partition.path}\" \"$backupPath\" && echo BACKUP_OK",
        )
        return if (result.success && result.outputText.contains("BACKUP_OK")) {
            Result.success(BuildPropBackup(partition, backupPath, timestamp))
        } else {
            Result.failure(IllegalStateException(result.errorText.ifBlank { "Gagal membuat backup" }))
        }
    }

    suspend fun listBackups(executor: ShellExecutor, partition: BuildPropPartition): List<BuildPropBackup> {
        val prefix = partition.name.lowercase()
        val result = executor.exec(
            "ls -1 $BACKUP_DIR/${prefix}_*.bak 2>/dev/null",
        )
        return result.output
            .mapNotNull { path ->
                val timestamp = Regex("${prefix}_(\\d+)\\.bak$").find(path)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@mapNotNull null
                BuildPropBackup(partition, path.trim(), timestamp)
            }
            .sortedByDescending { it.timestampMillis }
    }

    suspend fun applyEntry(
        executor: ShellExecutor,
        partition: BuildPropPartition,
        lineIndex: Int,
        key: String,
        newValue: String,
    ): ShellResult {
        val sanitizedValue = newValue.replace("\n", "").replace("\r", "")
        val targetLineNumber = lineIndex + 1
        val path = partition.path

        val escapedValue = sanitizedValue.replace("\\", "\\\\").replace("/", "\\/")
        val escapedKey = key.replace("\\", "\\\\").replace("/", "\\/")

        val script = buildString {
            appendLine("mount -o rw,remount ${remountTargetFor(partition)} 2>/dev/null")

            appendLine(
                "sed -i.tmp '${targetLineNumber}s/^.*\$/$escapedKey=$escapedValue/' \"$path\" " +
                    "&& rm -f \"$path.tmp\" && echo APPLY_OK",
            )
        }
        val result = executor.exec(script)
        return if (result.outputText.contains("APPLY_OK")) {
            ShellResult(success = true, output = result.output)
        } else {
            ShellResult.failure(result.errorText.ifBlank { "Gagal menulis ke $path" })
        }
    }

    suspend fun restore(executor: ShellExecutor, backup: BuildPropBackup): ShellResult {
        val path = backup.partition.path
        val script = """
            mount -o rw,remount ${remountTargetFor(backup.partition)} 2>/dev/null
            cp "${backup.backupPath}" "$path" && echo RESTORE_OK
        """.trimIndent()
        val result = executor.exec(script)
        return if (result.outputText.contains("RESTORE_OK")) {
            ShellResult(success = true, output = result.output)
        } else {
            ShellResult.failure(result.errorText.ifBlank { "Gagal memulihkan $path" })
        }
    }

    private fun remountTargetFor(partition: BuildPropPartition): String = when (partition) {
        BuildPropPartition.SYSTEM -> "/system"
        BuildPropPartition.VENDOR -> "/vendor"
        BuildPropPartition.PRODUCT -> "/product"
        BuildPropPartition.SYSTEM_EXT -> "/system_ext"
    }
}
