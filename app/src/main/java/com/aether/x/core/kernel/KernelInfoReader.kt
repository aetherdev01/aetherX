package com.aether.x.core.kernel

import com.aether.x.core.shell.ShellExecutor

/**
 * Membaca (read-only) state kernel mentah langsung dari sysfs/procfs lewat
 * [ShellExecutor] — dipakai oleh section "Kernel Manager" (khusus backend
 * Root, lihat gating di TweakScreen) untuk menampilkan nilai NYATA
 * perangkat, bukan asumsi tetap. Semua fungsi di kelas ini murni MEMBACA,
 * tidak pernah menulis — penulisan ada di
 * [com.aether.x.data.KernelManagerRepository], mengikuti pemisahan yang
 * sama seperti [com.aether.x.data.TweakRepository] (repository menulis)
 * vs sumber data lain yang membaca.
 *
 * SATU PANGGILAN SHELL PER FUNGSI BACA: tiap fungsi `cat` semua file sysfs
 * yang relevan dalam SATU perintah shell (dipisah marker unik per file),
 * bukan satu `exec()` per file — [ShellExecutor.exec] tiap panggilan punya
 * overhead proses (`su -c` baru), jadi membaca 8 core CPU misalnya akan
 * 8x lebih lambat kalau dipanggil satu-satu, apalagi dipoll berkala untuk
 * thermal (lihat [com.aether.x.ui.tweak.KernelManagerViewModel]).
 */
class KernelInfoReader {

    /**
     * Baca info seluruh core CPU yang terdeteksi. Core yang gagal dibaca
     * total (mis. sedang di-offline-kan sistem) tetap muncul di hasil
     * dengan [CpuCoreInfo.isUnavailable] true, supaya UI bisa menampilkan
     * "Core N: tidak tersedia" alih-alih diam-diam menghilangkannya dari
     * daftar (yang akan membingungkan kalau jumlah core terlihat berubah).
     */
    suspend fun readCpuCores(executor: ShellExecutor): List<CpuCoreInfo> {
        // Deteksi dulu berapa banyak core ada (indeks 0..N-1) dari daftar
        // direktori cpufreq yang benar-benar ada — TIDAK diasumsikan tetap
        // (mis. 8), karena jumlah core beda-beda tiap chipset.
        val countResult = executor.exec(
            "ls -d /sys/devices/system/cpu/cpu[0-9]*/cpufreq 2>/dev/null | wc -l",
        )
        val coreCount = countResult.outputText.trim().toIntOrNull() ?: 0
        if (coreCount <= 0) return emptyList()

        val script = buildString {
            for (i in 0 until coreCount) {
                val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
                appendLine("echo ===CORE_$i===")
                appendLine("cat $base/scaling_cur_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_min_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_max_freq 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_available_frequencies 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_governor 2>/dev/null")
                appendLine("echo ---")
                appendLine("cat $base/scaling_available_governors 2>/dev/null")
            }
        }
        val result = executor.exec(script)
        return parseCoreBlocks(result.output, coreCount)
    }

    private fun parseCoreBlocks(lines: List<String>, coreCount: Int): List<CpuCoreInfo> {
        // Setiap blok core dipisah marker "===CORE_i===", field di dalamnya
        // dipisah "---" — parsing sederhana berbasis pemisah teks ini, tidak
        // perlu regex karena formatnya kita kontrol sendiri dari script di atas.
        val cores = mutableListOf<CpuCoreInfo>()
        var currentIndex = -1
        var fields = mutableListOf<MutableList<String>>()

        fun flush() {
            if (currentIndex < 0) return
            // fields[0]=curFreq, [1]=minFreq, [2]=maxFreq, [3]=availFreq, [4]=governor, [5]=availGovernors
            val curFreq = fields.getOrNull(0)?.firstOrNull()?.trim()?.toIntOrNull()
            val minFreq = fields.getOrNull(1)?.firstOrNull()?.trim()?.toIntOrNull()
            val maxFreq = fields.getOrNull(2)?.firstOrNull()?.trim()?.toIntOrNull()
            val availFreq = fields.getOrNull(3)
                ?.joinToString(" ")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
                .distinct()
                .sorted()
            val governor = fields.getOrNull(4)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val availGovernors = fields.getOrNull(5)
                ?.joinToString(" ")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .distinct()

            cores += CpuCoreInfo(
                coreIndex = currentIndex,
                currentFreqKhz = curFreq,
                minFreqKhz = minFreq,
                maxFreqKhz = maxFreq,
                availableFrequenciesKhz = availFreq,
                currentGovernor = governor,
                availableGovernors = availGovernors,
            )
        }

        var fieldIndex = 0
        for (raw in lines) {
            val markerMatch = Regex("^===CORE_(\\d+)===$").find(raw.trim())
            if (markerMatch != null) {
                flush()
                currentIndex = markerMatch.groupValues[1].toInt()
                fields = MutableList(6) { mutableListOf() }
                fieldIndex = 0
                continue
            }
            if (raw.trim() == "---") {
                fieldIndex = (fieldIndex + 1).coerceAtMost(5)
                continue
            }
            if (currentIndex >= 0 && fieldIndex < fields.size) {
                fields[fieldIndex].add(raw)
            }
        }
        flush()

        // Jaga-jaga: kalau parsing gagal total untuk sebagian core (mis. output
        // shell tidak lengkap), tetap kembalikan entri kosong untuk core itu
        // supaya jumlah baris di UI konsisten dengan coreCount yang terdeteksi.
        val byIndex = cores.associateBy { it.coreIndex }
        return (0 until coreCount).map { i ->
            byIndex[i] ?: CpuCoreInfo(
                coreIndex = i,
                currentFreqKhz = null,
                minFreqKhz = null,
                maxFreqKhz = null,
                availableFrequenciesKhz = emptyList(),
                currentGovernor = null,
                availableGovernors = emptyList(),
            )
        }
    }

