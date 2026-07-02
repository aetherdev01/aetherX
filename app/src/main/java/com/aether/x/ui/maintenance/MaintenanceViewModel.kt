package com.aether.x.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.data.MaintenanceRepository
import com.aether.x.data.MaintenanceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel tunggal untuk status maintenance, dipasang di root aplikasi
 * (lihat [com.aether.x.MainActivity]/AetherXRoot) — bukan per-tab — supaya
 * dialog blocking-nya bisa menutupi SELURUH layar aplikasi (termasuk saat
 * onboarding, bukan hanya tab MainScreen).
 */
class MaintenanceViewModel : ViewModel() {

    private val repository = MaintenanceRepository()

    private val _status = MutableStateFlow(MaintenanceStatus(enabled = false, title = "", message = ""))
    val status: StateFlow<MaintenanceStatus> = _status.asStateFlow()

    init {
        // Berlangganan seumur hidup ViewModel (yaitu seumur hidup Activity) —
        // TIDAK PERNAH berhenti mendengarkan selama aplikasi terbuka, supaya
        // admin bisa mengaktifkan maintenance kapan saja (bahkan saat
        // pengguna sedang memakai aplikasi) dan dialog blocking langsung
        // muncul di layar mana pun tanpa pengguna perlu membuka ulang app.
        viewModelScope.launch {
            repository.observe().collect { result ->
                _status.value = result
            }
        }
    }
}
