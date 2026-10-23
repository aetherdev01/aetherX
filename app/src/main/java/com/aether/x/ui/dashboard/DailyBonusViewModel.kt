package com.aether.x.ui.dashboard

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.core.ads.DailyBonusClaimResult
import com.aether.x.core.ads.DailyBonusManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aether.x.core.locale.str
import com.aether.x.R

data class DailyBonusUiState(
    val loading: Boolean = true,
    val claimedToday: Boolean = false,
    val streak: Int = 0,
    val nextRewardCredits: Int = DailyBonusManager.BASE_CREDITS,
    val claiming: Boolean = false,
    /** Member sudah unlimited — kartu tampil sebagai info saja, tanpa iklan. */
    val isMember: Boolean = false,
)

/**
 * ViewModel kartu Bonus Harian di Dashboard. Logika aturan (streak, hadiah,
 * iklan reward) ada di [DailyBonusManager]; di sini hanya state UI + toast.
 */
class DailyBonusViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)
    private val manager = DailyBonusManager(preferences, AetherXApp.rewardedAdManager)

    private val _state = MutableStateFlow(DailyBonusUiState())
    val state: StateFlow<DailyBonusUiState> = _state.asStateFlow()

    init {
        // Status klaim ikut diperbarui tiap preferensi berubah (mis. setelah
        // klaim, atau saat membership aktif/berakhir).
        preferences.preferences.onEach { prefs ->
            val status = manager.status()
            _state.update {
                it.copy(
                    loading = false,
                    claimedToday = status.claimedToday,
                    streak = status.streak,
                    nextRewardCredits = status.nextRewardCredits,
                    isMember = prefs.isMembershipActive,
                )
            }
        }.launchIn(viewModelScope)
    }

    /** Dipanggil saat layar kembali aktif — jaga-jaga hari sudah berganti. */
    fun refresh() {
        viewModelScope.launch {
            val status = manager.status()
            _state.update {
                it.copy(
                    claimedToday = status.claimedToday,
                    streak = status.streak,
                    nextRewardCredits = status.nextRewardCredits,
                )
            }
        }
    }

    fun claim(activity: Activity?) {
        if (activity == null || _state.value.claiming) return
        _state.update { it.copy(claiming = true) }
        viewModelScope.launch {
            val result = manager.claim(activity)
            _state.update { it.copy(claiming = false) }
            val app = getApplication<Application>()
            when (result) {
                is DailyBonusClaimResult.Granted -> {
                    refresh()
                    app.showAetherToast(str(R.string.daily_bonus_bonus_diterima_kredit_preset_kernel, result.credits))
                }
                DailyBonusClaimResult.AlreadyClaimed ->
                    app.showAetherToast(str(R.string.daily_bonus_bonus_hari_ini_sudah_diklaim))
                DailyBonusClaimResult.AdNotReady ->
                    app.showAetherToast(str(R.string.kernel_preset_ad_not_ready))
                DailyBonusClaimResult.Cancelled ->
                    app.showAetherToast(str(R.string.daily_bonus_tonton_iklan_sampai_selesai_untuk))
            }
        }
    }
}