    /**
     * Baca info GPU. Mencoba beberapa jalur sysfs dikenal secara berurutan
     * (Adreno/Qualcomm -> Mali devfreq generik -> Mali via node misc),
     * mengikuti chipset yang sama dengan yang sudah ditangani
     * [com.aether.x.data.TweakRepository.applyGpuPerformanceMode]. Jalur
     * GED MediaTek (frequency bound, bukan devfreq governor) SENGAJA tidak
     * dibaca di sini karena tidak punya konsep "governor" sama sekali —
     * GPU Performance Mode toggle yang sudah ada tetap menanganinya lewat
     * jalurnya sendiri, terpisah dari kernel manager baca/tulis mentah ini.
     */
    suspend fun readGpuInfo(executor: ShellExecutor): GpuInfo {
        val script = """
            for p in /sys/class/kgsl/kgsl-3d0/devfreq /sys/devices/platform/*/kgsl-3d0/devfreq \
                     /sys/class/devfreq/*mali* /sys/devices/platform/*/devfreq/*mali*; do
              if [ -d "$p" ]; then
                echo "===PATH===${'$'}p"
                cat "${'$'}p/cur_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/min_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/max_freq" 2>/dev/null
                echo ---
                cat "${'$'}p/available_frequencies" 2>/dev/null
                echo ---
                cat "${'$'}p/governor" 2>/dev/null
                echo ---
                cat "${'$'}p/available_governors" 2>/dev/null
                break
              fi
            done
        """.trimIndent()
        val result = executor.exec(script)
        return parseGpuBlock(result.output)
    }

    private fun parseGpuBlock(lines: List<String>): GpuInfo {
        if (lines.isEmpty() || !lines.first().startsWith("===PATH===")) {
            return GpuInfo(null, null, null, null, emptyList(), null, emptyList())
        }
        val path = lines.first().removePrefix("===PATH===").trim()
        val fields = MutableList(6) { mutableListOf<String>() }
        var fieldIndex = 0
        for (raw in lines.drop(1)) {
            if (raw.trim() == "---") {
                fieldIndex = (fieldIndex + 1).coerceAtMost(5)
                continue
            }
            if (fieldIndex < fields.size) fields[fieldIndex].add(raw)
        }
        val curFreq = fields[0].firstOrNull()?.trim()?.toIntOrNull()
        val minFreq = fields[1].firstOrNull()?.trim()?.toIntOrNull()
        val maxFreq = fields[2].firstOrNull()?.trim()?.toIntOrNull()
        val availFreq = fields[3].joinToString(" ").trim()
            .split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.distinct().sorted()
        val governor = fields[4].firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val availGovernors = fields[5].joinToString(" ").trim()
            .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

        return GpuInfo(
            devfreqPath = path.takeIf { it.isNotBlank() },
            currentFreqKhz = curFreq,
            minFreqKhz = minFreq,
            maxFreqKhz = maxFreq,
            availableFrequenciesKhz = availFreq,
            currentGovernor = governor,
            availableGovernors = availGovernors,
        )
    }

    /**
     * Baca suhu semua zona termal (`/sys/class/thermal/thermal_zone*`).
     * Nilai mentah kernel dalam MILIDERAJAT Celsius (mis. 45000 = 45.0°C)
     * di HAMPIR SEMUA perangkat modern — dibagi 1000 di sini. Beberapa
     * perangkat lawas kadang melaporkan langsung dalam derajat (nilai < 1000),
     * itu ditangani sebagai kasus khusus supaya tidak menampilkan "0.045°C".
     */
    suspend fun readThermalZones(executor: ShellExecutor): List<ThermalZoneInfo> {
        val script = """
            for z in /sys/class/thermal/thermal_zone*; do
              idx=${'$'}(basename "${'$'}z" | tr -dc '0-9')
              type=${'$'}(cat "${'$'}z/type" 2>/dev/null)
              temp=${'$'}(cat "${'$'}z/temp" 2>/dev/null)
              echo "===ZONE_${'$'}{idx}===${'$'}{type}===${'$'}{temp}"
            done
        """.trimIndent()
        val result = executor.exec(script)
        return result.output.mapNotNull { line ->
            val match = Regex("^===ZONE_(\\d+)===(.*)===(-?\\d+)$").find(line.trim()) ?: return@mapNotNull null
            val (idxStr, type, tempStr) = match.destructured
            val rawTemp = tempStr.toIntOrNull() ?: return@mapNotNull null
            // Kasus khusus: sebagian kecil perangkat/driver melaporkan sudah
            // dalam derajat (bukan miliderajat) — nilai mentah realistis untuk
            // suhu perangkat (0-150) hampir tidak pernah terjadi dalam skala
            // miliderajat, jadi dipakai sebagai heuristik pembeda.
            val celsius = if (rawTemp in 0..150) rawTemp.toFloat() else rawTemp / 1000f
            ThermalZoneInfo(zoneIndex = idxStr.toInt(), type = type.trim(), temperatureCelsius = celsius)
        }.sortedBy { it.zoneIndex }
    }

    /** Versi kernel dari `uname -r` — murni informasi, tidak dipakai logika apa pun. */
    suspend fun readKernelVersion(executor: ShellExecutor): String? {
        val result = executor.exec("uname -r")
        return result.output.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun readSnapshot(executor: ShellExecutor): KernelSnapshot = KernelSnapshot(
        cpuCores = readCpuCores(executor),
        gpu = readGpuInfo(executor),
        thermalZones = readThermalZones(executor),
        kernelVersion = readKernelVersion(executor),
    )
}
