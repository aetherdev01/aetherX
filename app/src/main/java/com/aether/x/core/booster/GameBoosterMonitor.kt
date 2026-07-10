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

/**
 * Membaca FPS/CPU/GPU/Suhu secara berkala (interval [POLL_INTERVAL_MS])
 * SELAMA sebuah [GameBoosterSession] aktif — dipakai
 * [com.aether.x.core.overlay.GameBoosterOverlayService] untuk mengisi
 * [GameBoosterSession.metrics] (lihat perintah rework: "ada monitoring
 * seperti grafik").
 *
 * SUMBER DATA (MENIRU pola [com.aether.x.ui.dashboard.DashboardViewModel]
 * versi LAMA sebelum monitor CPU/GPU/Suhu dipindah ke sini — lihat KDoc
 * versi terbaru [com.aether.x.ui.dashboard.DashboardViewModel] soal
 * kenapa monitoring itu dipindah): backend Shizuku/Root aktif → baca lewat
 * [KernelInfoReader] (shell, reliable); backend NONE → fallback
 * [SystemStatsProvider] (baca langsung proses app, GPU load kemungkinan
 * besar tetap null karena keterbatasan permission Android biasa).
 *
 * FPS SELALU lewat [GfxInfoFpsReader] (`dumpsys gfxinfo`) — TIDAK ADA
 * fallback non-shell untuk FPS (data ini memang tidak tersedia sama sekali
 * tanpa shell), jadi [GameBoosterMetrics.fps] akan tetap null kalau backend
 * NONE — UI booster HARUS menampilkan hint "aktifkan Root/Shizuku untuk
 * FPS real-time" dalam kondisi ini, bukan menampilkan angka palsu.
 */
class GameBoosterMonitor(private val packageName: String) {

    private val kernelInfoReader = KernelInfoReader()
    private val fallbackStatsProvider = SystemStatsProvider()
    private val fpsReader = GfxInfoFpsReader(packageName)

    /**
     * Flow tak berkesudahan (sampai collector-nya di-cancel) yang meng-emit
     * [GameBoosterMetrics] baru setiap [POLL_INTERVAL_MS]. [GameBoosterMetrics.fpsHistory]
     * dikelola INTERNAL oleh flow ini (bukan oleh pemanggil) supaya jendela
     * riwayat konsisten terlepas dari berapa kali/di mana flow ini
     * di-collect ulang.
     */
    fun metricsFlow(context: Context): Flow<GameBoosterMetrics> = flow {
        val fpsHistory = ArrayDeque<Int>()
        while (true) {
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
            // RAM SELALU lewat ActivityManager.getMemoryInfo standar,
            // TIDAK LEWAT executor Root/Shizuku sama sekali — API publik
            // Android biasa ini tersedia terlepas dari status backend
            // privilege, beda dari cpu/gpu/temp di atas.
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
