package com.aether.x.data

import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropPartition
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * Writes build properties directly only if the target filesystem accepts writes.
 * On modern read-only Android partitions, it falls back to a Magisk-compatible
 * system.prop module instead of silently failing at sed -i.
 */
class BuildPropRepository {

    companion object {
        private const val BACKUP_DIR = "/data/adb/aetherx_backup/buildprop"
        private const val MODULE_DIR = "/data/adb/modules/aetherx_buildprop"
        private const val SYSTEMLESS_BACKUP_MARKER = "# AETHERX_SYSTEMLESS_BACKUP"
    }

    suspend fun backup(executor: ShellExecutor, partition: BuildPropPartition): Result<BuildPropBackup> {
        val timestamp = System.currentTimeMillis()
        val backupPath = "$BACKUP_DIR/${partition.name.lowercase()}_$timestamp.bak"
        val script = buildString {
            appendLine("(")
            appendLine("BACKUP_DIR=${shellQuote(BACKUP_DIR)}")
            appendLine("BACKUP_PATH=${shellQuote(backupPath)}")
            appendLine("TARGET=${shellQuote(partition.path)}")
            appendLine("mkdir -p \"\$BACKUP_DIR\" || { echo BACKUP_DIRECTORY_FAILED; exit 21; }")
            appendLine(remountCommand(partition))
            appendLine("PROBE=\"\$TARGET.aetherx_probe_\$\$\"")
            appendLine("if [ -f \"\$TARGET\" ] && ( : > \"\$PROBE\" ) 2>/dev/null; then")
            appendLine("  rm -f \"\$PROBE\"")
            appendLine("  if cp -p \"\$TARGET\" \"\$BACKUP_PATH\"; then echo BACKUP_FILE_OK; else echo BACKUP_FILE_FAILED; exit 22; fi")
            appendLine("else")
            appendLine("  rm -f \"\$PROBE\"")
            appendLine(systemlessBootstrap())
            appendLine("  printf '%s\\n' ${shellQuote(SYSTEMLESS_BACKUP_MARKER)} > \"\$BACKUP_PATH\" || { echo BACKUP_FILE_FAILED; exit 22; }")
            appendLine("  if [ -f \"\$MODDIR/system.prop\" ]; then cat \"\$MODDIR/system.prop\" >> \"\$BACKUP_PATH\" || exit 22; fi")
            appendLine("  echo BACKUP_SYSTEMLESS_OK")
            appendLine("fi")
            appendLine(")")
        }
        val result = executor.exec(script)
        return if (result.outputText.contains("BACKUP_FILE_OK") || result.outputText.contains("BACKUP_SYSTEMLESS_OK")) {
            Result.success(
                BuildPropBackup(
                    partition = partition,
                    backupPath = backupPath,
                    timestampMillis = timestamp,
                    isSystemless = result.outputText.contains("BACKUP_SYSTEMLESS_OK"),
                ),
            )
        } else {
            val detail = when {
                result.outputText.contains("SYSTEMLESS_UNAVAILABLE") ->
                    str(R.string.build_prop_partisi_hanya_baca_dan_modul)
                result.outputText.contains("BACKUP_DIRECTORY_FAILED") -> str(R.string.build_prop_folder_backup_tidak_dapat_dibuat)
                result.outputText.contains("BACKUP_FILE_FAILED") -> str(R.string.build_prop_gagal_menulis_file_backup)
                else -> result.errorText.ifBlank { result.outputText }.ifBlank { str(R.string.build_prop_gagal_membuat_backup) }
            }
            Result.failure(IllegalStateException(detail))
        }
    }

