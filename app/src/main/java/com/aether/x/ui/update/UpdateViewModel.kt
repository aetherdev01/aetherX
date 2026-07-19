package com.aether.x.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.BuildConfig
import com.aether.x.data.UpdateInfo
import com.aether.x.data.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class UpdateUiState(
    val visible: Boolean,
    val info: UpdateInfo,
    val currentVersionCode: Int,
    val currentVersionName: String,
)

class UpdateViewModel : ViewModel() {

    private val repository = UpdateRepository()
    private val currentVersionCode = BuildConfig.VERSION_CODE
    private val currentVersionName = BuildConfig.VERSION_NAME

    private val _dismissedVersionCode = MutableStateFlow(0)

    private val _state = MutableStateFlow(
        UpdateUiState(
            visible = false,
            info = UpdateInfo(0, "", "", "", false, false),
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
        ),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observe(), _dismissedVersionCode) { info, dismissed ->
                val hasNewerVersion = info.latestVersionCode > currentVersionCode
                val alreadyDismissed = info.latestVersionCode == dismissed
                UpdateUiState(
                    visible = hasNewerVersion && !alreadyDismissed,
                    info = info,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                )
            }.collect { _state.value = it }
        }
    }

    fun dismiss() {
        _dismissedVersionCode.value = _state.value.info.latestVersionCode
    }
}
