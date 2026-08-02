package com.aether.x.core.monitor

import android.util.Log
import com.aether.x.core.shell.RootShellExecutor

/**
 * RootSystemMonitor — jembatan tipis ke modul native sysmonitor (lihat
 * app/src/main/cpp/sysmonitor.h/.cpp/sysmonitor_jni.cpp) yang membaca
 * CPU load per-core (/proc/stat) dan GPU load/frekuensi (sysfs
 * kgsl/devfreq) langsung dari native, tanpa alokasi File/String
 * berulang di Kotlin — dibuat khusus supaya sampling cepat (200-500ms)
 * untuk grafik real-time tidak membebani GC.
 *
 * ROOT-ONLY BY DESIGN: object ini SENGAJA tidak melakukan pengecekan
 * root sendiri — pemanggil (RootSystemMonitorViewModel) WAJIB hanya
 * memulai polling saat
 * `PrivilegeManager.status.value.activeBackend == PrivilegeBackend.ROOT`.
 * Alasan gating ada di sisi Kotlin, bukan native, dijelaskan lengkap di
 * KDoc sysmonitor.h (intinya: sebagian path sysfs yang dibaca modul ini
 * kebetulan world-readable di sebagian device tanpa root, tapi supaya
 * perilaku fitur ini konsisten di SEMUA device — bukan tersedia di
 * sebagian device non-root dan tidak di device lain — fitur ini dikunci
 * root-only murni dari sisi kebijakan UI, bukan dari kemampuan teknis
 * baca filenya).
 *
 * Native lib yang dimuat sama dengan SignatureGuard/NativeIntegrityGuard/
 * AdBlockDetector/DeviceFingerprint (satu libaetherX.so, lihat KDoc
 * CMakeLists.txt) — `System.loadLibrary` di `init` ini aman dipanggil
 * berkali-kali kalau class lain juga sudah memuatnya lebih dulu (JVM
 * hanya memuat native lib sekali per proses).
 */
object RootSystemMonitor {

    private const val TAG = "RootSystemMonitor"

    /** true kalau libaetherX.so berhasil dimuat DAN RegisterNatives untuk class ini sukses. */
    var isNativeAvailable: Boolean = false
        private set

    init {
        isNativeAvailable = runCatching {
            System.loadLibrary("aetherX")
            // Panggilan percobaan — kalau RegisterNatives untuk class ini
            // gagal di jni_onload.cpp (lihat catatan non-fatal di sana),
            // nativeResetCpuDelta akan melempar UnsatisfiedLinkError di
            // sini, tertangkap runCatching, dan isNativeAvailable = false.
            nativeResetCpuDelta()
            true
        }.getOrElse { t ->
            Log.w(TAG, "Modul native sysmonitor tidak tersedia: ${t.message}")
            false
        }
    }

    private external fun nativeReadCpuSnapshot(): FloatArray?
    private external fun nativeReadGpuSnapshot(): FloatArray?
    private external fun nativeResetCpuDelta()

    /**
     * Baca satu snapshot CPU. Elemen pertama hasil native adalah beban
     * agregat (semua core), sisanya per-core — dipecah di sini supaya
     * pemanggil Kotlin tidak perlu tahu detail layout array native.
     * Return null kalau native tidak tersedia atau /proc/stat gagal
     * dibaca sama sekali. Baca PERTAMA setelah [resetDelta] selalu
     * mengembalikan aggregatePercent/perCorePercent berisi -1 (belum ada
     * sampel pembanding) — pemanggil sebaiknya membuang sampel ini dari
     * grafik.
     */
    fun readCpuSnapshot(): CpuLoadSnapshot? {
        if (!isNativeAvailable) return null
        val raw = runCatching { nativeReadCpuSnapshot() }.getOrNull() ?: return null
        if (raw.isEmpty()) return null
        return CpuLoadSnapshot(
            aggregatePercent = raw[0],
            perCorePercent = raw.drop(1),
        )
    }

