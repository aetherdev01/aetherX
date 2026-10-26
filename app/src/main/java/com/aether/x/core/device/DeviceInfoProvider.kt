package com.aether.x.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.StatFs
import java.io.File
import java.util.Locale

data class DeviceInfoSnapshot(
    val manufacturer: String,
    val model: String,
    val board: String,
    val androidVersion: String,
    val sdkInt: Int,
    val cpuAbi: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    /** SoC/chipset (mis. "SM8550") — kosong kalau ROM tidak mengisinya. */
    val socModel: String = "",
    val hardware: String = "",
    val kernelVersion: String = "",
    val cpuCores: Int = 0,
    /** 0..100, atau -1 kalau tidak terbaca. */
    val batteryPercent: Int = -1,
    val batteryTempC: Float? = null,
    val isCharging: Boolean = false,
    val uptimeMillis: Long = 0L,
    val refreshRateHz: Float = 0f,
    /** CPU/SoC thermal sensor when the ROM exposes a readable thermal zone. */
    val systemTempC: Float? = null,
) {
    val usedRamBytes: Long get() = (totalRamBytes - availableRamBytes).coerceAtLeast(0L)
    val usedStorageBytes: Long get() = (totalStorageBytes - availableStorageBytes).coerceAtLeast(0L)
    val ramUsedFraction: Float
        get() = if (totalRamBytes > 0) usedRamBytes.toFloat() / totalRamBytes else 0f
    val storageUsedFraction: Float
        get() = if (totalStorageBytes > 0) usedStorageBytes.toFloat() / totalStorageBytes else 0f
}

object DeviceInfoProvider {

    fun read(context: Context): DeviceInfoSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availableRam = memInfo.availMem

        val internalStat = StatFs(android.os.Environment.getDataDirectory().path)
        val totalStorage = internalStat.blockSizeLong * internalStat.blockCountLong
        val availableStorage = internalStat.blockSizeLong * internalStat.availableBlocksLong

        // Baterai lewat sticky broadcast (tidak butuh permission & tidak
        // mendaftarkan receiver permanen).
        val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val refreshRate = runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
                .refreshRate
        }.getOrDefault(0f)

        return DeviceInfoSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            board = Build.BOARD.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty(),
            totalRamBytes = totalRam,
            availableRamBytes = availableRam,
            totalStorageBytes = totalStorage,
            availableStorageBytes = availableStorage,
            socModel = Build.SOC_MODEL.orEmpty().takeIf { it != Build.UNKNOWN }.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            kernelVersion = System.getProperty("os.version").orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            batteryPercent = batteryPercent,
            batteryTempC = if (tempTenths >= 0) tempTenths / 10f else null,
            isCharging = charging,
            uptimeMillis = SystemClock.elapsedRealtime(),
            refreshRateHz = refreshRate,
            systemTempC = readSystemThermalTemperature(),
        )
    }

    /** Best-effort SoC/CPU/GPU temperature. Restricted thermal zones simply return null. */
    private fun readSystemThermalTemperature(): Float? = runCatching {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles { file -> file.name.startsWith("thermal_zone") } ?: return null
        val readings = zones.mapNotNull { zone ->
            val raw = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
                ?: return@mapNotNull null
            val celsius = if (raw > 1_000L) raw / 1_000f else raw.toFloat()
            if (celsius !in 10f..110f) return@mapNotNull null
            val type = File(zone, "type").takeIf { it.canRead() }?.readText()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            type to celsius
        }
        val nonBattery = readings.filterNot { (type, _) -> type.contains("battery") }
        val candidates = nonBattery.ifEmpty { readings }
        candidates.firstOrNull { (type, _) ->
            type.contains("cpu") || type.contains("soc") || type.contains("gpu") || type.contains("ap")
        }?.second ?: candidates.maxByOrNull { it.second }?.second
    }.getOrNull()
}

fun Long.toGbLabel(): String {
    val gb = this / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
}
