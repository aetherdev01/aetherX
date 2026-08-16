package com.aether.x.ui.dashboard

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.theme.AccentAmber

/**
 * NoRootAdvisoryGate — pop up informasional (BUKAN blocking gate seperti
 * MaintenanceGate/UpdateGate, pengguna tetap bisa lanjut pakai app tanpa
 * menyelesaikan aksi apa pun di sini) yang muncul di Dashboard SEKALI per
 * sesi saat backend privilege aktif adalah SHIZUKU (non-root) — memberi
 * tahu bahwa sebagian fitur (root-only: Kernel Manager, Build.prop Editor,
 * GPU Performance Mode, Thermal Throttle Override, Root Monitor CPU/GPU,
 * dst — lihat TweakScreen.kt gating activeBackend == ROOT) tidak tersedia
 * di mode Shizuku, dan mengarahkan pengguna untuk membuka akses root kalau
 * device-nya mendukung.
 *
 * SENGAJA tidak dipicu untuk PrivilegeBackend.NONE (belum ada akses sama
 * sekali) — kasus itu sudah ditangani penuh oleh alur onboarding/
 * PermissionSetupScreen, menambahkan pop up lagi di Dashboard untuk kasus
 * itu hanya akan dobel dengan gate yang sudah ada di sana.
 *
 * "Jangan tampilkan lagi" disimpan permanen lewat
 * [AetherXPreferences.setNoRootAdvisoryDismissed] — BUKAN cuma sekali per
 * sesi proses seperti composition state biasa, supaya pengguna yang sudah
 * paham konsekuensi pilihannya (mis. device tidak bisa di-root) tidak terus
 * "diganggu" pop up yang sama setiap kali buka app.
 */
class NoRootAdvisoryViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    init {
        combine(
            PrivilegeManager.status,
            preferences.preferences,
        ) { status, prefs ->
            status.activeBackend == PrivilegeBackend.SHIZUKU && !prefs.noRootAdvisoryDismissed
        }.onEach { shouldShow ->
            // Hanya membuka dialog (false -> true). Setelah pengguna
            // menutupnya (lewat dismiss() di bawah), tidak dipaksa terbuka
            // lagi otomatis oleh flow ini walau status.activeBackend belum
            // berubah dari SHIZUKU — mencegah dialog "muncul lagi sendiri"
            // saat DataStore belum sempat commit write dismiss di antara
            // dua emisi flow yang berdekatan.
            if (shouldShow) _visible.value = true
        }.launchIn(viewModelScope)
    }

    /** Tutup untuk sesi ini saja — akan muncul lagi lain kali app dibuka. */
    fun dismissForNow() {
        _visible.value = false
    }

    /** Tutup permanen — tidak akan muncul lagi kecuali data app dihapus. */
    fun dismissPermanently() {
        _visible.value = false
        viewModelScope.launch {
            preferences.setNoRootAdvisoryDismissed(true)
        }
    }
}

@Composable
fun NoRootAdvisoryGate(viewModel: NoRootAdvisoryViewModel = viewModel()) {
    val visible by viewModel.visible.collectAsState()
    if (!visible) return

    PopupDialog(
        onDismissRequest = viewModel::dismissForNow,
        icon = Icons.Outlined.Shield,
        iconTint = AccentAmber,
        title = stringResource(R.string.no_root_advisory_title),
        message = stringResource(R.string.no_root_advisory_message),
        confirmLabel = stringResource(R.string.no_root_advisory_confirm),
        onConfirm = viewModel::dismissPermanently,
        dismissLabel = stringResource(R.string.no_root_advisory_dismiss),
    )
}
