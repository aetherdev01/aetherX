package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
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
)

class KernelManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = KernelInfoReader()
    private val repository = KernelManagerRepository()
    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(KernelManagerUiState())
    val state: StateFlow<KernelManagerUiState> = _state.asStateFlow()

    init {
        refresh()
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
}
