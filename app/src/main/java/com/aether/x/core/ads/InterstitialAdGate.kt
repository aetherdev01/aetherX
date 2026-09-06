package com.aether.x.core.ads

import android.app.Activity

class InterstitialAdGate(
    private val adManager: InterstitialAdManager,
) {

    suspend fun maybeShow(activity: Activity, isMember: Boolean) {
        if (isMember) return

        if (!adManager.isReady) {
            // Iklan belum siap saat dibutuhkan — minta muat lagi sekarang juga
            // (bukan cuma menunggu retry loop internal yang bisa lagi delay
            // panjang), supaya kunjungan layar berikutnya punya peluang lebih
            // besar dapat iklan yang sudah siap.
            adManager.preload()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastShownAtMillis < COOLDOWN_MILLIS) return

        lastShownAtMillis = now
        adManager.show(activity) {  }
    }

    private companion object {

        @Volatile
        private var lastShownAtMillis: Long = 0L

        const val COOLDOWN_MILLIS = 60_000L
    }
}
