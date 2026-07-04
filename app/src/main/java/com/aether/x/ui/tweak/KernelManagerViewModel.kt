package com.aether.x.ui.tweak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.kernel.CpuCoreInfo
import com.aether.x.core.kernel.GpuInfo
import com.aether.x.core.kernel.KernelInfoReader
import com.aether.x.core.kernel.ThermalZoneInfo
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.KernelManagerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class KernelManagerUiState(
    val loading: Boolean = true,
    val cpuCores: List<CpuCoreInfo> = emptyList(),
    val gpu: GpuInfo? = null,
    val thermalZones: List<ThermalZoneInfo> = emptyList(),
    val kernelVersion: String? = null,
    val message: String? = null,
)

/**
 * ViewModel section "Kernel Manager" (khusus backend Root — lihat gating
 * di TweakScreen, section ini TIDAK ditampilkan untuk Shizuku/NONE karena
 * baca-tulis sysfs mentah di sini butuh akses root sungguhan).
 *
 * BEDA DENGAN [TweakViewModel]: [TweakViewModel] mengelola toggle "mode"
 * bernama (GPU Performance Mode, dst.) yang di baliknya menerapkan
 * nilai/governor TERBATAS dan SUDAH DITENTUKAN. ViewModel ini menampilkan
 * dan menulis nilai MENTAH langsung dari kernel (frekuensi per-core,
 * daftar governor lengkap yang didukung) — dua sistem independen yang
 * boleh dipakai bersamaan (lihat KDoc [KernelManagerRepository]).
 *
 * THERMAL DI-POLL BERKALA setiap [THERMAL_POLL_INTERVAL_MS] SELAMA
 * ViewModel ini hidup (mis. selama Composable section-nya berada di
 * composition) — otomatis berhenti sendiri saat ViewModel di-clear karena
 * `viewModelScope` dibatalkan (loop memeriksa `isActive`). CPU/GPU TIDAK
 * di-poll otomatis (hanya dibaca ulang manual lewat [refresh] atau setelah
 * [applyCoreFrequency]/[applyCoreGovernor]/[applyGpuFrequency]/[applyGpuGovernor]
 * berhasil) karena frekuensi CPU/GPU berubah sangat cepat (tiap beberapa
 * milidetik mengikuti beban) — menampilkannya live akan membuat angka
 * "bergetar" terus dan sulit dibaca, beda dari suhu yang berubah lambat.
 */
class KernelManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = KernelInfoReader()
    private val repository = KernelManagerRepository()

    private val _state = MutableStateFlow(KernelManagerUiState())
    val state: StateFlow<KernelManagerUiState> = _state.asStateFlow()

    init {
        refresh()
        startThermalPolling()
    }

    /** Baca ulang snapshot CPU + GPU + versi kernel. Dipanggil saat pertama dibuka dan lewat tombol refresh manual di UI. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(loading = false, message = appString(R.string.kernel_manager_error_root_unavailable)) }
                return@launch
            }
            val cores = reader.readCpuCores(executor)
            val gpu = reader.readGpuInfo(executor)
            val version = reader.readKernelVersion(executor)
            _state.update {
                it.copy(
                    loading = false,
                    cpuCores = cores,
                    gpu = gpu,
                    kernelVersion = version,
                )
            }
        }
    }

    /**
     * Poll suhu termal setiap [THERMAL_POLL_INTERVAL_MS] selama ViewModel
     * ini hidup. `isActive` di kondisi loop otomatis menjadi false begitu
     * `viewModelScope` dibatalkan (ViewModel.onCleared), jadi loop ini
     * TIDAK PERNAH perlu dihentikan manual dari UI — cukup biarkan
     * ViewModel ini di-scope ke lifecycle Composable section-nya seperti
     * ViewModel Compose lain di app ini.
     */
    private fun startThermalPolling() {
        viewModelScope.launch {
            while (isActive) {
                val executor = PrivilegeManager.getExecutor()
                if (executor != null) {
                    val zones = reader.readThermalZones(executor)
                    _state.update { it.copy(thermalZones = zones) }
                }
                delay(THERMAL_POLL_INTERVAL_MS)
            }
        }
    }

    /** Terapkan frekuensi min/max ke satu core, lalu baca ulang snapshot core itu supaya UI menampilkan nilai yang BENAR-BENAR tersimpan di kernel (bisa berbeda dari yang diminta kalau kernel menolak sebagian). */
    fun applyCoreFrequency(coreIndex: Int, minKhz: Int?, maxKhz: Int?) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val result = repository.setCoreFrequency(executor, coreIndex, minKhz, maxKhz)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_core_frequency, coreIndex)) }
            }
            refreshSingleCore(executor, coreIndex)
        }
    }

    fun applyCoreGovernor(coreIndex: Int, governorName: String) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val result = repository.setCoreGovernor(executor, coreIndex, governorName)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_core_governor, coreIndex)) }
            }
            refreshSingleCore(executor, coreIndex)
        }
    }

    private suspend fun refreshSingleCore(executor: ShellExecutor, coreIndex: Int) {
        // Baca ulang SEMUA core (bukan cuma satu) karena readCpuCores() satu
        // panggilan shell untuk semua core sekaligus lebih efisien daripada
        // menambah fungsi baca-satu-core terpisah hanya untuk kasus ini —
        // lihat KDoc KernelInfoReader soal alasan "satu panggilan shell per
        // fungsi baca". coreIndex dipakai murni untuk dokumentasi intent di
        // pemanggil, tidak memengaruhi logika di sini.
        val cores = reader.readCpuCores(executor)
        _state.update { it.copy(cpuCores = cores) }
    }

    fun applyGpuFrequency(minKhz: Int?, maxKhz: Int?) {
        val path = _state.value.gpu?.devfreqPath ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val result = repository.setGpuFrequency(executor, path, minKhz, maxKhz)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_gpu_frequency)) }
            }
            _state.update { it.copy(gpu = reader.readGpuInfo(executor)) }
        }
    }

    fun applyGpuGovernor(governorName: String) {
        val path = _state.value.gpu?.devfreqPath ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val result = repository.setGpuGovernor(executor, path, governorName)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_gpu_governor)) }
            }
            _state.update { it.copy(gpu = reader.readGpuInfo(executor)) }
        }
    }

    private fun appString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private companion object {
        const val THERMAL_POLL_INTERVAL_MS = 2500L
    }
}
