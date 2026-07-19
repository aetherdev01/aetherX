package com.aether.x.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.data.MaintenanceRepository
import com.aether.x.data.MaintenanceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaintenanceViewModel : ViewModel() {

    private val repository = MaintenanceRepository()

    private val _status = MutableStateFlow(MaintenanceStatus(enabled = false, title = "", message = ""))
    val status: StateFlow<MaintenanceStatus> = _status.asStateFlow()

    init {

        viewModelScope.launch {
            repository.observe().collect { result ->
                _status.value = result
            }
        }
    }
}
