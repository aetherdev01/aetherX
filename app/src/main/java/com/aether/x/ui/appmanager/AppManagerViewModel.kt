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

    val pendingPackageName: String? = null,
    val message: String? = null,
) {

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

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val catalog = AppManagerCatalog
    private val repository = AppManagerRepository()

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(AppManagerUiState())
    val state: StateFlow<AppManagerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
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

    fun toggleFreeze(entry: InstalledAppEntry) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor == null) {
                _state.update {
                    it.copy(
                        pendingPackageName = null,
                        message = appString(R.string.app_manager_error_root_unavailable),
                    )
                }
                return@launch
            }
            if (entry.isFrozen) {
                repository.unfreeze(executor, entry.packageName)
            } else {
                repository.freeze(executor, entry.packageName)
            }

            val actuallyFrozen = catalog.isPackageFrozen(executor, entry.packageName)
            val expectedFrozen = !entry.isFrozen
            if (actuallyFrozen != expectedFrozen) {
                _state.update {
                    it.copy(
                        message = appString(
                            if (entry.isFrozen) R.string.app_manager_error_unfreeze else R.string.app_manager_error_freeze,
                            entry.label,
                        ),
                    )
                }
            }

            val refreshedApps = catalog.loadManageableApps(getApplication(), executor)
            _state.update { it.copy(apps = refreshedApps, pendingPackageName = null) }
        }
    }

    fun forceStopApp(entry: InstalledAppEntry, activity: Activity?) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
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

    fun clearCacheApp(entry: InstalledAppEntry, activity: Activity?) {
        viewModelScope.launch {
            _state.update { it.copy(pendingPackageName = entry.packageName) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
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

    private fun appString(resId: Int, vararg args: Any): String {
        val text = getApplication<Application>().getString(resId, *args)
        getApplication<Application>().showAetherToast(text)
        return text
    }
}
