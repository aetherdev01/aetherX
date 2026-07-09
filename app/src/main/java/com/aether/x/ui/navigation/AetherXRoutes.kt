package com.aether.x.ui.navigation

object AetherXRoutes {
    const val PERMISSION_ONBOARDING = "permission_onboarding"
    // FITUR BARU (lihat perintah rework — "kenapa splash screen yang ada logo dan
    // loading nya hanya saat awal setup, setelah setup splash screen nya ga
    // muncul"): route splash TERPISAH dari alur onboarding lama, dipakai sebagai
    // startDestination SETIAP cold start (bukan hanya sebelum onboarding
    // selesai) — lihat MainActivity.AetherXRoot().
    const val SPLASH_MAIN = "splash_main"
    const val MAIN = "main"
    const val MANAGE_ACCESS = "manage_access"
    // FITUR BARU — layar penuh landscape Game Booster, dibuka dari drawer
    // TweakScreen (lihat TweakDrawerContent) sebagai destination NavHost
    // TERPISAH (bukan TweakSubTab internal) karena butuh memaksa orientasi
    // landscape yang berbeda dari seluruh app lain yang portrait.
    const val GAME_BOOSTER = "game_booster"
}
