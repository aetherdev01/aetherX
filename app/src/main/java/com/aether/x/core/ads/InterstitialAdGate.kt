package com.aether.x.core.ads

import android.app.Activity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pengatur kapan interstitial boleh tampil untuk user FREE.
 *
 * v3.6 — iklan dibuat LEBIH SERING, tidak hanya setelah aksi tweak:
 *  1. [maybeShow]      : setelah aksi tweak/toggle berhasil (titik lama).
 *  2. [onNavigation]   : tiap [NAV_INTERVAL] perpindahan tab / sub-menu.
 *  3. [onReturnToApp]  : saat user kembali ke app setelah >= [RETURN_AWAY_MILLIS].
 *
 * Semua jalur melewati cooldown yang SAMA ([COOLDOWN_MILLIS], dipakai bersama
 * lintas semua trigger) supaya tidak pernah muncul beruntun, tidak pernah
 * memblokir/menunda aksi user, dan member tidak pernah melihat iklan.
 */
class InterstitialAdGate(
    private val adManager: InterstitialAdManager,
) {

    suspend fun maybeShow(activity: Activity, isMember: Boolean) {
        if (isMember) return

        if (!adManager.isReady) {
            // Jangan menunda aksi user; minta preload dan pakai di trigger berikutnya.
            adManager.preload()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastShownAtMillis < COOLDOWN_MILLIS) return

        adManager.show(activity) { result ->
            // Jangan mengunci cooldown ketika provider gagal atau belum siap.
            if (result is InterstitialAdResult.Shown) {
                lastShownAtMillis = System.currentTimeMillis()
            } else {
                adManager.preload()
            }
        }
    }

    /** Dipanggil tiap kali user pindah tab utama / sub-menu. Tampil tiap [NAV_INTERVAL] kali. */
    suspend fun onNavigation(activity: Activity, isMember: Boolean) {
        if (isMember) return
        if (navigationCount.incrementAndGet() % NAV_INTERVAL != 0) return
        maybeShow(activity, isMember)
    }

    /** Dipanggil saat app kembali ke foreground; hanya jika sempat ditinggal cukup lama. */
    suspend fun onReturnToApp(activity: Activity, isMember: Boolean, awayMillis: Long) {
        if (isMember) return
        if (awayMillis < RETURN_AWAY_MILLIS) return
        maybeShow(activity, isMember)
    }

    private companion object {

        @Volatile
        private var lastShownAtMillis: Long = 0L

        private val navigationCount = AtomicInteger(0)

        /** Jeda minimum antar tampilan (dibagi lintas semua trigger). */
        const val COOLDOWN_MILLIS = 30_000L

        /** Iklan tiap N perpindahan tab/sub-menu. */
        const val NAV_INTERVAL = 2

        /** Minimum lama app di background sebelum "kembali ke app" dihitung. */
        const val RETURN_AWAY_MILLIS = 20_000L
    }
}
