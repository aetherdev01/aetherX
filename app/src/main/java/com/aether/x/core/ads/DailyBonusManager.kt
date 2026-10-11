package com.aether.x.core.ads

import android.app.Activity
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.RewardFeatureKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyBonusStatus(
    val claimedToday: Boolean,
    /** Streak yang SEDANG berjalan (0 kalau putus / belum pernah klaim). */
    val streak: Int,
    /** Kredit yang akan didapat kalau klaim sekarang. */
    val nextRewardCredits: Int,
)

sealed interface DailyBonusClaimResult {
    data class Granted(val credits: Int, val streak: Int) : DailyBonusClaimResult
    data object AlreadyClaimed : DailyBonusClaimResult
    data object AdNotReady : DailyBonusClaimResult
    data object Cancelled : DailyBonusClaimResult
}

/**
 * Bonus harian lewat iklan reward.
 *
 * Aturan:
 *  - Bisa diklaim 1x per hari (zona waktu device), dengan menonton 1 iklan reward.
 *  - Hadiah: kredit tambahan untuk fitur ber-gate (Preset Kernel) — jumlahnya
 *    naik seiring streak: [BASE_CREDITS] + (streak - 1), maksimum [MAX_CREDITS].
 *  - Streak putus kalau kemarin tidak klaim; mulai lagi dari 1.
 *  - Hadiah HANYA diberikan setelah iklan benar-benar selesai ditonton
 *    ([RewardedAdResult.Rewarded]); batal / gagal tidak menghabiskan klaim hari ini.
 */
class DailyBonusManager(
    private val preferences: AetherXPreferences,
    private val adManager: RewardedAdManager,
) {

    suspend fun status(): DailyBonusStatus {
        val prefs = preferences.preferences.first()
        val today = dateKey(0)
        val claimedToday = prefs.dailyBonusLastDate == today
        val liveStreak = when (prefs.dailyBonusLastDate) {
            today, dateKey(-1) -> prefs.dailyBonusStreak
            else -> 0
        }
        val nextStreak = if (claimedToday) liveStreak else liveStreak + 1
        return DailyBonusStatus(
            claimedToday = claimedToday,
            streak = liveStreak,
            nextRewardCredits = creditsFor(nextStreak),
        )
    }

    suspend fun claim(activity: Activity): DailyBonusClaimResult {
        val current = status()
        if (current.claimedToday) return DailyBonusClaimResult.AlreadyClaimed

        if (!adManager.isReady) {
            adManager.preload()
            return DailyBonusClaimResult.AdNotReady
        }

        return when (showAd(activity)) {
            RewardedAdResult.Rewarded -> {
                val newStreak = current.streak + 1
                val credits = creditsFor(newStreak)
                val today = dateKey(0)

                val quota = preferences.getRewardQuota(RewardFeatureKeys.KERNEL_PRESET, today)
                preferences.setRewardQuota(quota.copy(extraCredits = quota.extraCredits + credits))
                preferences.setDailyBonusClaimed(today, newStreak)

                DailyBonusClaimResult.Granted(credits, newStreak)
            }
            RewardedAdResult.Cancelled -> DailyBonusClaimResult.Cancelled
            RewardedAdResult.NotReady,
            is RewardedAdResult.Failed -> {
                adManager.preload()
                DailyBonusClaimResult.AdNotReady
            }
        }
    }

    private suspend fun showAd(activity: Activity): RewardedAdResult =
        suspendCancellableCoroutine { cont ->
            adManager.show(activity) { result ->
                if (cont.isActive) cont.resume(result) { _, _, _ -> }
            }
        }

    private fun creditsFor(streak: Int): Int =
        (BASE_CREDITS + (streak - 1).coerceAtLeast(0)).coerceAtMost(MAX_CREDITS)

    /** yyyyMMdd untuk hari ini + [dayOffset] hari (format sama dengan RewardGate). */
    private fun dateKey(dayOffset: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(cal.timeInMillis))
    }

    companion object {
        const val BASE_CREDITS = 3
        const val MAX_CREDITS = 7
        /** Panjang siklus streak yang ditampilkan di UI. */
        const val STREAK_CYCLE = 7
    }
}
