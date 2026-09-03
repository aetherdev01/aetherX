package com.aether.x.ui.whatsnew

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.BuildConfig
import com.aether.x.data.AetherXPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Menentukan kapan sheet "Apa yang Baru" ditampilkan: sekali saja, tepat
 * setelah app di-update ke versionCode yang lebih baru dari terakhir kali
 * user melihat changelog (`lastSeenChangelogVersionCode` di DataStore).
 *
 * SENGAJA lokal (bukan dari Firestore `config/update` yang dipakai
 * [com.aether.x.ui.update.UpdateGate]) — dokumen itu isinya changelog versi
 * TERBARU yang tersedia di server (dorongan untuk update), sedangkan sheet
 * ini adalah changelog versi yang SEDANG BERJALAN saat ini (recap setelah
 * user selesai update). Isi teksnya didefinisikan lokal di
 * `strings.xml` (`whatsnew_v3_5_*`) per rilis.
 */
class WhatsNewViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _shouldShow = MutableStateFlow(false)
    val shouldShow: StateFlow<Boolean> = _shouldShow.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSeen = preferences.preferences.first().lastSeenChangelogVersionCode
            _shouldShow.value = lastSeen < BuildConfig.VERSION_CODE
        }
    }

    fun dismiss() {
        _shouldShow.value = false
        viewModelScope.launch {
            preferences.setLastSeenChangelogVersionCode(BuildConfig.VERSION_CODE)
        }
    }
}
