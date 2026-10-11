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
            batteryTempC = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null,
            isCharging = charging,
            uptimeMillis = SystemClock.elapsedRealtime(),
            refreshRateHz = refreshRate,
        )
    }
}

fun Long.toGbLabel(): String {
    val gb = this / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
}
