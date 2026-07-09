package com.aether.x.core.apps

import android.content.Context
import android.content.Intent
import com.aether.x.data.AetherXPreferences

/**
 * Titik tunggal untuk "buka game dari dalam AetherX" (FITUR BARU — section
 * "Aktivitas Game" di Dashboard & Game Booster) — membuka game lewat launch
 * intent resminya SEKALIGUS mencatatnya sebagai [AetherXPreferences.recordGameLaunched]
 * supaya chip "Terakhir dipakai" di Dashboard dan urutan daftar game di Game
 * Booster selalu konsisten dari SATU sumber, tidak peduli dari layar mana
 * game itu dibuka.
 *
 * Berbeda dari [GameLauncher] (yang cuma tahu 2 game hardcoded Free Fire) —
 * fungsi ini menerima package name APAPUN (biasanya dari
 * [GameProfileCatalog.loadInstalledGames], daftar 500+ game generik), jadi
 * bisa dipakai untuk game apa pun yang terpasang, bukan hanya yang dikenal
 * [GameLauncher].
 */
object GameLaunchTracker {

    /**
     * Membuka [packageName] lewat launch intent resminya, lalu (HANYA kalau
     * berhasil dibuka) mencatatnya sebagai game terakhir dipakai. Mengembalikan
     * false kalau package tidak punya launch intent (mis. baru saja di-uninstall).
     */
    suspend fun launchAndTrack(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        val launched = try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
        if (launched) {
            AetherXPreferences(context.applicationContext).recordGameLaunched(packageName)
        }
        return launched
    }
}
