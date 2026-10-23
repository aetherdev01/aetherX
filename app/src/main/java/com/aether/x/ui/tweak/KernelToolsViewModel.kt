package com.aether.x.ui.tweak

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aether.x.AetherXApp
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.components.showAetherToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aether.x.core.locale.str
import com.aether.x.R

data class BlockDeviceInfo(
    val name: String,
    val currentScheduler: String?,
    val availableSchedulers: List<String>,
    val readAheadKb: Int?,
)

data class KernelToolsUiState(
    val loading: Boolean = true,
    val rootAvailable: Boolean = true,
    val blockDevices: List<BlockDeviceInfo> = emptyList(),
    val tcpCurrent: String? = null,
    val tcpAvailable: List<String> = emptyList(),
    val swappiness: Int? = null,
    val vfsCachePressure: Int? = null,
    val zramSizeBytes: Long? = null,
    val zramAlgorithm: String? = null,
)

/**
 * v3.6 — Advanced -> Kernel Tools (khusus Root). Melengkapi Kernel Manager
 * (CPU/GPU) dengan tunable kernel lain yang umum dipakai: I/O scheduler +
 * read-ahead per blok, TCP congestion control, swappiness, vfs_cache_pressure,
 * dan info ZRAM.
 *
 * Semua nilai yang ditulis divalidasi terhadap daftar yang DIBACA dari kernel
 * (atau rentang angka tetap) sebelum disusun menjadi perintah shell, jadi
 * tidak ada teks bebas dari UI yang masuk ke shell.
 */
class KernelToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AetherXPreferences(application)

    private val _state = MutableStateFlow(KernelToolsUiState())
    val state: StateFlow<KernelToolsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val executor = PrivilegeManager.getExecutorAwaitingConnection()
            if (executor == null) {
                _state.update { it.copy(loading = false, rootAvailable = false) }
                return@launch
            }
            _state.value = parse(executor.exec(READ_SCRIPT).output)
        }
    }

    fun setScheduler(device: String, scheduler: String, activity: Activity? = null) {
        val dev = _state.value.blockDevices.firstOrNull { it.name == device } ?: return
        if (scheduler !in dev.availableSchedulers) return
        write("echo $scheduler > /sys/block/${dev.name}/queue/scheduler", str(R.string.kernel_tools_i_o_scheduler, dev.name), activity)
    }

    fun setReadAhead(device: String, kb: Int, activity: Activity? = null) {
        val dev = _state.value.blockDevices.firstOrNull { it.name == device } ?: return
        if (kb !in 0..8192) return
        write("echo $kb > /sys/block/${dev.name}/queue/read_ahead_kb", "Read-ahead ${dev.name}", activity)
    }

    fun setTcp(algorithm: String, activity: Activity? = null) {
        if (algorithm !in _state.value.tcpAvailable) return
        write("echo $algorithm > /proc/sys/net/ipv4/tcp_congestion_control", str(R.string.kernel_tools_tcp_congestion), activity)
    }

    fun setSwappiness(value: Int, activity: Activity? = null) {
        if (value !in 0..200) return
        write("echo $value > /proc/sys/vm/swappiness", "Swappiness", activity)
    }

    fun setVfsCachePressure(value: Int, activity: Activity? = null) {
        if (value !in 1..500) return
        write("echo $value > /proc/sys/vm/vfs_cache_pressure", "VFS cache pressure", activity)
    }

    private fun write(command: String, label: String, activity: Activity?) {
        viewModelScope.launch {
            val executor = PrivilegeManager.getExecutorAwaitingConnection() ?: return@launch
            val result = executor.exec(command)
            val app = getApplication<Application>()
            if (result.success) {
                app.showAetherToast(str(R.string.kernel_tools_diterapkan, label))
            } else {
                app.showAetherToast(str(R.string.kernel_tools_gagal_menerapkan, label))
            }
            // Baca ulang supaya UI menampilkan nilai yang BENAR-BENAR tersimpan
            // (kernel boleh menolak atau membulatkan nilai).
            _state.value = parse(executor.exec(READ_SCRIPT).output)
            if (result.success && activity != null) {
                val isMember = preferences.preferences.first().isMembershipActive
                AetherXApp.interstitialAdGate.maybeShow(activity, isMember = isMember)
            }
        }
    }

    private fun parse(lines: List<String>): KernelToolsUiState {
        var section = ""
        val blk = mutableListOf<BlockDeviceInfo>()
        val tcp = mutableListOf<String>()
        val vm = mutableListOf<String>()
        val zram = mutableListOf<String>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("##")) {
                section = line
                continue
            }
            if (line.isEmpty()) continue
            when (section) {
                "##BLK" -> {
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val schedText = parts[1]
                        val current = Regex("\\[([^\\]]+)]").find(schedText)?.groupValues?.get(1)
                        val available = schedText.replace("[", "").replace("]", "")
                            .split(Regex("\\s+")).filter { it.isNotBlank() }
                        blk += BlockDeviceInfo(
                            name = parts[0],
                            currentScheduler = current,
                            availableSchedulers = available,
                            readAheadKb = parts.getOrNull(2)?.trim()?.toIntOrNull(),
                        )
                    }
                }
                "##TCP" -> tcp += line
                "##VM" -> vm += line
                "##ZRAM" -> zram += line
            }
        }

        val algo = zram.getOrNull(1)?.let { text ->
            Regex("\\[([^\\]]+)]").find(text)?.groupValues?.get(1) ?: text
        }
        return KernelToolsUiState(
            loading = false,
            rootAvailable = true,
            blockDevices = blk,
            tcpCurrent = tcp.getOrNull(0),
            tcpAvailable = tcp.getOrNull(1)?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty(),
            swappiness = vm.getOrNull(0)?.toIntOrNull(),
            vfsCachePressure = vm.getOrNull(1)?.toIntOrNull(),
            zramSizeBytes = zram.getOrNull(0)?.toLongOrNull()?.takeIf { it > 0L },
            zramAlgorithm = algo,
        )
    }

    private companion object {
        // '@' diganti jadi '$' supaya skrip shell tidak perlu escape di string Kotlin.
        val READ_SCRIPT: String = """
            echo '##BLK'
            for d in /sys/block/*; do
              n=@(basename @d)
              case @n in loop*|ram*|zram*|dm-*) continue;; esac
              if [ -r @d/queue/scheduler ]; then
                echo "@n|@(cat @d/queue/scheduler)|@(cat @d/queue/read_ahead_kb 2>/dev/null)"
              fi
            done
            echo '##TCP'
            cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null
            cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null
            echo '##VM'
            cat /proc/sys/vm/swappiness 2>/dev/null
            cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null
            echo '##ZRAM'
            cat /sys/block/zram0/disksize 2>/dev/null
            cat /sys/block/zram0/comp_algorithm 2>/dev/null
        """.trimIndent().replace('@', '$')
    }
}
