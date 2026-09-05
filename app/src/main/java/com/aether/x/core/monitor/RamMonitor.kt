package com.aether.x.core.monitor

import android.util.Log

/**
 * RamMonitor — jembatan ke modul native rammonitor (v3.5, fitur RAM
 * Cleaner). Lihat KDoc lengkap di rammonitor.h untuk alasan desain.
 *
 * BEDA PENTING dari [RootSystemMonitor]: modul ini TIDAK root-gated sama
 * sekali — /proc/meminfo adalah ringkasan memori SISTEM (bukan
 * per-proses), sudah world-readable di semua versi Android. Pemanggil
 * (RamCleanerViewModel) boleh langsung pakai ini tanpa cek
 * PrivilegeManager.status terlebih dahulu.
 *
 * Native lib yang dimuat SAMA dengan SignatureGuard/AdBlockDetector/
 * DeviceFingerprint/RootSystemMonitor (satu libaetherX.so) —
 * System.loadLibrary aman dipanggil berkali-kali (JVM hanya memuat
 * native lib sekali per proses).
 */
object RamMonitor {

    private const val TAG = "RamMonitor"

    /** true kalau libaetherX.so berhasil dimuat DAN RegisterNatives untuk class ini sukses. */
    var isNativeAvailable: Boolean = false
        private set

    init {
        isNativeAvailable = runCatching {
            System.loadLibrary("aetherX")
            // Panggilan percobaan sekali di init — kalau RegisterNatives
            // untuk class ini gagal di jni_onload.cpp, ini akan melempar
            // UnsatisfiedLinkError, tertangkap runCatching di bawah.
            nativeReadRamSnapshot()
            true
        }.getOrElse { t ->
            Log.e(TAG, "Modul native rammonitor tidak tersedia", t)
            false
        }
    }

    private external fun nativeReadRamSnapshot(): FloatArray?

    /**
     * Baca satu snapshot RAM. Return null kalau native tidak tersedia
     * ATAU /proc/meminfo gagal dibuka sama sekali di device ini (sangat
     * jarang). usedKb/usedPercent dihitung di sini dari
     * totalKb-availableKb, BUKAN dari MemFree — MemAvailable adalah
     * estimasi kernel yang sudah memperhitungkan cache yang bisa
     * di-reclaim, representasi "RAM bebas sebenarnya" yang jauh lebih
     * akurat daripada MemFree mentah (lihat KDoc rammonitor.h).
     */
    fun readSnapshot(): RamSnapshot? {
        if (!isNativeAvailable) return null
        val raw = runCatching { nativeReadRamSnapshot() }.getOrNull() ?: return null
        if (raw.size < 4) return null

        val totalKb = raw[0]
        val availableKb = raw[1].takeIf { it >= 0f }
        val swapTotalKb = raw[2].takeIf { it >= 0f }
        val swapFreeKb = raw[3].takeIf { it >= 0f }

        if (totalKb <= 0f) return null

        val usedKb = availableKb?.let { (totalKb - it).coerceAtLeast(0f) }
        val usedPercent = usedKb?.let { (it / totalKb * 100f).coerceIn(0f, 100f) }

        return RamSnapshot(
            totalKb = totalKb,
            availableKb = availableKb,
            usedKb = usedKb,
            usedPercent = usedPercent,
            swapTotalKb = swapTotalKb,
            swapFreeKb = swapFreeKb,
        )
    }
}

/**
 * Satu sampel RAM sistem, semua nilai dalam KB kecuali usedPercent (0-100).
 * Field selain [totalKb] bisa null kalau baris terkait tidak ditemukan di
 * /proc/meminfo device ini (jarang terjadi).
 */
data class RamSnapshot(
    val totalKb: Float,
    val availableKb: Float?,
    val usedKb: Float?,
    val usedPercent: Float?,
    val swapTotalKb: Float?,
    val swapFreeKb: Float?,
)
