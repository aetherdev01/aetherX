package com.aether.x.core.kernel

/**
 * Snapshot satu core CPU pada saat dibaca. [availableFrequenciesKhz] dan
 * [availableGovernors] adalah daftar yang BENAR-BENAR didukung kernel
 * perangkat ini (dibaca dari `scaling_available_frequencies` /
 * `scaling_available_governors`), BUKAN daftar tetap — chipset berbeda
 * (Snapdragon/MediaTek/Exynos) punya step frequency dan governor yang
 * berbeda-beda, jadi UI wajib menampilkan pilihan dari sini, bukan hardcode.
 */
data class CpuCoreInfo(
    val coreIndex: Int,
    val currentFreqKhz: Int?,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val availableFrequenciesKhz: List<Int>,
    val currentGovernor: String?,
    val availableGovernors: List<String>,
) {
    /** true kalau tidak ada satu pun file sysfs core ini yang berhasil dibaca — kemungkinan core offline/tidak ada. */
    val isUnavailable: Boolean
        get() = currentFreqKhz == null && minFreqKhz == null && maxFreqKhz == null && availableFrequenciesKhz.isEmpty()
}

/**
 * Snapshot GPU. [devfreqPath] disimpan supaya [com.aether.x.data.KernelManagerRepository]
 * tahu jalur sysfs mana yang benar-benar ada di perangkat ini saat menulis
 * balik (Adreno/Mali/GED punya jalur berbeda — lihat KDoc
 * [KernelInfoReader.readGpuInfo] untuk daftar lengkap jalur yang dicoba).
 */
data class GpuInfo(
    val devfreqPath: String?,
    val currentFreqKhz: Int?,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val availableFrequenciesKhz: List<Int>,
    val currentGovernor: String?,
    val availableGovernors: List<String>,
) {
    val isUnavailable: Boolean get() = devfreqPath == null
}

/** Satu zona termal kernel (`/sys/class/thermal/thermal_zoneN`). */
data class ThermalZoneInfo(
    val zoneIndex: Int,
    val type: String,
    val temperatureCelsius: Float,
)

data class KernelSnapshot(
    val cpuCores: List<CpuCoreInfo>,
    val gpu: GpuInfo,
    val thermalZones: List<ThermalZoneInfo>,
    val kernelVersion: String?,
)
