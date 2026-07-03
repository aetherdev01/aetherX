package com.aether.x.core.security

import com.aether.x.data.AetherXPreferences

/** Hasil pemeriksaan [LicenseAttemptGuard.checkBeforeAttempt]. */
sealed interface AttemptGuardResult {
    /** Boleh lanjut mencoba aktivasi. */
    data object Allowed : AttemptGuardResult

    /** Sedang lockout — [remainingSeconds] detik lagi sebelum boleh coba lagi. */
    data class Locked(val remainingSeconds: Long) : AttemptGuardResult
}

/**
 * GUARD LAPIS KEDUA terhadap brute-force kode lisensi, di SISI CLIENT.
 *
 * Ini melengkapi guard utama di [SECURITY.md]/firestore.rules (App Check —
 * yang menutup akses dari LUAR app), dengan membatasi kecepatan percobaan
 * DARI DALAM app asli itu sendiri. Tanpa ini, seseorang yang memakai app
 * asli (App Check lolos) tetap bisa mengetik-coba banyak kode secara manual
 * atau lewat automasi UI (mis. UI Automator/Accessibility script) dengan
 * cepat. Guard ini membuat percobaan berulang jadi lambat secara sengaja:
 *
 * - Setelah [SOFT_LIMIT] percobaan gagal berturut-turut dalam satu window,
 *   mulai kena lockout singkat.
 * - Tiap kelipatan berikutnya, durasi lockout naik (backoff eksponensial,
 *   dibatasi [MAX_LOCKOUT_MILLIS]) — mencontoh pola lockout percobaan
 *   password di banyak sistem auth.
 * - State-nya DIPERSIST ke DataStore ([AetherXPreferences]), bukan cuma
 *   in-memory, supaya tidak bisa direset dengan sekadar force-close lalu
 *   buka ulang aplikasi.
 * - Percobaan yang GAGAL karena [com.aether.x.data.LicenseResult.NetworkError]
 *   TIDAK dihitung sebagai percobaan gagal (offline bukan salah pengguna).
 *
 * CATATAN: ini bukan pengganti penegakan sisi server (App Check +
 * firestore.rules) — device yang di-root/APK dimodifikasi tetap bisa
 * mem-bypass guard ini secara teori. Tapi kombinasi keduanya membuat
 * brute-force lewat app asli menjadi sangat lambat (menit -> berhari-hari
 * untuk jumlah percobaan yang sama), sementara App Check menutup jalur
 * yang lebih cepat (langsung ke REST API) sama sekali.
 */
class LicenseAttemptGuard(private val preferences: AetherXPreferences) {

    private companion object {
        /** Mulai kena lockout setelah percobaan gagal ke-berapa. */
        const val SOFT_LIMIT = 3

        /** Window waktu (ms) sebelum penghitung percobaan gagal direset otomatis. */
        const val WINDOW_MILLIS = 10 * 60 * 1000L // 10 menit

        /** Lockout awal setelah melewati SOFT_LIMIT. */
        const val BASE_LOCKOUT_MILLIS = 30 * 1000L // 30 detik

        /** Lockout maksimum, berapa pun banyaknya percobaan gagal beruntun. */
        const val MAX_LOCKOUT_MILLIS = 60 * 60 * 1000L // 1 jam
    }

    /** Dipanggil SEBELUM mengirim percobaan aktivasi ke [LicenseRepository.activate]. */
    suspend fun checkBeforeAttempt(nowMillis: Long = System.currentTimeMillis()): AttemptGuardResult {
        val state = preferences.getLicenseAttemptState()
        val lockoutUntil = state.lockoutUntilMillis
        if (lockoutUntil != null && lockoutUntil > nowMillis) {
            val remainingSeconds = (lockoutUntil - nowMillis + 999) / 1000
            return AttemptGuardResult.Locked(remainingSeconds)
        }
        return AttemptGuardResult.Allowed
    }

    /** Dipanggil setiap kali aktivasi GAGAL karena kode salah/terpakai/dsb (bukan NetworkError). */
    suspend fun recordFailure(nowMillis: Long = System.currentTimeMillis()) {
        val state = preferences.getLicenseAttemptState()
        val windowStillValid = state.windowStartMillis != null &&
            (nowMillis - state.windowStartMillis) < WINDOW_MILLIS

        val windowStart = if (windowStillValid) state.windowStartMillis!! else nowMillis
        val newCount = if (windowStillValid) state.failedAttemptCount + 1 else 1

        val lockoutUntil = if (newCount >= SOFT_LIMIT) {
            val overBy = newCount - SOFT_LIMIT
            // Backoff eksponensial: 30d, 60d, 120d, ... dibatasi MAX_LOCKOUT_MILLIS.
            val multiplier = 1L shl overBy.coerceAtMost(20) // hindari overflow shift
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

    /** Dipanggil setiap kali aktivasi BERHASIL — reset penuh guard ini. */
    suspend fun recordSuccess() {
        preferences.clearLicenseAttemptState()
    }
}
