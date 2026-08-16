package com.aether.x.core.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aether.x.core.security.SecretStrings
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

class SystemStatsProvider {

    private var lastCpuTotal: Long = -1
    private var lastCpuIdle: Long = -1

    fun readCpuLoadPercent(): Int? = runCatching {
        RandomAccessFile("/proc/stat", "r").use { reader ->
            val line = reader.readLine() ?: return null

            val parts = line.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0] != "cpu") return null
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return null

            val idle = values[3] + (values.getOrNull(4) ?: 0L)
            val total = values.sum()

            if (lastCpuTotal < 0) {
                lastCpuTotal = total
                lastCpuIdle = idle
                return null
            }

            val totalDelta = total - lastCpuTotal
            val idleDelta = idle - lastCpuIdle
            lastCpuTotal = total
            lastCpuIdle = idle

            if (totalDelta <= 0) return null
            val busyPercent = ((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f
            busyPercent.roundToInt().coerceIn(0, 100)
        }
    }.getOrNull()

    fun readTemperatureCelsius(context: Context): Float? {
        val fromBattery = runCatching {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            val tenthsOfCelsius = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tenthsOfCelsius > 0) tenthsOfCelsius / 10f else null
        }.getOrNull()

        return fromBattery ?: readThermalZoneCelsius()
    }

    private fun readThermalZoneCelsius(): Float? = runCatching {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null

        for (zone in zones.sortedBy { it.name }) {
            val tempFile = File(zone, "temp")
            if (!tempFile.canRead()) continue
            val raw = tempFile.readText().trim().toLongOrNull() ?: continue

            val celsius = if (raw > 1000) raw / 1000f else raw.toFloat()
            if (celsius in 10f..100f) return celsius
        }
        null
    }.getOrNull()

    // Sama seperti RootSystemMonitor.gpuLoadPaths — disimpan terenkripsi
    // (lihat SecretStrings.kt) supaya path deteksi ini tidak muncul
    // sebagai `const-string` polos di classes.dex. Payload digenerate
    // lewat tools/encode_secret.py.
    private val candidatePaths: List<String> by lazy {
        SecretStrings.revealList(
            "I6j+eUjxdOTCahE7R9ln0GBdnCO5P5p+/QWoPkDCqRROGHD9tqwty1AD3HCg7lHRVrltLtGoststLh5fofXW3YUltMXIYl+I",
            "iPwScFxrn3MGvqYiYZpzqZbGwHcrZJI3oVkQ/MPUXkwaPZ/tUIU8zMA5BDQfkd2phDRYUg==",
            "mm9NUroIYp0Pg2YQSTSGNp1reds/dbThExcV4FEHF3kbn8BWdrW879Kdty4By+uUy7QGb8WlvF2dyLswaKCr",
        )
    }

    fun readGpuLoadPercent(): Int? = runCatching {
        for (path in candidatePaths) {
            val file = File(path)
            if (!file.canRead()) continue
            val text = file.readText().trim()

            val numeric = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: continue
            return numeric.coerceIn(0, 100)
        }
        null
    }.getOrNull()
}
