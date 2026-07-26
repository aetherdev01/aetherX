package com.aether.x.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.core.monitor.RootSystemMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Jumlah sampel yang disimpan untuk digambar di grafik (garis bergeser ke kiri setelah penuh). */
private const val HISTORY_SIZE = 60

/** Interval polling — cukup cepat untuk terasa "real-time", cukup jarang untuk tidak boros baterai/CPU. */
private const val POLL_INTERVAL_MS = 500L

data class RootMonitorUiState(
    val nativeAvailable: Boolean = true,
    val cpuAggregateHistory: List<Float> = emptyList(),
    val cpuPerCoreLatest: List<Float> = emptyList(),
    val gpuLoadHistory: List<Float> = emptyList(),
    val gpuFreqMhz: Float? = null,
    val hasSamples: Boolean = false,
)

/**
 * RootMonitorViewModel — mengelola polling loop [RootSystemMonitor] dan
 * rolling history buffer untuk grafik CPU/GPU real-time. Dipakai HANYA
 * dari layar monitor root-only (lihat RootMonitorScreen.kt) — pemanggil
 * WAJIB memastikan `PrivilegeStatus.activeBackend == PrivilegeBackend.ROOT`
 * sebelum menampilkan layar ini sama sekali (gating dilakukan di level
 * navigasi/drawer, konsisten dengan Kernel Manager & Build Prop yang juga
 * root-only — lihat TweakScreen.kt).
 *
 * Polling otomatis berhenti saat [onPause] dipanggil (mis. layar tidak
 * lagi terlihat) dan dimulai lagi dari [onResume], supaya tidak terus
 * membaca /proc/stat & sysfs di background saat pengguna pindah tab.
 */
class RootMonitorViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        RootMonitorUiState(nativeAvailable = RootSystemMonitor.isNativeAvailable),
    )
    val state: StateFlow<RootMonitorUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun onResume() {
        if (!RootSystemMonitor.isNativeAvailable) return
        if (pollingJob?.isActive == true) return

        RootSystemMonitor.resetDelta()
        _state.update {
            RootMonitorUiState(
                nativeAvailable = true,
                cpuAggregateHistory = emptyList(),
                cpuPerCoreLatest = emptyList(),
                gpuLoadHistory = emptyList(),
                gpuFreqMhz = null,
                hasSamples = false,
            )
        }

        pollingJob = viewModelScope.launch {
            while (true) {
                val cpu = withContext(Dispatchers.IO) { RootSystemMonitor.readCpuSnapshot() }
                val gpu = withContext(Dispatchers.IO) { RootSystemMonitor.readGpuSnapshot() }

                // Sampel pertama setelah reset selalu -1 (belum ada delta pembanding) — dibuang, tidak masuk history.
                if (cpu != null && cpu.aggregatePercent >= 0f) {
                    _state.update { current ->
                        current.copy(
                            cpuAggregateHistory = (current.cpuAggregateHistory + cpu.aggregatePercent).takeLast(HISTORY_SIZE),
                            cpuPerCoreLatest = cpu.perCorePercent,
                            hasSamples = true,
                        )
                    }
                }

                if (gpu?.loadPercent != null) {
                    _state.update { current ->
                        current.copy(
                            gpuLoadHistory = (current.gpuLoadHistory + gpu.loadPercent).takeLast(HISTORY_SIZE),
                            gpuFreqMhz = gpu.freqMhz,
                        )
                    }
                } else if (gpu != null) {
                    _state.update { it.copy(gpuFreqMhz = gpu.freqMhz) }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun onPause() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        pollingJob?.cancel()
    }
}
