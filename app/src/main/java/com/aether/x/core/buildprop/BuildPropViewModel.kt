package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.R
import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropEntry
import com.aether.x.core.buildprop.BuildPropPartition
import com.aether.x.core.buildprop.BuildPropReader
import com.aether.x.core.buildprop.BuildPropSnapshot
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.BuildPropRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingBuildPropEdit(
    val partition: BuildPropPartition,
    val entry: BuildPropEntry,
    val newValue: String,
)

data class BuildPropUiState(
    val loading: Boolean = true,
    val snapshots: List<BuildPropSnapshot> = emptyList(),
    val selectedPartition: BuildPropPartition = BuildPropPartition.SYSTEM,
    val searchQuery: String = "",
    val pendingEdit: PendingBuildPropEdit? = null,
    val backedUpThisSession: Set<BuildPropPartition> = emptySet(),
    val backupsForSelectedPartition: List<BuildPropBackup> = emptyList(),
    val pendingRestore: BuildPropBackup? = null,
    val message: String? = null,
) {

    val visibleEntries: List<BuildPropEntry>
        get() {
            val snapshot = snapshots.firstOrNull { it.partition == selectedPartition } ?: return emptyList()
            if (searchQuery.isBlank()) return snapshot.entries
            return snapshot.entries.filter { it.key.contains(searchQuery, ignoreCase = true) }
        }
}

class BuildPropViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = BuildPropReader()
    private val repository = BuildPropRepository()

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(BuildPropUiState())
    val state: StateFlow<BuildPropUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(loading = false, message = appString(R.string.buildprop_error_root_unavailable)) }
                return@launch
            }
            val snapshots = reader.readAll(executor)
            _state.update { it.copy(loading = false, snapshots = snapshots) }
            refreshBackupList()
        }
    }

    fun selectPartition(partition: BuildPropPartition) {
        _state.update { it.copy(selectedPartition = partition, searchQuery = "") }
        refreshBackupList()
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun requestEdit(entry: BuildPropEntry, newValue: String) {
        if (newValue == entry.value) return
        _state.update {
            it.copy(pendingEdit = PendingBuildPropEdit(it.selectedPartition, entry, newValue))
        }
    }

    fun cancelPendingEdit() {
        _state.update { it.copy(pendingEdit = null) }
    }

    fun confirmEdit(activity: Activity?) {
        val pending = _state.value.pendingEdit ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(pendingEdit = null, message = appString(R.string.buildprop_error_root_unavailable)) }
                return@launch
            }

            if (pending.partition !in _state.value.backedUpThisSession) {
                val backupResult = repository.backup(executor, pending.partition)
                if (backupResult.isFailure) {
                    _state.update {
                        it.copy(
                            pendingEdit = null,
                            message = appString(R.string.buildprop_error_backup_failed, pending.partition.displayLabel),
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(backedUpThisSession = it.backedUpThisSession + pending.partition) }
            }

            val result = repository.applyEntry(
                executor = executor,
                partition = pending.partition,
                lineIndex = pending.entry.lineIndex,
                key = pending.entry.key,
                newValue = pending.newValue,
            )
            _state.update {
                it.copy(
                    pendingEdit = null,
                    message = if (result.success) {
                        appString(R.string.buildprop_success_applied, pending.entry.key)
                    } else {
                        appString(R.string.buildprop_error_apply_failed, pending.entry.key)
                    },
                )
            }
            if (result.success && activity != null) {
                val isMember = preferences.preferences.first().isMembershipActive
                AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
            }
            refresh()
        }
    }

    private fun refreshBackupList() {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor() ?: return@launch
            val backups = repository.listBackups(executor, _state.value.selectedPartition)
            _state.update { it.copy(backupsForSelectedPartition = backups) }
        }
    }

    fun requestRestore(backup: BuildPropBackup) {
        _state.update { it.copy(pendingRestore = backup) }
    }

    fun cancelPendingRestore() {
        _state.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val backup = _state.value.pendingRestore ?: return
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutor()
            if (executor == null) {
                _state.update { it.copy(pendingRestore = null, message = appString(R.string.buildprop_error_root_unavailable)) }
                return@launch
            }
            val result = repository.restore(executor, backup)
            _state.update {
                it.copy(
                    pendingRestore = null,
                    message = if (result.success) {
                        appString(R.string.buildprop_success_restored, backup.partition.displayLabel)
                    } else {
                        appString(R.string.buildprop_error_restore_failed, backup.partition.displayLabel)
                    },
                )
            }
            refresh()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun appString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
