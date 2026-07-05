package com.aether.x.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.Locale

/**
 * Snapshot info dasar perangkat (model, chipset, RAM, penyimpanan, versi
 * Android) — SEMUA dibaca lewat API publik Android biasa
 * ([android.os.Build], [ActivityManager], [StatFs]), TIDAK butuh Shizuku
 * ataupun Root sama sekali. Ini yang membedakannya dari
 * [com.aether.x.core.kernel.KernelInfoReader] (baca sysfs mentah, khusus
 * Root) — section "Info Device" di tab Dashboard harus tetap berguna untuk
 * SEMUA pengguna, termasuk yang belum/tidak mengaktifkan Shizuku/Root.
 */
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
)

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
        )
    }
}

/** Format byte ke GB dengan 1 angka desimal, mis. "7.4 GB". */
fun Long.toGbLabel(): String {
    val gb = this / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gb)
}
