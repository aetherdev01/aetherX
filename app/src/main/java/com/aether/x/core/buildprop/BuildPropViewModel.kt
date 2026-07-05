package com.aether.x.ui.tweak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropEntry
import com.aether.x.core.buildprop.BuildPropPartition
import com.aether.x.core.buildprop.BuildPropReader
import com.aether.x.core.buildprop.BuildPropSnapshot
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.BuildPropRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Permintaan edit yang sedang menunggu konfirmasi pengguna lewat dialog —
 * TIDAK langsung diterapkan begitu pengguna menekan simpan pada satu baris.
 * Lihat KDoc [BuildPropViewModel] soal kenapa konfirmasi ini wajib, tidak
 * bisa dilewati walau [BuildPropUiState.pendingEdit] terlihat seperti
 * langkah tambahan yang bisa disederhanakan.
 */
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
    /** Entri partisi terpilih, difilter [searchQuery] terhadap key (case-insensitive) — dipakai langsung oleh UI, tidak perlu logika filter terpisah di Composable. */
    val visibleEntries: List<BuildPropEntry>
        get() {
            val snapshot = snapshots.firstOrNull { it.partition == selectedPartition } ?: return emptyList()
            if (searchQuery.isBlank()) return snapshot.entries
            return snapshot.entries.filter { it.key.contains(searchQuery, ignoreCase = true) }
        }
}

/**
 * ViewModel untuk "Build.prop Editor" (khusus backend Root, sub-tab baru di
 * drawer Tweak — lihat gating `PrivilegeBackend.ROOT` yang sama dengan
 * Kernel Manager/App Manager di TweakScreen).
 *
 * KENAPA ALUR EDIT PUNYA DUA LANGKAH (pilih value baru lalu KONFIRMASI
 * TERPISAH lewat [pendingEdit], bukan langsung tulis saat pengguna menekan
 * "simpan" di baris): berbeda dari toggle switch tweak biasa yang aman
 * dibalik kapan saja, mengedit build.prop BISA menyebabkan bootloop kalau
 * value salah untuk key tertentu (terutama namespace `ro.*` yang dibaca
 * SEKALI saat boot dan tidak bisa diubah lagi sampai reboot berikutnya).
 * Dialog konfirmasi (dirender di [com.aether.x.ui.tweak.BuildPropEditorSection])
 * menjelaskan risiko ini secara eksplisit setiap kali, TIDAK ada opsi
 * "jangan tampilkan lagi" — pengulangan peringatan ini disengaja, bukan
 * oversight.
 *
 * BACKUP WAJIB SEBELUM EDIT PERTAMA PER PARTISI PER SESI: [backedUpThisSession]
 * melacak partisi mana yang sudah dibackup sejak ViewModel ini dibuat (mis.
 * sejak layar Build.prop Editor dibuka) — [confirmEdit] MENOLAK menerapkan
 * perubahan (mengembalikan awal ke alur backup, bukan diam-diam melewati)
 * kalau partisi terkait belum pernah dibackup di sesi ini. Ini ditegakkan
 * DI VIEWMODEL (bukan cuma di UI) supaya tidak ada jalur pemanggilan yang
 * bisa melewati backup, termasuk kalau composable dipanggil ulang dengan
 * urutan tidak terduga.
 */
class BuildPropViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = BuildPropReader()
    private val repository = BuildPropRepository()

    private val _state = MutableStateFlow(BuildPropUiState())
    val state: StateFlow<BuildPropUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Baca ulang semua partisi. Dipanggil saat pertama dibuka, lewat tombol refresh manual, dan otomatis setelah apply/restore berhasil (menampilkan nilai yang BENAR-BENAR tersimpan di file). */
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

    /**
     * Langkah 1: pengguna menekan "simpan" pada satu baris di UI. INI BELUM
     * MENULIS APAPUN — hanya menyimpan niat ke [BuildPropUiState.pendingEdit]
     * supaya UI menampilkan dialog konfirmasi. Penulisan sungguhan ada di
     * [confirmEdit], dipanggil terpisah setelah pengguna menekan "Ya, saya
     * paham risikonya" di dialog itu.
     */
    fun requestEdit(entry: BuildPropEntry, newValue: String) {
        if (newValue == entry.value) return // tidak ada perubahan, tidak perlu dialog konfirmasi maupun backup
        _state.update {
            it.copy(pendingEdit = PendingBuildPropEdit(it.selectedPartition, entry, newValue))
        }
    }

    fun cancelPendingEdit() {
        _state.update { it.copy(pendingEdit = null) }
    }

    /**
     * Langkah 2: dipanggil hanya dari tombol konfirmasi di dialog. Kalau
     * partisi terkait belum dibackup di sesi ini, fungsi ini membuat backup
     * DULU secara otomatis sebelum menulis perubahan — pengguna tidak perlu
     * langkah manual terpisah untuk "backup dulu baru edit", tapi backup
     * tetap selalu terjadi tanpa bisa dilewati.
     */
    fun confirmEdit() {
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
                    return@launch // TIDAK lanjut menulis kalau backup gagal — ini syarat keras, bukan best-effort
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
            refresh() // baca ulang supaya UI menampilkan isi file yang sebenarnya, termasuk kalau sed gagal sebagian
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

    /** Pulihkan file dari backup yang dipilih pengguna di dialog konfirmasi restore — overwrite penuh, lihat KDoc [BuildPropRepository.restore]. */
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
