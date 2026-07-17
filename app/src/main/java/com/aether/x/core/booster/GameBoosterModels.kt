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
    // FITUR BARU (lihat perintah rework — "rework total tampilan game
    // booster seperti di foto pertama": gauge RAM di sisi kanan layar):
    // dibaca via ActivityManager.getMemoryInfo standar (BUKAN lewat
    // shell/root seperti cpuLoadPercent/gpuLoadPercent) — API publik
    // biasa yang selalu tersedia terlepas dari status Root/Shizuku,
    // sehingga TIDAK PERNAH null (beda dari cpuLoadPercent/gpuLoadPercent
    // yang bisa null tanpa Root — lihat GameBoosterMonitor).
    val ramLoadPercent: Int? = null,
    // Riwayat FPS 60 sampel terakhir (kira-kira 1 menit pada interval baca
    // 1 detik) — dipakai untuk gambar grafik garis di sidebar/layar penuh,
    // BUKAN untuk kalkulasi lain. Selalu diurutkan lama->baru (index 0 =
    // sampel terlama dalam jendela ini).
    val fpsHistory: List<Int> = emptyList(),
)

/**
 * FITUR BARU (rework total Game Booster — rail "quick app" di sisi kiri
 * panel, lihat perintah rework): satu entri app yang MASIH punya task
 * hidup di recent apps, siap ditampilkan sebagai ikon di rail vertikal
 * kiri panel. Dimuat oleh [com.aether.x.core.overlay.GameBoosterOverlayService]
 * lewat [com.aether.x.core.monitor.RecentTasksReader.listRecentPackages]
 * (nama & package) + [com.aether.x.core.apps.GameProfileCatalog.loadIconForPackage]
 * (ikon), MIRIP pola [GameBoosterSession.icon] tapi untuk banyak app
 * sekaligus alih-alih satu game yang sedang di-boost.
 */
data class RecentAppEntry(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
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
    // FITUR BARU (lihat perintah rework floating booster — card game
    // menampilkan ikon ASLI, bukan ikon generik SportsEsports seperti
    // sebelumnya): dimuat SEKALI saat sesi dimulai lewat
    // com.aether.x.core.apps.GameProfileCatalog.loadIconForPackage, bukan
    // di-load ulang tiap recomposition. Null selama masih dimuat ATAU
    // kalau package sudah di-uninstall / iconnya gagal dibaca — UI harus
    // fallback ke ikon generik pada kasus null, BUKAN menampilkan area
    // kosong.
    val icon: androidx.compose.ui.graphics.ImageBitmap? = null,
    // true selama animasi splash Game Booster berjalan (lihat
    // GameBoosterSplashActivity) — floating sidebar SENGAJA tidak
    // ditampilkan sampai splash ini selesai supaya tidak tumpang tindih
    // secara visual dengan animasinya sendiri.
    val showingSplash: Boolean = false,
    // FITUR BARU (lihat perintah rework — "rework total tampilan game
    // booster seperti di foto pertama", menu "Kunci Rotasi" & "Akselerasi
    // Sentuhan"): pola umur-hidup SAMA seperti dndEnabled/fpsOverlayEnabled
    // di atas — berlaku selama sesi ini, disinkronkan ke
    // AetherXPreferences.gameBoosterRotationLocked/gameBoosterTouchBoostEnabled
    // oleh GameBoosterScreenViewModel setiap kali diubah dari UI.
    val rotationLocked: Boolean = false,
    val touchBoostEnabled: Boolean = false,
    // FITUR BARU (rework total tampilan — lihat perintah rework "bisa di
    // minimize dengan mudah"): rail quick-app (lihat [RecentAppEntry] di
    // atas), dimuat ULANG setiap kali panel dibuka (bukan sekali di awal
    // sesi seperti [icon]) supaya daftar recent apps selalu mencerminkan
    // app yang BENAR-BENAR baru saja dipakai pengguna, bukan snapshot basi
    // dari saat sesi boost pertama kali dimulai.
    val recentApps: List<RecentAppEntry> = emptyList(),
)
