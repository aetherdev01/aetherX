package com.aether.x.core.ads

import android.app.Activity

class InterstitialAdGate(
    private val adManager: InterstitialAdManager,
) {

    suspend fun maybeShow(activity: Activity, isMember: Boolean) {
        if (isMember) return

        val adBlockSignals = AdBlockDetector.detect(activity)
        if (adBlockSignals.anyDetected) {
            AdBlockDialogState.requestShow(activity, adBlockSignals)
            return
        }

        if (!adManager.isReady) return

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
