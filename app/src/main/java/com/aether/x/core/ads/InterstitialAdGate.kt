package com.aether.x.core.ads

import android.app.Activity

class InterstitialAdGate(
    private val adManager: InterstitialAdManager,
) {

    suspend fun maybeShow(activity: Activity, isMember: Boolean) {
        if (isMember) return

        if (!adManager.isReady) {
            // Kedua network dipreload sejak startup. Jika keduanya belum siap
            // pada trigger ini, jangan menunda aksi user; biarkan preload
            // berjalan dan gunakan iklan pada trigger berikutnya.
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

    private companion object {

        @Volatile
        private var lastShownAtMillis: Long = 0L

        const val COOLDOWN_MILLIS = 60_000L
    }
}