    suspend fun listBackups(executor: ShellExecutor, partition: BuildPropPartition): List<BuildPropBackup> {
        val prefix = partition.name.lowercase()
        val result = executor.exec(
            """
            for file in '$BACKUP_DIR'/${prefix}_*.bak; do
                [ -f "${'$'}file" ] || continue
                if [ "${'$'}(head -n 1 "${'$'}file")" = ${shellQuote(SYSTEMLESS_BACKUP_MARKER)} ]; then
                    echo "SYSTEMLESS|${'$'}file"
                else
                    echo "FILE|${'$'}file"
                fi
            done
            """.trimIndent(),
        )
        return result.output
            .mapNotNull { row ->
                val trimmedRow = row.trim()
                val mode = trimmedRow.substringBefore('|', "FILE")
                val path = if ('|' in trimmedRow) trimmedRow.substringAfter('|') else trimmedRow
                val timestamp = Regex("${prefix}_(\\d+)\\.bak$").find(path)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@mapNotNull null
                BuildPropBackup(partition, path, timestamp, isSystemless = mode == "SYSTEMLESS")
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
        val safeValue = newValue.replace("\n", "").replace("\r", "")
        if (!key.matches(Regex("[A-Za-z0-9._-]+"))) {
            return ShellResult.failure(str(R.string.build_prop_nama_properti_tidak_valid))
        }

        val lineNumber = lineIndex + 1
        val path = partition.path
        val expectedLine = "$key=$safeValue"
        val escapedReplacement = escapeSedReplacement(expectedLine)
        val sedScript = "$lineNumber s/.*/$escapedReplacement/"
        val script = buildString {
            appendLine("(")
            appendLine("TARGET=${shellQuote(path)}")
            appendLine("EXPECTED=${shellQuote(expectedLine)}")
            appendLine(remountCommand(partition))
            appendLine("PROBE=\"\$TARGET.aetherx_probe_\$\$\"")
            appendLine("if [ -f \"\$TARGET\" ] && ( : > \"\$PROBE\" ) 2>/dev/null; then")
            appendLine("  rm -f \"\$PROBE\"")
            appendLine("  if sed -i ${shellQuote(sedScript)} \"\$TARGET\" && [ \"\$(sed -n '${lineNumber}p' \"\$TARGET\")\" = \"\$EXPECTED\" ]; then")
            appendLine("    echo APPLY_FILE_OK")
            appendLine("    exit 0")
            appendLine("  else")
            appendLine("    echo APPLY_FILE_FAILED")
            appendLine("    exit 28")
            appendLine("  fi")
            appendLine("fi")
            appendLine("rm -f \"\$PROBE\"")
            appendLine(systemlessBootstrap())
            appendLine("PROPFILE=\"\$MODDIR/system.prop\"")
            appendLine("TMPFILE=\"\$PROPFILE.aetherx_tmp\"")
            appendLine("touch \"\$PROPFILE\" || { echo SYSTEMLESS_WRITE_FAILED; exit 27; }")
            appendLine("awk -v k=${shellQuote(key)} 'index(\$0, k \"=\") != 1 { print }' \"\$PROPFILE\" > \"\$TMPFILE\" || { rm -f \"\$TMPFILE\"; echo SYSTEMLESS_WRITE_FAILED; exit 27; }")
            appendLine("printf '%s=%s\\n' ${shellQuote(key)} ${shellQuote(safeValue)} >> \"\$TMPFILE\" || { rm -f \"\$TMPFILE\"; echo SYSTEMLESS_WRITE_FAILED; exit 27; }")
            appendLine("chmod 0644 \"\$TMPFILE\" 2>/dev/null || true")
            appendLine("mv \"\$TMPFILE\" \"\$PROPFILE\" || { rm -f \"\$TMPFILE\"; echo SYSTEMLESS_WRITE_FAILED; exit 27; }")
            appendLine("RESETPROP=\$(command -v resetprop 2>/dev/null)")
            appendLine("[ -n \"\$RESETPROP\" ] || { [ -x /data/adb/magisk/resetprop ] && RESETPROP=/data/adb/magisk/resetprop; }")
            appendLine("[ -n \"\$RESETPROP\" ] || { [ -x /sbin/resetprop ] && RESETPROP=/sbin/resetprop; }")
            appendLine("if [ -n \"\$RESETPROP\" ] && \"\$RESETPROP\" -n ${shellQuote(key)} ${shellQuote(safeValue)} >/dev/null 2>&1; then")
            appendLine("  echo SYSTEMLESS_APPLY_OK")
            appendLine("else")
            appendLine("  echo SYSTEMLESS_SAVED_REBOOT")
            appendLine("fi")
            appendLine(")")
        }

        val result = executor.exec(script)
        return when {
            result.outputText.contains("APPLY_FILE_OK") ->
                ShellResult(success = true, output = result.output + "APPLY_OK")
            result.outputText.contains("SYSTEMLESS_APPLY_OK") ->
                ShellResult(success = true, output = result.output + "APPLY_OK" + "SYSTEMLESS_APPLY_OK")
            result.outputText.contains("SYSTEMLESS_SAVED_REBOOT") ->
                ShellResult(success = true, output = result.output + "APPLY_OK" + "SYSTEMLESS_SAVED_REBOOT")
            result.outputText.contains("SYSTEMLESS_UNAVAILABLE") ->
                ShellResult.failure(str(R.string.build_prop_partisi_hanya_baca_dan_root))
            result.outputText.contains("SYSTEMLESS_WRITE_FAILED") ->
                ShellResult.failure(str(R.string.build_prop_gagal_menyimpan_override_system_prop))
            result.outputText.contains("APPLY_FILE_FAILED") ->
                ShellResult.failure(str(R.string.build_prop_file_dapat_ditulis_tetapi_penggantian))
            else -> ShellResult.failure(result.errorText.ifBlank { result.outputText }.ifBlank { str(R.string.build_prop_gagal_menulis, path) })
        }
    }

    suspend fun restore(executor: ShellExecutor, backup: BuildPropBackup): ShellResult {
        val script = buildString {
            appendLine("(")
            appendLine("BACKUP_PATH=${shellQuote(backup.backupPath)}")
            appendLine("TARGET=${shellQuote(backup.partition.path)}")
            appendLine("if [ ! -f \"\$BACKUP_PATH\" ]; then echo RESTORE_BACKUP_MISSING; exit 31; fi")
            appendLine("if [ \"\$(head -n 1 \"\$BACKUP_PATH\")\" = ${shellQuote(SYSTEMLESS_BACKUP_MARKER)} ]; then")
            appendLine(systemlessBootstrap())
            appendLine("  PROPFILE=\"\$MODDIR/system.prop\"")
            appendLine("  TMPFILE=\"\$PROPFILE.aetherx_tmp\"")
            appendLine("  tail -n +2 \"\$BACKUP_PATH\" > \"\$TMPFILE\" || { echo RESTORE_SYSTEMLESS_FAILED; exit 32; }")
            appendLine("  chmod 0644 \"\$TMPFILE\" 2>/dev/null || true")
            appendLine("  mv \"\$TMPFILE\" \"\$PROPFILE\" || { rm -f \"\$TMPFILE\"; echo RESTORE_SYSTEMLESS_FAILED; exit 32; }")
            appendLine("  RESETPROP=\$(command -v resetprop 2>/dev/null)")
            appendLine("  [ -n \"\$RESETPROP\" ] || { [ -x /data/adb/magisk/resetprop ] && RESETPROP=/data/adb/magisk/resetprop; }")
            appendLine("  if [ -n \"\$RESETPROP\" ]; then")
            appendLine("    while IFS='=' read -r PROP_KEY PROP_VALUE; do")
            appendLine("      case \"\$PROP_KEY\" in ''|\\#*) continue ;; esac")
            appendLine("      \"\$RESETPROP\" -n \"\$PROP_KEY\" \"\$PROP_VALUE\" >/dev/null 2>&1 || true")
            appendLine("    done < \"\$PROPFILE\"")
            appendLine("  fi")
            appendLine("  echo RESTORE_SYSTEMLESS_OK")
            appendLine("else")
            appendLine(remountCommand(backup.partition))
            appendLine("  PROBE=\"\$TARGET.aetherx_probe_\$\$\"")
            appendLine("  if [ -f \"\$TARGET\" ] && ( : > \"\$PROBE\" ) 2>/dev/null; then")
            appendLine("    rm -f \"\$PROBE\"")
            appendLine("    cp -p \"\$BACKUP_PATH\" \"\$TARGET\" && echo RESTORE_FILE_OK || { echo RESTORE_FILE_FAILED; exit 33; }")
            appendLine("  else")
            appendLine("    rm -f \"\$PROBE\"")
            appendLine("    echo RESTORE_FILE_FAILED")
            appendLine("    exit 33")
            appendLine("  fi")
            appendLine("fi")
            appendLine(")")
        }
        val result = executor.exec(script)
        return when {
            result.outputText.contains("RESTORE_FILE_OK") || result.outputText.contains("RESTORE_SYSTEMLESS_OK") ->
                ShellResult(success = true, output = result.output + "RESTORE_OK")
            result.outputText.contains("SYSTEMLESS_UNAVAILABLE") ->
                ShellResult.failure(str(R.string.build_prop_root_manager_tidak_dapat_memulihkan))
            result.outputText.contains("RESTORE_BACKUP_MISSING") ->
                ShellResult.failure(str(R.string.build_prop_file_backup_tidak_ditemukan))
            else -> ShellResult.failure(result.errorText.ifBlank { result.outputText }.ifBlank { str(R.string.build_prop_gagal_memulihkan, backup.partition.path) })
        }
    }

    private fun remountCommand(partition: BuildPropPartition): String =
        "mount -o rw,remount ${shellQuote(remountTargetFor(partition))} >/dev/null 2>&1 || true"

    private fun remountTargetFor(partition: BuildPropPartition): String = when (partition) {
        BuildPropPartition.SYSTEM -> "/system"
        BuildPropPartition.VENDOR -> "/vendor"
        BuildPropPartition.PRODUCT -> "/product"
        BuildPropPartition.SYSTEM_EXT -> "/system_ext"
    }

    private fun systemlessBootstrap(): String = """
        if [ ! -d /data/adb/modules ] || {
            ! command -v magisk >/dev/null 2>&1 &&
            ! command -v resetprop >/dev/null 2>&1 &&
            [ ! -x /data/adb/magisk/magisk ] &&
            [ ! -x /data/adb/magisk/resetprop ] &&
            [ ! -x /sbin/magisk ] &&
            [ ! -x /debug_ramdisk/magisk ];
        }; then
            echo SYSTEMLESS_UNAVAILABLE
            exit 25
        fi
        MODDIR=${shellQuote(MODULE_DIR)}
        mkdir -p "${'$'}MODDIR" || { echo SYSTEMLESS_WRITE_FAILED; exit 26; }
        if [ ! -f "${'$'}MODDIR/module.prop" ]; then
            cat > "${'$'}MODDIR/module.prop" <<'AETHERX_MODULE'
        id=aetherx_buildprop
        name=AetherX BuildProp Overrides
        version=1.0
        versionCode=1
        author=AetherX
        description=Systemless build property overrides managed by AetherX
        AETHERX_MODULE
        fi
        [ -f "${'$'}MODDIR/system.prop" ] || : > "${'$'}MODDIR/system.prop"
        chmod 0755 "${'$'}MODDIR" 2>/dev/null || true
        chmod 0644 "${'$'}MODDIR/module.prop" "${'$'}MODDIR/system.prop" 2>/dev/null || true
    """.trimIndent()
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun escapeSedReplacement(value: String): String = value
        .replace("\\", "\\\\")
        .replace("&", "\\&")
        .replace("/", "\\/")
}
