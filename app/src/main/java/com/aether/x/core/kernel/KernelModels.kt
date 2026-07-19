package com.aether.x.core.kernel

data class CpuCoreInfo(
    val coreIndex: Int,
    val currentFreqKhz: Int?,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val availableFrequenciesKhz: List<Int>,
    val currentGovernor: String?,
    val availableGovernors: List<String>,
) {

    val isUnavailable: Boolean
        get() = currentFreqKhz == null && minFreqKhz == null && maxFreqKhz == null && availableFrequenciesKhz.isEmpty()
}

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
