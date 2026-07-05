package com.aether.x.core.appmanager

import androidx.compose.ui.graphics.ImageBitmap

/** Asal aplikasi — menentukan apakah boleh muncul di daftar App Manager sama sekali. */
enum class AppOrigin {
    /** Aplikasi pihak ketiga terpasang pengguna (bukan bawaan sistem) — selalu boleh di-freeze. */
    THIRD_PARTY,

    /**
     * Aplikasi sistem/bawaan pabrik yang namanya ADA di
     * `assets/app_manager_bloatware_whitelist.txt` — HANYA app yang cocok
     * whitelist ini yang ditampilkan dari kategori sistem. Lihat KDoc
     * [AppManagerCatalog] untuk alasan kenapa TIDAK menampilkan semua app
     * sistem secara bebas.
     */
    KNOWN_BLOATWARE,
}

/** Satu entri aplikasi terpasang yang berhak muncul di App Manager (lihat [AppOrigin]). */
data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val origin: AppOrigin,
    /** true kalau app ini SEDANG di-freeze (dinonaktifkan lewat `pm disable-user`). */
    val isFrozen: Boolean,
)
