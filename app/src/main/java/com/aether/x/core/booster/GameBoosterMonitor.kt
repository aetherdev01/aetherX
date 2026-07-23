package com.aether.x.core.booster

import android.content.Context
import com.aether.x.core.kernel.KernelInfoReader
import com.aether.x.core.monitor.GfxInfoFpsReader
import com.aether.x.core.monitor.SystemStatsProvider
import com.aether.x.core.permission.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class GameBoosterMonitor(private val packageName: String) {

    private val kernelInfoReader = KernelInfoReader()
    private val fallbackStatsProvider = SystemStatsProvider()
    private val fpsReader = GfxInfoFpsReader(packageName)

    fun metricsFlow(context: Context): Flow<GameBoosterMetrics> = flow {
        val fpsHistory = ArrayDeque<Int>()
        while (true) {
            // SENGAJA tetap pakai [PrivilegeManager.getExecutor] (non-suspend,
            // baca snapshot state saat ini) di sini, BUKAN
            // [PrivilegeManager.getExecutorAwaitingConnection] — loop ini
            // polling tiap [POLL_INTERVAL_MS] dan SUDAH punya fallback yang
            // baik lewat [fallbackStatsProvider] saat executor null. Kalau
            // dipaksa menunggu reconnect penuh (bisa puluhan detik saat
            // perlu rediscovery mDNS) di setiap iterasi, overlay FPS/CPU/GPU
            // akan macet berkali-kali detik alih-alih tetap menampilkan
            // angka fallback yang responsif — beda kebutuhan dari aksi
            // tweak sekali-klik yang memang lebih baik menunggu.
            val executor = PrivilegeManager.getExecutor()
            val fps = if (executor != null) {
                withContext(Dispatchers.IO) { runCatching { fpsReader.readFps(executor) }.getOrNull() }
            } else {
                null
            }
            val cpu: Int?
            val gpu: Int?
            val temp: Float?
            if (executor != null) {
                cpu = withContext(Dispatchers.IO) { runCatching { kernelInfoReader.readCpuLoadPercent(executor) }.getOrNull() }
                gpu = withContext(Dispatchers.IO) { runCatching { kernelInfoReader.readGpuBusyPercent(executor) }.getOrNull() }
                temp = withContext(Dispatchers.IO) { fallbackStatsProvider.readTemperatureCelsius(context) }
            } else {
                cpu = withContext(Dispatchers.IO) { fallbackStatsProvider.readCpuLoadPercent() }
                gpu = withContext(Dispatchers.IO) { fallbackStatsProvider.readGpuLoadPercent() }
                temp = withContext(Dispatchers.IO) { fallbackStatsProvider.readTemperatureCelsius(context) }
            }

            val ram = withContext(Dispatchers.IO) {
                runCatching {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    am?.getMemoryInfo(memInfo)
                    if (memInfo.totalMem > 0) {
                        (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100).toInt()
                    } else {
                        null
                    }
                }.getOrNull()
            }

            if (fps != null) {
                if (fpsHistory.size >= MAX_HISTORY_SIZE) fpsHistory.removeFirst()
                fpsHistory.addLast(fps)
            }

            emit(
                GameBoosterMetrics(
                    fps = fps,
                    cpuLoadPercent = cpu,
                    gpuLoadPercent = gpu,
                    temperatureCelsius = temp,
                    ramLoadPercent = ram,
                    fpsHistory = fpsHistory.toList(),
                ),
            )
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1000L
        const val MAX_HISTORY_SIZE = 60
    }
}
