package com.aether.x.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Interstitial router for free users.
 *
 * AdMob is preferred. Unity Ads is used as a fallback when AdMob is not
 * ready or fails to show. Both providers are preloaded in parallel.
 */
class FallbackInterstitialAdManager(
    private val adMob: AdMobInterstitialAdManager,
    private val unity: UnityInterstitialAdManager,
) : InterstitialAdManager {

    private companion object {
        const val TAG = "FallbackInterstitialAd"
    }

    override val isReady: Boolean
        get() = adMob.isReady || unity.isReady

    fun initialize(context: Context, activity: Activity) {
        adMob.initialize(context)
        unity.initialize(activity)
    }

    override fun preload() {
        adMob.preload()
        unity.preload()
    }

    override fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit) {
        when {
            adMob.isReady -> {
                adMob.show(activity) { result ->
                    if (result is InterstitialAdResult.Failed && unity.isReady) {
                        Log.w(TAG, "AdMob gagal ditampilkan, fallback ke Unity: ${result.reason}")
                        unity.show(activity, onResult)
                    } else {
                        onResult(result)
                    }
                }
            }
            unity.isReady -> unity.show(activity, onResult)
            else -> {
                preload()
                onResult(InterstitialAdResult.NotReady)
            }
        }
    }
}
