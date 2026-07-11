package com.aether.x.ui.appmanager

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
import com.aether.x.core.appmanager.AppManagerCatalog
import com.aether.x.core.appmanager.InstalledAppEntry
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppManagerRepository
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppManagerUiState(
    val loading: Boolean = true,
    val apps: List<InstalledAppEntry> = emptyList(),
    val searchQuery: String = "",
    /** Package name yang SEDANG diproses (freeze/unfreeze berjalan) — dipakai UI untuk nonaktifkan toggle sementara, mencegah dobel-tap. */
    val pendingPackageName: String? = null,
    val message: String? = null,
) {
    /** Daftar app setelah difilter [searchQuery] (cocok nama tampilan ATAU package name, tidak case-sensitive). */
    val filteredApps: List<InstalledAppEntry>
        get() = if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
}

/**
 * ViewModel untuk App Manager (khusus backend Root — lihat gating di
 * TweakScreen/drawer, sama seperti Kernel Manager). BEDA dari
 * [com.aether.x.ui.tweak.KernelManagerViewModel]: App Manager berurusan
 * dengan `PackageManager` + `pm` shell command untuk aplikasi, bukan
 * sysfs kernel — tapi mengikuti pola AndroidViewModel + StateFlow yang
 * sama untuk konsistensi.
 *
 * TIDAK ADA POLLING otomatis di sini (beda dari thermal di Kernel
 * Manager) — status frozen aplikasi TIDAK berubah sendiri tanpa aksi
 * pengguna (beda dari suhu yang terus berubah mengikuti beban), jadi
 * cukup dibaca ulang saat: pertama dibuka, tombol refresh manual, atau
 * setelah toggle freeze/unfreeze berhasil.
 */
class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val catalog = AppManagerCatalog
    private val repository = AppManagerRepository()
    // RILIS v2.0 (lihat perintah rework — "perbaiki iklan yang hanya muncul
    // di fitur tutup semua apps, jadikan lebih konsisten di semua fitur"):
    // dipakai HANYA untuk baca isMembershipActive sebelum memanggil
    // AetherXApp.interstitialAdGate.maybeShow (lihat forceStopApp/
    // clearCacheApp di bawah) — pola SAMA PERSIS dengan
    // TweakViewModel.preferences.
    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(AppManagerUiState())
    val state: StateFlow<AppManagerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(loading = false, message = appString(R.string.app_manager_error_root_unavailable)) }
                return@launch
            }
            val apps = catalog.loadManageableApps(getApplication(), executor)
            _state.update { it.copy(loading = false, apps = apps) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /**
     * Toggle freeze/unfreeze satu app. [entry] (termasuk `entry.isFrozen`)
     * diteruskan APA ADANYA dari UI (bukan dibaca ulang dari state di sini)
     * supaya tombol yang ditekan pengguna SELALU menerapkan aksi kebalikan
     * dari yang mereka LIHAT saat menekan, walaupun ada kemungkinan kecil
     * state berubah di antara render dan tap (mis. refresh berjalan
     * bersamaan).
     */
    fun toggleFreeze(entry: InstalledAppEntry) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update {
                    it.copy(
                        pendingPackageName = null,
                        message = appString(R.string.app_manager_error_root_unavailable),
                    )
                }
                return@launch
            }
            val result = if (entry.isFrozen) {
                repository.unfreeze(executor, entry.packageName)
            } else {
                repository.freeze(executor, entry.packageName)
            }
            if (!result.success) {
                _state.update {
                    it.copy(
                        message = appString(
                            if (entry.isFrozen) R.string.app_manager_error_unfreeze else R.string.app_manager_error_freeze,
                            entry.label,
                        ),
                    )
                }
            }
            // Baca ulang status app INI dari sumber kebenaran (pm list
            // packages -d) alih-alih optimistically flip di memori — kalau
            // pm menolak diam-diam (mis. app tidak boleh di-disable di
            // perangkat tertentu), UI harus menampilkan status yang
            // SEBENARNYA, bukan yang diasumsikan berhasil.
            val refreshedApps = catalog.loadManageableApps(getApplication(), executor)
            _state.update { it.copy(apps = refreshedApps, pendingPackageName = null) }
        }
    }

    /**
     * Force stop satu app — reversibel/tidak destruktif (lihat KDoc
     * [AppManagerRepository.forceStop]), jadi TIDAK butuh dialog konfirmasi
     * di UI, beda dari [clearCacheApp] di bawah.
     *
     * @param activity RILIS v2.0 (lihat perintah rework — "perbaiki iklan
     * yang hanya muncul di fitur tutup semua apps, jadikan lebih konsisten
     * di semua fitur"): parameter TRANSIENT (TIDAK disimpan di ViewModel,
     * lihat KDoc [com.aether.x.ui.tweak.TweakViewModel.onKillBackgroundAppsChange]
     * untuk alasan lengkapnya), dipakai untuk memicu
     * [AetherXApp.interstitialAdGate] SETELAH aksi berhasil — SATU-SATUNYA
     * titik pemicu iklan sebelumnya hanya "Tutup Semua Aplikasi" di
     * TweakViewModel, sekarang Force Stop di App Manager juga jadi titik
     * transisi natural yang sama. Cooldown GLOBAL 1 menit di
     * InterstitialAdGate tetap berlaku otomatis lintas fitur (mencegah
     * dua-duanya menampilkan iklan berurutan kalau pengguna memakai kedua
     * fitur dalam rentang waktu singkat).
     */
    fun forceStopApp(entry: InstalledAppEntry, activity: Activity?) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update {
                    it.copy(pendingPackageName = null, message = appString(R.string.app_manager_error_root_unavailable))
                }
                return@launch
            }
            val result = repository.forceStop(executor, entry.packageName)
            _state.update {
                it.copy(
                    pendingPackageName = null,
                    message = appString(
                        if (result.success) R.string.app_manager_force_stop_success else R.string.app_manager_force_stop_error,
                        entry.label,
                    ),
                )
            }
            if (result.success && activity != null) {
                val isMember = preferences.preferences.first().isMembershipActive
                AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
            }
        }
    }

    /**
     * Bersihkan cache satu app. UI (AppManagerScreen) WAJIB memanggil ini
     * HANYA SETELAH pengguna mengonfirmasi lewat dialog — lihat KDoc
     * [AppManagerRepository.clearCache] soal kenapa aksi ini tetap dianggap
     * "sedikit destruktif" walau jauh lebih aman daripada `pm clear` biasa.
     *
     * @param activity lihat KDoc parameter sama di [forceStopApp] di atas.
     */
    fun clearCacheApp(entry: InstalledAppEntry, activity: Activity?) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update {
                    it.copy(pendingPackageName = null, message = appString(R.string.app_manager_error_root_unavailable))
                }
                return@launch
            }
            val result = repository.clearCache(executor, entry.packageName)
            _state.update {
                it.copy(
                    pendingPackageName = null,
                    message = appString(
                        if (result.success) R.string.app_manager_clear_cache_success else R.string.app_manager_clear_cache_error,
                        entry.label,
                    ),
                )
            }
            if (result.success && activity != null) {
                val isMember = preferences.preferences.first().isMembershipActive
                AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /** FITUR BARU (lihat perintah rework — "tambahkan Toast di semua Fitur"): lihat KDoc appString di TweakViewModel. */
    private fun appString(resId: Int, vararg args: Any): String {
        val text = getApplication<Application>().getString(resId, *args)
        getApplication<Application>().showAetherToast(text)
        return text
    }
}