    /**
     * Baca satu snapshot GPU (load% + frekuensi MHz) lewat native/fopen
     * langsung. DIPERTAHANKAN untuk kompatibilitas/device yang path
     * sysfs GPU-nya kebetulan world-readable, tapi TIDAK dipakai lagi
     * sebagai jalur utama — lihat [readGpuSnapshotViaRoot].
     *
     * Root cause kenapa jalur ini sering gagal di banyak device Adreno:
     * `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` dkk umumnya
     * bermode 0440 (root-only read). Proses app biasa TIDAK otomatis
     * dapat izin baca file itu hanya karena `PrivilegeStatus.activeBackend
     * == ROOT` — status itu berarti app PUNYA akses menjalankan command
     * lewat `su` (lihat RootShellExecutor), bukan berarti UID proses app
     * berubah jadi root. fopen() native di sysmonitor.cpp berjalan
     * sebagai UID app biasa, jadi kena EACCES diam-diam (readSmallFile
     * return false, tidak melempar) dan nsmReadGpu selalu gagal di
     * device begini walau root granted.
     */
    fun readGpuSnapshot(): GpuLoadSnapshot? {
        if (!isNativeAvailable) return null
        val raw = runCatching { nativeReadGpuSnapshot() }.getOrNull() ?: return null
        if (raw.size < 2) return null
        return GpuLoadSnapshot(
            loadPercent = raw[0].takeIf { it >= 0f },
            freqMhz = raw[1].takeIf { it >= 0f },
        )
    }

    /** Path sysfs GPU yang dicoba berurutan, sama dengan yang dipakai native/SystemStatsProvider.kt. */
    private val gpuLoadPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/devfreq/gpufreq/gpu_busy",
    )
    private val gpuFreqPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/devfreq/gpufreq/cur_freq",
    )

    /**
     * Baca snapshot GPU lewat shell root (`su -c cat <path>`), memakai
     * [RootShellExecutor] yang sama dipakai fitur root lain di app ini.
     * Ini jalur UTAMA untuk GPU sekarang — beda dari CPU (/proc/stat)
     * yang tetap world-readable jadi cukup lewat native, file sysfs GPU
     * banyak device (termasuk Adreno) bermode root-only, jadi WAJIB
     * dibaca lewat proses shell root, bukan fopen() langsung dari proses
     * app. Satu panggilan `cat` menggabungkan semua path kandidat supaya
     * tidak perlu round-trip `su` berkali-kali tiap sampel (mahal).
     *
     * Return null kalau shell root gagal dijalankan sama sekali (root
     * dicabut, dll — lihat RootShellExecutor.exec). Return snapshot
     * dengan field null individual kalau shell berhasil tapi tidak satu
     * pun path GPU dikenal berhasil dibaca di device ini.
     */
    suspend fun readGpuSnapshotViaRoot(): GpuLoadSnapshot? {
        val allPaths = (gpuLoadPaths + gpuFreqPaths).joinToString(" ")
        val command = buildString {
            append("for f in ")
            append(allPaths)
            append("; do echo \"\$f=\$(cat \"\$f\" 2>/dev/null)\"; done")
        }
        val result = runCatching { RootShellExecutor().exec(command) }.getOrNull() ?: return null
        if (!result.success && result.output.isEmpty()) return null

        val values = result.output.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx < 0) return@mapNotNull null
            line.substring(0, idx) to line.substring(idx + 1).trim()
        }.toMap()

        val loadPercent = gpuLoadPaths.firstNotNullOfOrNull { path ->
            values[path]?.let { extractFirstNumberOrNull(it) }?.takeIf { it in 0f..100f }
        }
        val rawFreq = gpuFreqPaths.firstNotNullOfOrNull { path ->
            values[path]?.let { extractFirstNumberOrNull(it) }?.takeIf { it > 0f }
        }
        val freqMhz = rawFreq?.let { if (it > 100_000f) it / 1_000_000f else it }

        if (loadPercent == null && freqMhz == null) return null
        return GpuLoadSnapshot(loadPercent = loadPercent, freqMhz = freqMhz)
    }

    private fun extractFirstNumberOrNull(text: String): Float? =
        Regex("\\d+").find(text)?.value?.toFloatOrNull()

    /**
     * Reset state delta CPU di sisi native. WAJIB dipanggil setiap kali
     * monitor dimulai (mis. saat layar monitor dibuka lagi setelah
     * ditutup) supaya baca pertama tidak memakai delta basi dari sesi
     * sebelumnya (lihat nsmResetCpuDelta di sysmonitor.cpp).
     */
    fun resetDelta() {
        if (!isNativeAvailable) return
        runCatching { nativeResetCpuDelta() }
    }
}

/** Satu sampel beban CPU: agregat + per-core, dalam persen (0-100, atau null kalau tidak terbaca). */
data class CpuLoadSnapshot(
    val aggregatePercent: Float,
    val perCorePercent: List<Float>,
)

/** Satu sampel beban/frekuensi GPU. null pada salah satu field berarti tidak terbaca di device ini. */
data class GpuLoadSnapshot(
    val loadPercent: Float?,
    val freqMhz: Float?,
)
