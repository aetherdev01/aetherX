package com.aether.x.ui.dashboard

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.R
import com.aether.x.core.monitor.RamMonitor
import com.aether.x.core.monitor.RamSnapshot
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class RamCleanerUiState(
    /** false kalau modul native rammonitor gagal dimuat — UI sebaiknya
     * sembunyikan kartu ini sepenuhnya alih-alih tampil kosong/error. */
    val available: Boolean = true,
    val snapshot: RamSnapshot? = null,
    val boosting: Boolean = false,
    val lastFreedLabel: String? = null,
)

/**
 * v3.5 — fitur RAM Cleaner baru, dibangun di atas modul native
 * rammonitor (lihat rammonitor.h/.cpp/_jni.cpp + RamMonitor.kt untuk
 * pembacaan angkanya). ViewModel ini murni orkestrasi aksi "Bersihkan
 * RAM" di sisi Kotlin — modul native HANYA baca angka, tidak melakukan
 * aksi apapun (lihat KDoc rammonitor.h).
 */
class RamCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RamCleanerUiState())
    val state: StateFlow<RamCleanerUiState> = _state.asStateFlow()

    init {
        if (!RamMonitor.isNativeAvailable) {
            _state.update { it.copy(available = false) }
        } else {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) { RamMonitor.readSnapshot() }
            _state.update { it.copy(snapshot = snapshot, available = snapshot != null) }
        }
    }

    /**
     * "Bersihkan RAM" menggabungkan DUA jalur sekaligus:
     *
     * 1. [killBackgroundThirdPartyApps] — killBackgroundProcesses per app
     *    pihak ketiga (non-system, bukan diri sendiri sendiri). TIDAK
     *    butuh root, cukup izin KILL_BACKGROUND_PROCESSES (normal-level,
     *    auto-granted, lihat AndroidManifest.xml). Efeknya SENGAJA
     *    dikomunikasikan apa adanya di KDoc ini — Android hanya benar-benar
     *    mengizinkan mematikan proses yang SUDAH berstatus cached/
     *    background (oom_adj tinggi); proses foreground/bound-service
     *    tidak akan mati lewat API ini, dan OS sendiri sudah cukup
     *    agresif mengelola ini setiap saat. Jalur ini nyaris tidak
     *    berefek terukur di banyak device modern — dipertahankan karena
     *    tidak ada ruginya (aman, gratis, tidak butuh root) dan tetap
     *    membantu di device yang oom management-nya kurang agresif.
     * 2. `sync; echo 1 > /proc/sys/vm/drop_caches` lewat root (KALAU
     *    granted) — INI jalur yang efeknya nyata & terukur: reclaim page
     *    cache/dentries/inodes kernel, operasi Linux standar yang aman
     *    (tidak menghapus data apapun, cache dibangun ulang otomatis
     *    saat dibutuhkan lagi). Kalau root tidak granted, jalur ini
     *    dilewati diam-diam — jalur 1 tetap jalan sendirian.
     */
    fun boostRam() {
        viewModelScope.launch {
            _state.update { it.copy(boosting = true) }
            val before = _state.value.snapshot
                ?: withContext(Dispatchers.Default) { RamMonitor.readSnapshot() }

            withContext(Dispatchers.Default) { killBackgroundThirdPartyApps() }

            val executor = PrivilegeManager.getExecutor()
            if (executor != null) {
                runCatching { executor.exec("sync; echo 1 > /proc/sys/vm/drop_caches 2>/dev/null") }
            }

            // Jeda kecil supaya OS sempat benar-benar reclaim memory
            // sebelum snapshot "sesudah" dibaca — drop_caches dan kill
            // proses tidak instan-sinkron di semua device/kernel.
            delay(400)

            val after = withContext(Dispatchers.Default) { RamMonitor.readSnapshot() }
            val freedKb = if (before?.availableKb != null && after?.availableKb != null) {
                (after.availableKb - before.availableKb).coerceAtLeast(0f)
            } else {
                null
            }

            val app = getApplication<Application>()
            val freedLabel = freedKb?.takeIf { it > 0f }?.let { formatKb(it) }
            val message = if (freedLabel != null) {
                app.getString(R.string.ram_cleaner_freed_format, freedLabel)
            } else {
                app.getString(R.string.ram_cleaner_done_no_change)
            }

            _state.update {
                it.copy(
                    boosting = false,
                    snapshot = after ?: it.snapshot,
                    lastFreedLabel = freedLabel,
                )
            }
            app.showAetherToast(message)
        }
    }

    private fun killBackgroundThirdPartyApps() {
        val context = getApplication<Application>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val pm = context.packageManager
        val ownPackage = context.packageName

        @Suppress("DEPRECATION")
        val apps = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        apps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ownPackage }
            .forEach { app -> runCatching { am.killBackgroundProcesses(app.packageName) } }
    }

    private fun formatKb(kb: Float): String {
        val mb = kb / 1024f
        return if (mb >= 1024f) {
            String.format(Locale.US, "%.1f GB", mb / 1024f)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
