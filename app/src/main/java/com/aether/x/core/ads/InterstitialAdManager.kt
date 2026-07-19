package com.aether.x.core.ads

import android.app.Activity

sealed interface InterstitialAdResult {

    data object Shown : InterstitialAdResult

    data object NotReady : InterstitialAdResult

    data class Failed(val reason: String) : InterstitialAdResult
}

interface InterstitialAdManager {

    val isReady: Boolean

    fun preload()

    fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit)
}

class NoOpInterstitialAdManager : InterstitialAdManager {
    override val isReady: Boolean = false
    override fun preload() = Unit
    override fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit) {
        onResult(InterstitialAdResult.NotReady)
    }
}
