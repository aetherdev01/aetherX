package com.aether.x.data

import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropPartition
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

/**
 * Menulis (apply) perubahan properti ke file `build.prop` di sysfs/data
 * partition langsung — pasangan TULIS untuk
 * [com.aether.x.core.buildprop.BuildPropReader] yang murni BACA, mengikuti
 * pemisahan yang sama seperti [KernelManagerRepository] vs
 * [com.aether.x.core.kernel.KernelInfoReader].
 *
 * INI BERBEDA KELAS RISIKO DARI SEMUA REPOSITORY ROOT LAIN DI APP INI:
 * [TweakRepository.applyVmHeapBoost] menulis lewat `setprop` (runtime,
 * hilang saat reboot, tidak bisa membuat perangkat gagal boot). Repository
 * INI mengedit FILE yang dibaca ulang oleh init/Zygote setiap kali
 * perangkat menyala — properti yang salah (terutama di bawah namespace
 * `ro.*` yang immutable setelah boot, atau properti yang dicek hard oleh
 * vendor init scripts) BISA menyebabkan bootloop. Karena itu:
 *
 * 1. [backup] WAJIB dipanggil sebelum [applyEntry] pertama pada partisi
 *    tertentu di tiap sesi edit (ditegakkan oleh
 *    [com.aether.x.ui.tweak.BuildPropViewModel], bukan di sini — repository
 *    ini tetap mengekspos [applyEntry] tanpa syarat backup supaya lapisan
 *    data tidak diam-diam menolak permintaan valid, tapi ViewModel-lah yang
 *    menjamin urutan operasi ke pengguna).
 * 2. Penulisan menyasar NOMOR BARIS pasti (lihat KDoc [BuildPropEntry]),
 *    bukan pattern-match key, untuk menghindari salah sasaran pada key
 *    duplikat.
 * 3. TIDAK ADA APPLY OTOMATIS SETELAH TULIS — properti di file ini hanya
 *    dibaca ulang saat boot berikutnya, jadi UI wajib menampilkan
 *    peringatan "perlu reboot", bukan berpura-pura perubahan sudah aktif.
 */
class BuildPropRepository {

    companion object {
        private const val BACKUP_DIR = "/data/adb/aetherx_backup/buildprop"
    }

    /**
     * Salin file partisi ke [BACKUP_DIR] dengan nama menyertakan timestamp,
     * supaya backup dari sesi edit berbeda tidak saling timpa. Dipanggil
     * SEKALI per partisi per sesi (ViewModel melacak partisi mana yang
     * sudah dibackup di sesi berjalan supaya tidak menumpuk file backup
     * tiap kali pengguna mengedit satu key lagi di partisi yang sama).
     */
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

    /** Daftar semua backup tersimpan untuk satu partisi, terbaru duluan. */
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

    /**
     * Terapkan satu perubahan value pada baris [lineIndex] (0-based, sesuai
     * [BuildPropEntry.lineIndex]) partisi [partition]. Remount rw dicoba
     * dulu (best-effort — banyak perangkat modern tidak mengizinkan remount
     * `/system` rw sama sekali walau root penuh, karena dm-verity/AVB;
     * kegagalan remount TIDAK menghentikan percobaan tulis, karena sebagian
     * perangkat/root solution tetap bisa menulis tanpa remount eksplisit
     * lewat overlay milik Magisk).
     *
     * [newValue] ditulis APA ADANYA tanpa validasi isi (validasi format
     * value per-key di luar cakupan repository ini, sama seperti
     * [KernelManagerRepository] tidak memvalidasi frekuensi terhadap daftar
     * available) — tapi karakter newline di [newValue] DIBUANG paksa karena
     * satu entri properti wajib satu baris, newline di tengah akan merusak
     * struktur file dan mempengaruhi baris-baris sesudahnya.
     */
    suspend fun applyEntry(
        executor: ShellExecutor,
        partition: BuildPropPartition,
        lineIndex: Int,
        key: String,
        newValue: String,
    ): ShellResult {
        val sanitizedValue = newValue.replace("\n", "").replace("\r", "")
        val targetLineNumber = lineIndex + 1 // sed pakai 1-based, entry kita 0-based
        val path = partition.path

        // Escape delimiter '/' dan backslash di value supaya tidak merusak
        // sintaks sed (kita pakai '/' sebagai delimiter perintah s///).
        val escapedValue = sanitizedValue.replace("\\", "\\\\").replace("/", "\\/")
        val escapedKey = key.replace("\\", "\\\\").replace("/", "\\/")

        val script = buildString {
            appendLine("mount -o rw,remount ${remountTargetFor(partition)} 2>/dev/null")
            // -i.tmp lalu hapus .tmp: sebagian toolbox Android tidak
            // mendukung `sed -i` tanpa argumen suffix (BusyBox vs GNU sed
            // berbeda perilaku), jadi selalu pakai suffix eksplisit untuk
            // kompatibilitas, lalu file suffix dihapus segera setelahnya.
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

    /**
     * Pulihkan satu partisi dari file backup tertentu — overwrite penuh
     * (bukan per-baris) karena tujuannya mengembalikan file PERSIS seperti
     * sebelum sesi edit, termasuk kalau di sesi itu ada beberapa key yang
     * sudah sempat diubah.
     */
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

    /** Titik mount yang perlu di-remount rw untuk partisi tertentu (mis. `/vendor` untuk file di bawahnya, bukan `/system`). */
    private fun remountTargetFor(partition: BuildPropPartition): String = when (partition) {
        BuildPropPartition.SYSTEM -> "/system"
        BuildPropPartition.VENDOR -> "/vendor"
        BuildPropPartition.PRODUCT -> "/product"
        BuildPropPartition.SYSTEM_EXT -> "/system_ext"
    }
}
