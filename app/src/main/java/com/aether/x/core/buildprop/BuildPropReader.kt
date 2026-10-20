package com.aether.x.core.buildprop

import com.aether.x.core.shell.ShellExecutor

class BuildPropReader {

    companion object {
        private const val OVERRIDE_BEGIN = "===AETHERX_OVERRIDES_BEGIN==="
        private const val OVERRIDE_END = "===AETHERX_OVERRIDES_END==="
        private const val OVERRIDE_PATH = "/data/adb/modules/aetherx_buildprop/system.prop"
    }

    suspend fun readAll(executor: ShellExecutor): List<BuildPropSnapshot> {
        val script = buildString {
            for (partition in BuildPropPartition.entries) {
                appendLine("echo ===PART_${partition.name}===")
                appendLine("if [ -f ${shellQuote(partition.path)} ]; then echo EXISTS_YES; else echo EXISTS_NO; fi")
                appendLine("if [ -w ${shellQuote(partition.path)} ]; then echo WRITABLE_YES; else echo WRITABLE_NO; fi")
                appendLine("echo ---BODY---")
                appendLine("cat ${shellQuote(partition.path)} 2>/dev/null")
            }
            appendLine("echo $OVERRIDE_BEGIN")
            appendLine("if [ -f ${shellQuote(OVERRIDE_PATH)} ]; then cat ${shellQuote(OVERRIDE_PATH)} 2>/dev/null; fi")
            appendLine("echo $OVERRIDE_END")
        }
        val result = executor.exec(script)
        return parseBlocks(result.output)
    }

    private fun parseBlocks(lines: List<String>): List<BuildPropSnapshot> {
        val snapshots = mutableListOf<BuildPropSnapshot>()
        val overrides = linkedMapOf<String, String>()
        var currentPartition: BuildPropPartition? = null
        var exists = false
        var writable = false
        var inBody = false
        var inOverrides = false
        var bodyLines = mutableListOf<String>()

        fun flush() {
            val partition = currentPartition ?: return
            snapshots += buildSnapshot(partition, exists, writable, bodyLines)
        }

        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed == OVERRIDE_BEGIN) {
                flush()
                currentPartition = null
                inBody = false
                inOverrides = true
                bodyLines = mutableListOf()
                continue
            }
            if (trimmed == OVERRIDE_END) {
                inOverrides = false
                continue
            }
            if (inOverrides) {
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val separatorIndex = raw.indexOf('=')
                    if (separatorIndex > 0) {
                        val key = raw.substring(0, separatorIndex).trim()
                        val value = raw.substring(separatorIndex + 1).trim()
                        if (key.isNotEmpty()) overrides[key] = value
                    }
                }
                continue
            }

            val partMatch = Regex("^===PART_(\\w+)===$").find(trimmed)
            if (partMatch != null) {
                flush()
                currentPartition = BuildPropPartition.entries.firstOrNull { it.name == partMatch.groupValues[1] }
                exists = false
                writable = false
                inBody = false
                bodyLines = mutableListOf()
                continue
            }
            when (trimmed) {
                "EXISTS_YES" -> { exists = true; continue }
                "EXISTS_NO" -> { exists = false; continue }
                "WRITABLE_YES" -> { writable = true; continue }
                "WRITABLE_NO" -> { writable = false; continue }
                "---BODY---" -> { inBody = true; continue }
            }
            if (inBody && currentPartition != null) bodyLines.add(raw)
        }
        flush()

        val byPartition = snapshots.associateBy { it.partition }
        return BuildPropPartition.entries.map { partition ->
            val snapshot = byPartition[partition]
                ?: BuildPropSnapshot(partition, exists = false, writable = false, entries = emptyList(), rawLineCount = 0)
            snapshot.copy(entries = snapshot.entries.map { entry -> entry.copy(value = overrides[entry.key] ?: entry.value) })
        }
    }

    private fun buildSnapshot(
        partition: BuildPropPartition,
        exists: Boolean,
        writable: Boolean,
        bodyLines: List<String>,
    ): BuildPropSnapshot {
        if (!exists) return BuildPropSnapshot(partition, exists = false, writable = false, entries = emptyList(), rawLineCount = 0)

        val entries = mutableListOf<BuildPropEntry>()
        bodyLines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEachIndexed
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            if (key.isEmpty()) return@forEachIndexed
            entries += BuildPropEntry(lineIndex = index, key = key, value = value)
        }

        return BuildPropSnapshot(
            partition = partition,
            exists = true,
            writable = writable,
            entries = entries,
            rawLineCount = bodyLines.size,
        )
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\''") + "'"
}
