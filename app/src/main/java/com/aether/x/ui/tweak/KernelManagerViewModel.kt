package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
import com.aether.x.core.ads.RewardGate
import com.aether.x.core.ads.RewardGateResult
import com.aether.x.core.kernel.CpuCoreInfo
import com.aether.x.core.kernel.GpuInfo
import com.aether.x.core.kernel.KernelInfoReader
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.KernelManagerRepository
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KernelManagerUiState(
    val loading: Boolean = true,
    val cpuCores: List<CpuCoreInfo> = emptyList(),
    val gpu: GpuInfo? = null,
    val kernelVersion: String? = null,
    val message: String? = null,
    val applyingPreset: KernelPreset? = null,
    /** Non-null kalau preset ini kena kuota gratis habis & butuh nonton
     * iklan dulu — lihat KDoc [KernelManagerViewModel.applyPreset]. */
    val pendingPresetRequiresAd: KernelPreset? = null,
    /** null = member (unlimited, jangan tampilkan badge kuota di UI). */
    val remainingFreePresetUses: Int? = null,
)

enum class KernelPreset { BATTERY_SAVER, BALANCED, PERFORMANCE }

class KernelManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = KernelInfoReader()
    private val repository = KernelManagerRepository()
    private val preferences = AetherXPreferences(application)
    private val rewardGate = RewardGate(preferences, AetherXApp.rewardedAdManager)

    private val _state = MutableStateFlow(KernelManagerUiState())
    val state: StateFlow<KernelManagerUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { refreshRemainingFreeUses() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
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

    fun applyCoreFrequency(coreIndex: Int, minKhz: Int?, maxKhz: Int?, activity: Activity? = null) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            val result = repository.setCoreFrequency(executor, coreIndex, minKhz, maxKhz)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_core_frequency, coreIndex)) }
            }
            refreshSingleCore(executor, coreIndex)
            if (result.success) maybeShowAd(activity)
        }
    }

    fun applyCoreGovernor(coreIndex: Int, governorName: String, activity: Activity? = null) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            val result = repository.setCoreGovernor(executor, coreIndex, governorName)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_core_governor, coreIndex)) }
            }
            refreshSingleCore(executor, coreIndex)
            if (result.success) maybeShowAd(activity)
        }
    }

    private suspend fun refreshSingleCore(executor: ShellExecutor, coreIndex: Int) {

        val cores = reader.readCpuCores(executor)
        _state.update { it.copy(cpuCores = cores) }
    }

    fun applyGpuFrequency(minKhz: Int?, maxKhz: Int?, activity: Activity? = null) {
        val path = _state.value.gpu?.devfreqPath ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            val result = repository.setGpuFrequency(executor, path, minKhz, maxKhz)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_gpu_frequency)) }
            }
            _state.update { it.copy(gpu = reader.readGpuInfo(executor)) }
            if (result.success) maybeShowAd(activity)
        }
    }

    fun applyGpuGovernor(governorName: String, activity: Activity? = null) {
        val path = _state.value.gpu?.devfreqPath ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            val result = repository.setGpuGovernor(executor, path, governorName)
            if (!result.success) {
                _state.update { it.copy(message = appString(R.string.kernel_manager_error_gpu_governor)) }
            }
            _state.update { it.copy(gpu = reader.readGpuInfo(executor)) }
            if (result.success) maybeShowAd(activity)
        }
    }

    /**
     * Terapkan preset (Hemat Baterai/Seimbang/Performa) ke SEMUA core CPU
     * sekaligus + GPU (kalau tersedia), dalam satu aksi — dibuat supaya
     * pengguna awam tidak perlu memahami/mengatur slider index per-core
     * secara manual satu-satu. Governor & frekuensi target dipilih dari
     * daftar yang BENAR-BENAR didukung chipset (availableGovernors/
     * availableFrequenciesKhz), bukan nilai bebas — konsisten dengan
     * batasan yang sama dipakai kontrol manual di layar ini.
     */
    /**
     * v3.5 — RewardGate akhirnya benar-benar dipakai (sebelumnya
     * infrastrukturnya sudah ada penuh di RewardGate.kt/
     * UnityRewardedAdManager.kt tapi TIDAK ADA satupun pemanggilnya di
     * seluruh app). Preset Kernel Manager dipilih sebagai fitur pertama
     * yang di-gate karena paling "sekali klik, hasil langsung terasa" —
     * pas buat dijadiin insentif nonton iklan.
     *
     * User FREE dapat [PRESET_FREE_USES_PER_DAY] kali terap preset
     * gratis per hari (reset otomatis tiap hari lewat RewardQuotaState.
     * dateKey — lihat AetherXPreferences.getRewardQuota). Kalau kuota
     * habis, applyPreset TIDAK langsung jalan — malah set
     * pendingPresetRequiresAd supaya UI (KernelManagerSection) tampilkan
     * prompt "tonton iklan untuk +1". Member (isMembershipActive) selalu
     * RewardGateResult.Allowed, tidak pernah kena prompt ini sama sekali.
     */
    fun applyPreset(preset: KernelPreset, activity: Activity? = null) {
        viewModelScope.launch {
            val isMember = preferences.preferences.first().isMembershipActive
            when (rewardGate.checkAccess(PRESET_FEATURE_KEY, isMember, PRESET_FREE_USES_PER_DAY)) {
                RewardGateResult.RequiresAd -> {
                    _state.update { it.copy(pendingPresetRequiresAd = preset) }
                }
                RewardGateResult.Allowed -> {
                    performApplyPreset(preset, activity, isMember)
                }
            }
        }
    }

    /** Dipanggil dari tombol "Tonton Iklan" di prompt kuota habis. */
    fun watchAdForPendingPreset(activity: Activity) {
        val pending = _state.value.pendingPresetRequiresAd ?: return
        viewModelScope.launch {
            when (rewardGate.watchAdForCredit(PRESET_FEATURE_KEY, activity)) {
                RewardGate.WatchAdResult.CreditGranted -> {
                    _state.update { it.copy(pendingPresetRequiresAd = null) }
                    val isMember = preferences.preferences.first().isMembershipActive
                    performApplyPreset(pending, activity, isMember)
                }
                RewardGate.WatchAdResult.AdNotReady -> {
                    _state.update {
                        it.copy(message = appString(R.string.kernel_preset_ad_not_ready))
                    }
                }
                RewardGate.WatchAdResult.Cancelled -> {
                    // User batal di tengah nonton — biarkan prompt tetap
                    // terbuka, jangan konsumsi apapun, jangan tutup dialog.
                }
            }
        }
    }

    fun dismissPendingPresetPrompt() {
        _state.update { it.copy(pendingPresetRequiresAd = null) }
    }

    private suspend fun refreshRemainingFreeUses() {
        val isMember = preferences.preferences.first().isMembershipActive
        val remaining = if (isMember) {
            null
        } else {
            rewardGate.remainingFreeUses(PRESET_FEATURE_KEY, PRESET_FREE_USES_PER_DAY)
        }
        _state.update { it.copy(remainingFreePresetUses = remaining) }
    }

    private suspend fun performApplyPreset(preset: KernelPreset, activity: Activity?, isMember: Boolean) {
        val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return
        _state.update { it.copy(applyingPreset = preset) }

        val cores = _state.value.cpuCores
        cores.forEach { core ->
            if (core.isUnavailable) return@forEach
            val freqs = core.availableFrequenciesKhz.sorted()
            if (freqs.isEmpty()) return@forEach
            val governor = pickGovernor(core.availableGovernors, preset)
            val (minKhz, maxKhz) = presetFrequencyRange(freqs, preset)
            if (governor != null) repository.setCoreGovernor(executor, core.coreIndex, governor)
            repository.setCoreFrequency(executor, core.coreIndex, minKhz = minKhz, maxKhz = maxKhz)
        }

        val gpu = _state.value.gpu
        val gpuPath = gpu?.devfreqPath
        if (gpu != null && gpuPath != null) {
            val freqs = gpu.availableFrequenciesKhz.sorted()
            if (freqs.isNotEmpty()) {
                val governor = pickGovernor(gpu.availableGovernors, preset)
                val (minKhz, maxKhz) = presetFrequencyRange(freqs, preset)
                if (governor != null) repository.setGpuGovernor(executor, gpuPath, governor)
                repository.setGpuFrequency(executor, gpuPath, minKhz = minKhz, maxKhz = maxKhz)
            }
        }

        rewardGate.consumeUse(PRESET_FEATURE_KEY, isMember, PRESET_FREE_USES_PER_DAY)

        val refreshedCores = reader.readCpuCores(executor)
        val refreshedGpu = reader.readGpuInfo(executor)
        val presetLabel = appString(presetLabelRes(preset))
        _state.update {
            it.copy(
                cpuCores = refreshedCores,
                gpu = refreshedGpu,
                applyingPreset = null,
                message = getApplication<Application>().getString(
                    R.string.kernel_manager_preset_applied,
                    presetLabel,
                ),
            )
        }
        refreshRemainingFreeUses()
        maybeShowAd(activity)
    }

    private fun pickGovernor(available: List<String>, preset: KernelPreset): String? {
        if (available.isEmpty()) return null
        val priority = when (preset) {
            KernelPreset.PERFORMANCE -> listOf("performance")
            KernelPreset.BATTERY_SAVER -> listOf("powersave")
            KernelPreset.BALANCED -> listOf("schedutil", "interactive", "ondemand")
        }
        return priority.firstOrNull { it in available } ?: available.first()
    }

    /** freqs HARUS sudah terurut ascending. Return Pair(minKhz, maxKhz). */
    private fun presetFrequencyRange(freqs: List<Int>, preset: KernelPreset): Pair<Int, Int> {
        val lowest = freqs.first()
        val highest = freqs.last()
        return when (preset) {
            KernelPreset.PERFORMANCE -> highest to highest
            KernelPreset.BALANCED -> lowest to highest
            KernelPreset.BATTERY_SAVER -> {
                val capIndex = ((freqs.size - 1) * 0.4).toInt().coerceIn(0, freqs.size - 1)
                lowest to freqs[capIndex]
            }
        }
    }

    private fun presetLabelRes(preset: KernelPreset): Int = when (preset) {
        KernelPreset.BATTERY_SAVER -> R.string.kernel_preset_battery_saver
        KernelPreset.BALANCED -> R.string.kernel_preset_balanced
        KernelPreset.PERFORMANCE -> R.string.kernel_preset_performance
    }

    private suspend fun maybeShowAd(activity: Activity?) {
        if (activity == null) return
        val isMember = preferences.preferences.first().isMembershipActive
        AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
    }

    private fun appString(resId: Int, vararg args: Any): String {
        val text = getApplication<Application>().getString(resId, *args)
        getApplication<Application>().showAetherToast(text)
        return text
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        private const val PRESET_FEATURE_KEY = "kernel_preset"
        const val PRESET_FREE_USES_PER_DAY = 3
    }
}
