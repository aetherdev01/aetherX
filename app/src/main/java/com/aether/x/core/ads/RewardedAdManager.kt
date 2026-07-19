package com.aether.x.core.ads

import android.app.Activity

sealed interface RewardedAdResult {

    data object Rewarded : RewardedAdResult

    data object Cancelled : RewardedAdResult

    data object NotReady : RewardedAdResult

    data class Failed(val reason: String) : RewardedAdResult
}

interface RewardedAdManager {

    val isReady: Boolean

    fun preload()

    fun show(activity: Activity, onResult: (RewardedAdResult) -> Unit)
}

class NoOpRewardedAdManager : RewardedAdManager {
    override val isReady: Boolean = false
    override fun preload() = Unit
    override fun show(activity: Activity, onResult: (RewardedAdResult) -> Unit) {
        onResult(RewardedAdResult.NotReady)
    }
}
