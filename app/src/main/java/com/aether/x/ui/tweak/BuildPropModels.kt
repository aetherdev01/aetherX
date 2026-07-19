package com.aether.x.core.buildprop

enum class BuildPropPartition(val path: String, val displayLabel: String) {
    SYSTEM("/system/build.prop", "System"),
    VENDOR("/vendor/build.prop", "Vendor"),
    PRODUCT("/product/build.prop", "Product"),
    SYSTEM_EXT("/system_ext/build.prop", "System_ext"),
}

data class BuildPropEntry(
    val lineIndex: Int,
    val key: String,
    val value: String,
)

data class BuildPropSnapshot(
    val partition: BuildPropPartition,
    val exists: Boolean,
    val writable: Boolean,
    val entries: List<BuildPropEntry>,
    val rawLineCount: Int,
)

data class BuildPropBackup(
    val partition: BuildPropPartition,
    val backupPath: String,
    val timestampMillis: Long,
)
