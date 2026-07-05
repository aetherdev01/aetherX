package com.aether.x.core.buildprop

/**
 * Satu partisi sumber `build.prop` yang dicoba dibaca. Perangkat modern
 * (Android 10+ dengan Treble/dynamic partitions) memecah properti sistem ke
 * beberapa file terpisah, bukan satu `/system/build.prop` seperti dulu —
 * mengedit hanya `/system/build.prop` di perangkat begini bisa membuat
 * pengguna tidak melihat efek apapun (property yang sama juga di-override
 * dari partisi lain yang dibaca belakangan oleh init). Reader mencoba semua
 * jalur ini dan menandai mana yang benar-benar ada & writable di perangkat
 * ini; UI hanya menawarkan partisi yang [exists] true.
 */
enum class BuildPropPartition(val path: String, val displayLabel: String) {
    SYSTEM("/system/build.prop", "System"),
    VENDOR("/vendor/build.prop", "Vendor"),
    PRODUCT("/product/build.prop", "Product"),
    SYSTEM_EXT("/system_ext/build.prop", "System_ext"),
}

/**
 * Satu baris properti key=value yang berhasil di-parse. [lineIndex] disimpan
 * (bukan cuma key/value) supaya penulisan balik lewat `sed` bisa menyasar
 * NOMOR BARIS pasti, bukan pattern-match teks key — menghindari kasus ganda
 * kalau ada key yang sama muncul lebih dari sekali di file (build.prop tidak
 * melarang duplikat, baris terakhir yang menang saat dibaca init, tapi kalau
 * app menulis berdasarkan pattern key saja bisa salah sasaran mengubah
 * kemunculan pertama).
 */
data class BuildPropEntry(
    val lineIndex: Int,
    val key: String,
    val value: String,
)

/**
 * Snapshot satu partisi build.prop yang berhasil dibaca. [rawLineCount]
 * disimpan terpisah dari [entries].size karena file asli berisi baris
 * komentar (`#`) dan baris kosong yang TIDAK masuk [entries] tapi tetap
 * harus dipertahankan filenya utuh — dipakai sebagai sanity-check ringan di
 * ViewModel (kalau rawLineCount tiba-tiba 0 padahal sebelumnya ratusan,
 * kemungkinan file gagal terbaca penuh, bukan benar-benar kosong).
 */
data class BuildPropSnapshot(
    val partition: BuildPropPartition,
    val exists: Boolean,
    val writable: Boolean,
    val entries: List<BuildPropEntry>,
    val rawLineCount: Int,
)

/**
 * Metadata satu file backup yang tersimpan di
 * `/data/adb/aetherx_backup/`. [timestampMillis] dipakai untuk urutan
 * tampilan (terbaru dulu) sekaligus nama file, supaya beberapa backup
 * partisi yang sama tidak saling timpa antar sesi edit.
 */
data class BuildPropBackup(
    val partition: BuildPropPartition,
    val backupPath: String,
    val timestampMillis: Long,
)
