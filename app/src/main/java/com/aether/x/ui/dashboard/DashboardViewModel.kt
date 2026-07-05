package com.aether.x.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.device.DeviceInfoProvider
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.monitor.SystemStatsProvider
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
)

/**
 * ViewModel tab Dashboard (dulu tab "Tweak" — lihat perintah rework):
 * ringkasan CPU load, GPU load, suhu perangkat, dan info device dasar
 * (model, chipset, RAM, penyimpanan, versi Android).
 *
 * SEMUA data di sini dibaca lewat API PUBLIK Android biasa ([SystemStatsProvider],
 * [DeviceInfoProvider]) — TIDAK butuh Shizuku ataupun Root, beda dari
 * [com.aether.x.ui.tweak.KernelManagerViewModel] yang baca sysfs mentah
 * (frekuensi per-core, governor) dan HANYA berfungsi untuk backend Root.
 * Dashboard sengaja dibuat berguna untuk SEMUA pengguna sejak pertama buka
 * aplikasi, terlepas dari metode akses yang mereka pilih.
 *
 * CPU load & GPU load di-poll berkala selama ViewModel ini hidup (mengikuti
 * pola yang sama dengan thermal polling di KernelManagerViewModel) — suhu
 * ikut di-refresh di siklus yang sama karena ketiganya sama-sama murah untuk
 * dibaca ulang tiap beberapa detik.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val statsProvider = SystemStatsProvider()

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(deviceInfo = DeviceInfoProvider.read(application)) }
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                val app = getApplication<Application>()
                val cpu = withContext(Dispatchers.IO) { statsProvider.readCpuLoadPercent() }
                val gpu = withContext(Dispatchers.IO) { statsProvider.readGpuLoadPercent() }
                val temp = withContext(Dispatchers.IO) { statsProvider.readTemperatureCelsius(app) }
                _state.update {
                    it.copy(
                        cpuLoadPercent = cpu ?: it.cpuLoadPercent,
                        gpuLoadPercent = gpu,
                        temperatureCelsius = temp ?: it.temperatureCelsius,
                    )
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
        const val POLL_INTERVAL_MS = 2000L
    }
}
