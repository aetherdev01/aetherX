package com.aether.x.core.ads

import android.app.Activity
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.RewardQuotaState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface RewardGateResult {

    data object Allowed : RewardGateResult

    data object RequiresAd : RewardGateResult
}

class RewardGate(
    private val preferences: AetherXPreferences,
    private val adManager: RewardedAdManager,
) {

    sealed interface WatchAdResult {

        data object CreditGranted : WatchAdResult

        data object Cancelled : WatchAdResult

        data object AdNotReady : WatchAdResult
    }

    suspend fun checkAccess(featureKey: String, isMember: Boolean, freeUsesPerDay: Int): RewardGateResult {
        if (isMember) return RewardGateResult.Allowed

        val state = preferences.getRewardQuota(featureKey, today())
        val hasFreeUseLeft = state.freeUsesToday < freeUsesPerDay
        val hasCreditLeft = state.extraCredits > 0
        return if (hasFreeUseLeft || hasCreditLeft) RewardGateResult.Allowed else RewardGateResult.RequiresAd
    }

    suspend fun remainingFreeUses(featureKey: String, freeUsesPerDay: Int): Int {
        val state = preferences.getRewardQuota(featureKey, today())
        return (freeUsesPerDay - state.freeUsesToday).coerceAtLeast(0)
    }

    suspend fun consumeUse(featureKey: String, isMember: Boolean, freeUsesPerDay: Int) {
        if (isMember) return

        val current = preferences.getRewardQuota(featureKey, today())
        val updated = if (current.extraCredits > 0) {
            current.copy(extraCredits = current.extraCredits - 1)
        } else {
            current.copy(freeUsesToday = (current.freeUsesToday + 1).coerceAtMost(freeUsesPerDay))
        }
        preferences.setRewardQuota(updated)
    }

    suspend fun watchAdForCredit(featureKey: String, activity: Activity): WatchAdResult {
        if (!adManager.isReady) return WatchAdResult.AdNotReady

        val result = showAdSuspend(activity)
        return when (result) {
            RewardedAdResult.Rewarded -> {
                val current = preferences.getRewardQuota(featureKey, today())
                preferences.setRewardQuota(current.copy(extraCredits = current.extraCredits + 1))
                WatchAdResult.CreditGranted
            }
            RewardedAdResult.Cancelled -> WatchAdResult.Cancelled
            RewardedAdResult.NotReady -> WatchAdResult.AdNotReady
            is RewardedAdResult.Failed -> WatchAdResult.AdNotReady
        }
    }

    private suspend fun showAdSuspend(activity: Activity): RewardedAdResult =
        suspendCancellableCoroutine { cont ->
            adManager.show(activity) { result ->
                if (cont.isActive) cont.resume(result) { _, _, _ -> }
            }
        }

    private fun today(): String = DATE_FORMAT.format(Date())

    private companion object {

        val DATE_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
