package com.aether.x.core.booster

import com.aether.x.data.GameMode

/**
 * Snapshot lengkap monitoring performa real-time Game Booster — dibaca
 * berkala oleh [GameBoosterMonitor] SELAMA sidebar/overlay Game Booster
 * aktif (lihat perintah rework: "ada monitoring seperti grafik").
 *
 * BERBEDA dari [com.aether.x.ui.dashboard.DashboardUiState] (yang SUDAH
 * TIDAK punya CPU/GPU/Suhu sama sekali sejak rework total Dashboard) —
 * monitoring real-time SEKARANG murni domain Game Booster, dipakai HANYA
 * selama sesi bermain, bukan di layar ringkasan yang dilihat sebentar-
 * sebentar.
 */
data class GameBoosterMetrics(
    val fps: Int? = null,
    val cpuLoadPercent: Int? = null,
    val gpuLoadPercent: Int? = null,
    val temperatureCelsius: Float? = null,
    // Riwayat FPS 60 sampel terakhir (kira-kira 1 menit pada interval baca
    // 1 detik) — dipakai untuk gambar grafik garis di sidebar/layar penuh,
    // BUKAN untuk kalkulasi lain. Selalu diurutkan lama->baru (index 0 =
    // sampel terlama dalam jendela ini).
    val fpsHistory: List<Int> = emptyList(),
)

/**
 * Satu sesi Game Booster aktif — package game yang sedang di-boost beserta
 * preset & toggle yang berlaku SELAMA sesi ini. Instance-nya dipegang oleh
 * [GameBoosterOverlayService] (bukan ViewModel biasa, karena overlay hidup
 * di luar lifecycle Activity manapun — lihat KDoc service tsb) dan
 * diobservasi oleh [com.aether.x.ui.booster.GameBoosterScreen] lewat
 * [GameBoosterOverlayService.activeSession] saat layar itu dibuka
 * bersamaan dengan sesi yang sedang berjalan (mis. pengguna kembali ke
 * AetherX dari game tanpa menutup game-nya).
 */
data class GameBoosterSession(
    val packageName: String,
    val gameLabel: String,
    val mode: GameMode,
    val dndEnabled: Boolean,
    val fpsOverlayEnabled: Boolean,
    val metrics: GameBoosterMetrics = GameBoosterMetrics(),
    // true selama animasi splash Game Booster berjalan (lihat
    // GameBoosterSplashActivity) — floating sidebar SENGAJA tidak
    // ditampilkan sampai splash ini selesai supaya tidak tumpang tindih
    // secara visual dengan animasinya sendiri.
    val showingSplash: Boolean = false,
)
