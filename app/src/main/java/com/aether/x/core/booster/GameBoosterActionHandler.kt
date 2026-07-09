package com.aether.x.core.booster

import android.os.Environment
import com.aether.x.core.shell.ShellExecutor
import com.aether.x.data.CpuGovernor
import com.aether.x.data.GameMode
import com.aether.x.data.TweakRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menerapkan efek nyata dari menu Game Booster (lihat perintah rework:
 * "ada pilihan menu banyak, dari ... mode game, jangan ganggu, screenshot,
 * mode boost, mode hemat") — SEMUA lewat [TweakRepository] yang SUDAH ADA
 * (tidak menduplikasi logic shell), supaya "Mode Boost" di Game Booster
 * konsisten persis dengan governor Performance manual di layar Tweak biasa,
 * bukan implementasi shell terpisah yang bisa berbeda perilaku.
 */
class GameBoosterActionHandler(private val repository: TweakRepository = TweakRepository()) {

    /**
     * Terapkan [mode] (LOW="Mode Hemat", MID="Mode Normal", BOOST="Mode
     * Boost") sebagai kombinasi CPU governor + GPU performance mode:
     * - LOW: [CpuGovernor.POWERSAVE] + GPU performance OFF — prioritaskan
     *   baterai/suhu di atas performa, untuk game ringan atau saat baterai
     *   menipis.
     * - MID: [CpuGovernor.UNIVERSAL] (governor bawaan optimal per-chipset)
     *   + GPU performance OFF — keseimbangan default, cocok untuk
     *   mayoritas game.
     * - BOOST: [CpuGovernor.PERFORMANCE] + GPU performance ON — performa
     *   maksimum, mengorbankan baterai/suhu, untuk game berat/kompetitif.
     */
    suspend fun applyMode(executor: ShellExecutor, mode: GameMode) {
        val governor = when (mode) {
            GameMode.LOW -> CpuGovernor.POWERSAVE
            GameMode.MID -> CpuGovernor.UNIVERSAL
            GameMode.BOOST -> CpuGovernor.PERFORMANCE
        }
        repository.applyCpuGovernor(executor, governor)
        repository.applyGpuPerformanceMode(executor, enabled = mode == GameMode.BOOST)
    }

    /** Toggle "Jangan Ganggu" (DND) — reuse [TweakRepository.applyGameMode], lihat KDoc-nya. */
    suspend fun applyDnd(executor: ShellExecutor, enabled: Boolean) {
        repository.applyGameMode(executor, enabled)
    }

    /**
     * Ambil screenshot lewat `screencap` shell (butuh root/Shizuku — TIDAK
     * memakai [android.media.projection.MediaProjection] supaya tidak perlu
     * meminta izin capture layar terpisah setiap sesi, yang akan
     * mengganggu alur "buka Game Booster lalu langsung main" — root/Shizuku
     * sudah merupakan prasyarat mayoritas fitur lain di app ini).
     *
     * File disimpan ke folder Pictures/Screenshots publik (lokasi standar
     * screenshot Android) dengan nama `AetherX_<timestamp>.png`, supaya
     * langsung muncul di galeri seperti screenshot tombol volume+power
     * biasa — BUKAN disimpan ke folder privat app yang tidak terlihat
     * pengguna.
     *
     * Mengembalikan path absolut file kalau berhasil, atau null kalau gagal
     * (mis. shell command gagal, atau tidak ada storage permission untuk
     * folder publik di beberapa ROM lama).
     */
    suspend fun takeScreenshot(executor: ShellExecutor): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val screenshotDir = "${Environment.getExternalStorageDirectory().path}/Pictures/Screenshots"
        val filePath = "$screenshotDir/AetherX_$timestamp.png"
        val script = """
            mkdir -p "$screenshotDir"
            screencap -p "$filePath"
        """.trimIndent()
        val result = executor.exec(script)
        return if (result.success) filePath else null
    }
}
