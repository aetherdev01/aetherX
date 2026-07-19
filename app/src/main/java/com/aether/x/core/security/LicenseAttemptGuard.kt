package com.aether.x.core.security

import com.aether.x.data.AetherXPreferences

sealed interface AttemptGuardResult {

    data object Allowed : AttemptGuardResult

    data class Locked(val remainingSeconds: Long) : AttemptGuardResult
}

class LicenseAttemptGuard(private val preferences: AetherXPreferences) {

    private companion object {

        const val SOFT_LIMIT = 3

        const val WINDOW_MILLIS = 10 * 60 * 1000L

        const val BASE_LOCKOUT_MILLIS = 30 * 1000L

        const val MAX_LOCKOUT_MILLIS = 60 * 60 * 1000L
    }

    suspend fun checkBeforeAttempt(nowMillis: Long = System.currentTimeMillis()): AttemptGuardResult {
        val state = preferences.getLicenseAttemptState()
        val lockoutUntil = state.lockoutUntilMillis
        if (lockoutUntil != null && lockoutUntil > nowMillis) {
            val remainingSeconds = (lockoutUntil - nowMillis + 999) / 1000
            return AttemptGuardResult.Locked(remainingSeconds)
        }
        return AttemptGuardResult.Allowed
    }

    suspend fun recordFailure(nowMillis: Long = System.currentTimeMillis()) {
        val state = preferences.getLicenseAttemptState()
        val windowStillValid = state.windowStartMillis != null &&
            (nowMillis - state.windowStartMillis) < WINDOW_MILLIS

        val windowStart = if (windowStillValid) state.windowStartMillis!! else nowMillis
        val newCount = if (windowStillValid) state.failedAttemptCount + 1 else 1

        val lockoutUntil = if (newCount >= SOFT_LIMIT) {
            val overBy = newCount - SOFT_LIMIT

            val multiplier = 1L shl overBy.coerceAtMost(20)
            val duration = (BASE_LOCKOUT_MILLIS * multiplier).coerceAtMost(MAX_LOCKOUT_MILLIS)
            nowMillis + duration
        } else {
            null
        }

        preferences.recordFailedLicenseAttempt(
            failedAttemptCount = newCount,
            windowStartMillis = windowStart,
            lockoutUntilMillis = lockoutUntil,
        )
    }

    suspend fun recordSuccess() {
        preferences.clearLicenseAttemptState()
    }
}
