package com.aether.x.core.buildprop

import com.aether.x.core.shell.ShellExecutor

/**
 * Membaca (read-only) isi file `build.prop` dari tiap [BuildPropPartition]
 * lewat [ShellExecutor] — pasangan BACA untuk
 * [com.aether.x.data.BuildPropRepository] yang murni MENULIS, mengikuti
 * pemisahan yang sama seperti [com.aether.x.core.kernel.KernelInfoReader]
 * vs [com.aether.x.data.KernelManagerRepository].
 *
 * SATU PANGGILAN SHELL UNTUK SEMUA PARTISI: seperti KernelInfoReader,
 * menghindari satu `exec()` per partisi karena tiap panggilan `su -c` baru
 * ada overhead proses.
 */
class BuildPropReader {

    /**
     * Baca seluruh partisi build.prop yang dikenal sekaligus. Partisi yang
     * tidak ada di perangkat ini (mis. `/product/build.prop` di perangkat
     * lama pra-Treble) tetap dikembalikan dengan [BuildPropSnapshot.exists]
     * false, supaya UI bisa menyembunyikannya dari daftar tanpa perlu
     * memanggil lagi.
     */
    suspend fun readAll(executor: ShellExecutor): List<BuildPropSnapshot> {
        val script = buildString {
            for (partition in BuildPropPartition.entries) {
                appendLine("echo ===PART_${partition.name}===")
                appendLine("if [ -f \"${partition.path}\" ]; then echo EXISTS_YES; else echo EXISTS_NO; fi")
                // Cek writable dengan test -w, BUKAN dengan mencoba tulis
                // beneran — test -w murni query permission tanpa efek
                // samping, aman dipanggil setiap kali layar dibuka.
                appendLine("if [ -w \"${partition.path}\" ]; then echo WRITABLE_YES; else echo WRITABLE_NO; fi")
                appendLine("echo ---BODY---")
                appendLine("cat \"${partition.path}\" 2>/dev/null")
            }
        }
        val result = executor.exec(script)
        return parseBlocks(result.output)
    }

    private fun parseBlocks(lines: List<String>): List<BuildPropSnapshot> {
        val snapshots = mutableListOf<BuildPropSnapshot>()
        var currentPartition: BuildPropPartition? = null
        var exists = false
        var writable = false
        var inBody = false
        var bodyLines = mutableListOf<String>()

        fun flush() {
            val partition = currentPartition ?: return
            snapshots += buildSnapshot(partition, exists, writable, bodyLines)
        }

        for (raw in lines) {
            val trimmed = raw.trim()
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
            if (inBody && currentPartition != null) {
                bodyLines.add(raw)
            }
        }
        flush()

        // Jaga urutan tampilan tetap sesuai enum (System, Vendor, Product,
        // System_ext), terlepas urutan output shell (yang seharusnya sama,
        // tapi ini jaga-jaga kalau parsing sebagian gagal).
        val byPartition = snapshots.associateBy { it.partition }
        return BuildPropPartition.entries.map { p ->
            byPartition[p] ?: BuildPropSnapshot(p, exists = false, writable = false, entries = emptyList(), rawLineCount = 0)
        }
    }

    /**
     * Parse baris `key=value` dari isi mentah satu file build.prop.
     * Baris komentar (`#`) dan baris kosong SENGAJA tidak masuk [BuildPropEntry]
     * (tidak ada key untuk diedit) tapi tetap terhitung di [BuildPropSnapshot.rawLineCount]
     * — lihat KDoc [BuildPropSnapshot] untuk kegunaannya.
     */
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
            if (separatorIndex <= 0) return@forEachIndexed // baris malformed tanpa '=', dilewati dari daftar edit tapi tetap ada di file asli
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
}
