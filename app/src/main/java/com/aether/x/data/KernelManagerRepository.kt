package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

/**
 * Menulis (apply) nilai kernel mentah per-core CPU dan GPU langsung ke
 * sysfs — pasangan TULIS untuk [com.aether.x.core.kernel.KernelInfoReader]
 * yang murni BACA. Dipakai oleh section "Kernel Manager" (khusus backend
 * Root, lihat gating di TweakScreen).
 *
 * BEDA DENGAN [TweakRepository.applyCpuGovernor]/[TweakRepository.applyGpuPerformanceMode]:
 * fungsi-fungsi di [TweakRepository] menerapkan satu governor/mode yang
 * SAMA ke semua core/ke GPU sekaligus, dengan governor dipilih dari daftar
 * terbatas ([CpuGovernor]). Fungsi di kelas ini mengambil governor/frekuensi
 * sebagai STRING/INT MENTAH per-core, memungkinkan kontrol granular yang
 * kernel manager biasa punya — TIDAK menggantikan section "Root" yang sudah
 * ada, keduanya independen dan boleh dipakai bersamaan (yang terakhir
 * ditulis ke sysfs yang menang, seperti biasa sysfs bekerja).
 *
 * TIDAK ADA VALIDASI nilai terhadap [com.aether.x.core.kernel.CpuCoreInfo.availableFrequenciesKhz]/
 * [com.aether.x.core.kernel.CpuCoreInfo.availableGovernors] di level repository ini — itu tanggung
 * jawab UI/ViewModel pemanggil (lihat KernelManagerViewModel) memastikan
 * hanya mengirim nilai yang memang muncul di daftar "available" hasil baca
 * sebelumnya. Kernel sendiri akan menolak diam-diam (gagal tulis ke sysfs)
 * kalau nilai tidak valid, jadi tidak ada risiko crash — hanya risiko
 * penulisan tidak berefek.
 */
class KernelManagerRepository {

    /**
     * Set frekuensi min/max satu core spesifik. [minKhz]/[maxKhz] null
     * berarti field itu TIDAK diubah (dibiarkan seperti sebelumnya) —
     * berguna kalau UI hanya mau mengubah salah satu (mis. cuma slider max
     * yang digeser, min tetap).
     */
    suspend fun setCoreFrequency(
        executor: ShellExecutor,
        coreIndex: Int,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val base = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq"
        val commands = buildList {
            // max ditulis DULU kalau turun (mis. max baru < min lama) supaya
            // tidak sempat ada state min > max yang ditolak kernel;
            // sebaliknya min ditulis dulu kalau naik. Urutan aman generik:
            // tulis max dulu, baru min — kernel menolak diam-diam kombinasi
            // invalid, jadi urutan ini hanya optimisasi "kemungkinan berhasil
            // lebih tinggi", bukan penjamin mutlak untuk semua kasus.
            if (maxKhz != null) add("echo $maxKhz > $base/scaling_max_freq 2>/dev/null")
            if (minKhz != null) add("echo $minKhz > $base/scaling_min_freq 2>/dev/null")
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    /** Set governor satu core spesifik ke [governorName] MENTAH (harus salah satu dari [com.aether.x.core.kernel.CpuCoreInfo.availableGovernors]). */
    suspend fun setCoreGovernor(executor: ShellExecutor, coreIndex: Int, governorName: String): ShellResult {
        val base = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq"
        return executor.exec("echo $governorName > $base/scaling_governor 2>/dev/null")
    }

    /**
     * Terapkan frekuensi min/max ke SEMUA core sekaligus — dipakai tombol
     * "Terapkan ke semua core" opsional di UI, bukan jalur utama (jalur
     * utama tetap per-core lewat [setCoreFrequency]).
     */
    suspend fun setAllCoresFrequency(
        executor: ShellExecutor,
        coreCount: Int,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val commands = (0 until coreCount).flatMap { i ->
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
            buildList {
                if (maxKhz != null) add("echo $maxKhz > $base/scaling_max_freq 2>/dev/null")
                if (minKhz != null) add("echo $minKhz > $base/scaling_min_freq 2>/dev/null")
            }
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    /**
     * Set frekuensi min/max GPU. [devfreqPath] WAJIB nilai dari
     * [com.aether.x.core.kernel.GpuInfo.devfreqPath] hasil baca sebelumnya
     * (jalur sysfs GPU berbeda-beda per chipset — lihat KDoc
     * [com.aether.x.core.kernel.KernelInfoReader.readGpuInfo]), BUKAN
     * ditebak/hardcode di sini.
     */
    suspend fun setGpuFrequency(
        executor: ShellExecutor,
        devfreqPath: String,
        minKhz: Int?,
        maxKhz: Int?,
    ): ShellResult {
        val commands = buildList {
            if (maxKhz != null) add("echo $maxKhz > $devfreqPath/max_freq 2>/dev/null")
            if (minKhz != null) add("echo $minKhz > $devfreqPath/min_freq 2>/dev/null")
        }
        if (commands.isEmpty()) return ShellResult(success = true)
        return executor.exec(commands.joinToString("; "))
    }

    /** Set governor GPU ke [governorName] MENTAH (harus dari [com.aether.x.core.kernel.GpuInfo.availableGovernors]). */
    suspend fun setGpuGovernor(executor: ShellExecutor, devfreqPath: String, governorName: String): ShellResult {
        return executor.exec("echo $governorName > $devfreqPath/governor 2>/dev/null")
    }
}
