package com.aether.x.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.device.DeviceInfoProvider
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.kernel.KernelInfoReader
import com.aether.x.core.monitor.SystemStatsProvider
import com.aether.x.core.permission.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val cpuLoadPercent: Int? = null,
    val gpuLoadPercent: Int? = null,
    val temperatureCelsius: Float? = null,
    val deviceInfo: DeviceInfoSnapshot? = null,
    // true kalau backend Shizuku/Root aktif TAPI node GPU busy % tetap
    // tidak terbaca (chipset non-Adreno seperti Mali/PowerVR umumnya tidak
    // punya node persentase) — dipakai UI untuk membedakan "belum sempat
    // terbaca" vs "memang tidak didukung chipset ini".
    val gpuUnsupported: Boolean = false,
)

/**
 * ViewModel tab Dashboard: ringkasan CPU load, GPU load, suhu perangkat,
 * dan info device dasar (model, chipset, RAM, penyimpanan, versi Android).
 *
 * SUMBER DATA CPU/GPU/SUHU (REWORK — sebelumnya baca langsung dari proses
 * app lewat [SystemStatsProvider] tanpa shell, yang membuat CPU/GPU sering
 * tampil "-" karena sebagian node sysfs, terutama `gpu_busy_percentage`,
 * DIBATASI PERMISSION untuk proses biasa di banyak ROM walau datanya
 * publik lewat shell):
 * 1. Kalau backend Shizuku/Root aktif ([PrivilegeManager.getExecutor] tidak
 *    null): baca lewat [KernelInfoReader.readCpuLoadPercent] dan
 *    [KernelInfoReader.readGpuBusyPercent] — keduanya jalan lewat shell,
 *    jauh lebih reliable karena tidak kena batasan permission per-app.
 * 2. Kalau backend NONE (belum aktifkan Shizuku/Root): fallback ke
 *    [SystemStatsProvider] (baca langsung dari proses app) — tetap
 *    berfungsi untuk CPU/suhu (yang memang publik), GPU load kemungkinan
 *    besar tetap "-" di kondisi ini karena keterbatasan izin bawaan
 *    Android, BUKAN bug Dashboard ini.
 *
 * Info device (model, RAM, storage, dst.) SELALU lewat [DeviceInfoProvider]
 * (API publik Android biasa) terlepas dari backend privilese apa pun.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val fallbackStatsProvider = SystemStatsProvider()
    private val kernelInfoReader = KernelInfoReader()

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(application)) }
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                val executor = PrivilegeManager.getExecutor()
                if (executor != null) {
                    // Jalur Shizuku/Root: satu panggilan shell per metrik,
                    // dijalankan di Dispatchers.IO karena ShellExecutor.exec
                    // adalah operasi blocking (proses shell).
                    val cpu = withContext(Dispatchers.IO) {
                        runCatching { kernelInfoReader.readCpuLoadPercent(executor) }.getOrNull()
                    }
                    val gpu = withContext(Dispatchers.IO) {
                        runCatching { kernelInfoReader.readGpuBusyPercent(executor) }.getOrNull()
                    }
                    val temp = withContext(Dispatchers.IO) {
                        fallbackStatsProvider.readTemperatureCelsius(getApplication())
                    }
                    _state.update {
                        it.copy(
                            cpuLoadPercent = cpu ?: it.cpuLoadPercent,
                            gpuLoadPercent = gpu,
                            gpuUnsupported = gpu == null,
                            temperatureCelsius = temp ?: it.temperatureCelsius,
                        )
                    }
                } else {
                    // Jalur fallback (backend NONE): baca langsung dari proses
                    // app tanpa shell — cukup untuk CPU load & suhu, GPU load
                    // kemungkinan besar tetap null karena permission (lihat KDoc
                    // kelas ini).
                    val app = getApplication<Application>()
                    val cpu = withContext(Dispatchers.IO) { fallbackStatsProvider.readCpuLoadPercent() }
                    val gpu = withContext(Dispatchers.IO) { fallbackStatsProvider.readGpuLoadPercent() }
                    val temp = withContext(Dispatchers.IO) { fallbackStatsProvider.readTemperatureCelsius(app) }
                    _state.update {
                        it.copy(
                            cpuLoadPercent = cpu ?: it.cpuLoadPercent,
                            gpuLoadPercent = gpu,
                            gpuUnsupported = gpu == null,
                            temperatureCelsius = temp ?: it.temperatureCelsius,
                        )
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Baca ulang info device (mis. setelah storage berubah signifikan). Dipanggil manual lewat tombol refresh. */
    fun refreshDeviceInfo() {
        val app = getApplication<Application>()
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(app)) }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2500L
    }
}
