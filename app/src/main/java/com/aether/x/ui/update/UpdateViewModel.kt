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

/**
 * State yang dipakai [UpdateGate] untuk memutuskan apakah dialog update
 * perlu ditampilkan. Berbeda dari [MaintenanceGate] yang TIDAK BISA
 * di-dismiss, dialog update ini opsional (lihat [UpdateGate]) — begitu
 * pengguna menekan "Nanti", [dismissedVersionCode] disimpan supaya dialog
 * yang SAMA tidak muncul berulang-ulang selama sesi aplikasi ini masih
 * berjalan. Dismiss ini murni di memori (bukan DataStore/preferences) jadi
 * akan muncul lagi kalau aplikasi benar-benar ditutup dan dibuka ulang —
 * ini disengaja, supaya update yang tertunda tidak terlupakan selamanya.
 */
data class UpdateUiState(
    val visible: Boolean,
    val info: UpdateInfo,
    val currentVersionCode: Int,
)

/**
 * ViewModel tunggal untuk info update, dipasang di root aplikasi (lihat
 * [com.aether.x.MainActivity]/AetherXRoot) — bukan per-tab — supaya dialog
 * update bisa muncul di layar mana pun, mengikuti pola [MaintenanceGate].
 */
class UpdateViewModel : ViewModel() {

    private val repository = UpdateRepository()
    private val currentVersionCode = BuildConfig.VERSION_CODE

    private val _dismissedVersionCode = MutableStateFlow(0)

    private val _state = MutableStateFlow(
        UpdateUiState(
            visible = false,
            info = UpdateInfo(0, "", "", "", false),
            currentVersionCode = currentVersionCode,
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
                )
            }.collect { _state.value = it }
        }
    }

    /** Dipanggil saat pengguna menekan "Nanti" — sembunyikan dialog untuk versi ini saja. */
    fun dismiss() {
        _dismissedVersionCode.value = _state.value.info.latestVersionCode
    }
}
