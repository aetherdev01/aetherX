package com.aether.x.ui.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.core.monitor.SystemStatsProvider
import com.aether.x.ui.components.SectionCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.aether.x.core.locale.str
import com.aether.x.R

private data class AdvancedHardwareSnapshot(
    val usedRamMb: Long? = null,
    val totalRamMb: Long? = null,
    val cpuFrequencyMhz: List<Int> = emptyList(),
    val batteryTemperatureC: Float? = null,
    val usedStorageGb: Float? = null,
    val totalStorageGb: Float? = null,
)

/** Extra live metrics are shown only when the OS exposes a real reading. */
@Composable
fun AdvancedHardwareMetricsCard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(AdvancedHardwareSnapshot()) }

    LaunchedEffect(context.applicationContext) {
        while (isActive) {
            snapshot = withContext(Dispatchers.IO) { readSnapshot(context.applicationContext) }
            delay(2_000L)
        }
    }

    SectionCard(title = str(R.string.advanced_hardware_metrics_title), watermarkIcon = Icons.Outlined.Memory) {
        val ramTotal = snapshot.totalRamMb
        val ramUsed = snapshot.usedRamMb
        if (ramTotal != null && ramUsed != null && ramTotal > 0) {
            MetricPair(
                label = str(R.string.advanced_hardware_metrics_card_ram_terpakai),
                value = "${formatGb(ramUsed)} / ${formatGb(ramTotal)} GB",
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (ramUsed.toFloat() / ramTotal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            )
        } else {
            MetricPair("RAM", str(R.string.advanced_hardware_metrics_card_tidak_tersedia))
        }

        val frequencies = snapshot.cpuFrequencyMhz
        MetricPair(
            label = str(R.string.advanced_hardware_metrics_card_frekuensi_cpu_terbaca),
            value = if (frequencies.isNotEmpty()) {
                str(R.string.advanced_hardware_metrics_card_mhz_core, frequencies.minOrNull(), frequencies.maxOrNull(), frequencies.size)
            } else str(R.string.advanced_hardware_metrics_card_akses_sysfs_dibatasi),
        )

        MetricPair(
            label = str(R.string.advanced_hardware_metrics_card_suhu_terdeteksi),
            value = snapshot.batteryTemperatureC?.let { "${(it * 10).roundToInt() / 10f} °C" } ?: str(R.string.advanced_hardware_metrics_card_tidak_tersedia),
        )

        val usedStorage = snapshot.usedStorageGb
        val totalStorage = snapshot.totalStorageGb
        MetricPair(
            label = str(R.string.advanced_hardware_metrics_card_penyimpanan_internal),
            value = if (usedStorage != null && totalStorage != null) {
                "${(usedStorage * 10).roundToInt() / 10f} / ${(totalStorage * 10).roundToInt() / 10f} GB"
            } else str(R.string.advanced_hardware_metrics_card_tidak_tersedia),
        )

        Text(
            str(R.string.advanced_hardware_metrics_sensor_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricPair(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatGb(mb: Long): String = "%.1f".format(mb / 1024.0)

private fun readSnapshot(context: Context): AdvancedHardwareSnapshot {
    val memory = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }.getOrNull()

    val frequencies = buildList {
        val cpuRoot = File("/sys/devices/system/cpu")
        val cores = cpuRoot.listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) }.orEmpty()
        cores.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }.forEach { core ->
            val raw = runCatching { File(core, "cpufreq/scaling_cur_freq").takeIf(File::canRead)?.readText()?.trim()?.toLongOrNull() }.getOrNull()
            if (raw != null && raw > 0) add((raw / 1000L).toInt())
        }
    }

    val storage = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes / (1024f * 1024f * 1024f)
        val free = stat.availableBytes / (1024f * 1024f * 1024f)
        (total - free) to total
    }.getOrNull()

    val temp = runCatching { SystemStatsProvider().readTemperatureCelsius(context) }.getOrNull()
    return AdvancedHardwareSnapshot(
        usedRamMb = memory?.let { (it.totalMem - it.availMem).coerceAtLeast(0L) / (1024L * 1024L) },
        totalRamMb = memory?.let { it.totalMem / (1024L * 1024L) },
        cpuFrequencyMhz = frequencies,
        batteryTemperatureC = temp,
        usedStorageGb = storage?.first,
        totalStorageGb = storage?.second,
    )
}
