package com.aether.x.core.ads

import android.app.Activity
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.RewardQuotaState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Hasil pengecekan [RewardGate.checkAccess] — lihat KDoc masing-masing varian. */
sealed interface RewardGateResult {
    /**
     * Boleh langsung jalankan aksinya SEKARANG, TANPA menampilkan iklan
     * apa pun. Terjadi kalau: pengguna member aktif ([RewardGate] selalu
     * skip iklan untuk member — lihat KDoc kelas), ATAU kuota gratis harian
     * fitur ini belum habis, ATAU masih ada kredit ekstra tersisa dari
     * tontonan iklan sebelumnya.
     */
    data object Allowed : RewardGateResult

    /**
     * Kuota gratis harian sudah habis dan tidak ada kredit ekstra tersisa —
     * pengguna PERLU menonton rewarded ad dulu (lihat [RewardGate.watchAdForCredit])
     * sebelum aksi ini boleh dijalankan lagi. UI sebaiknya menampilkan
     * tombol "Tonton Iklan untuk Pakai Lagi" alih-alih menjalankan aksi.
     */
    data object RequiresAd : RewardGateResult
}

/**
 * GUARD GENERIK untuk fitur "buka/pakai lagi dengan nonton rewarded ad" —
 * dipakai untuk fitur SEKALI-JALAN (mis. tombol aksi, bukan toggle
 * permanen) yang dibatasi kuota gratis per hari untuk pengguna non-member.
 *
 * PRINSIP UTAMA (mengikuti filosofi membership di app ini — lihat KDoc
 * [com.aether.x.ui.membership.MembershipViewModel]: "aplikasi TIDAK PERNAH
 * mengunci fitur di balik gerbang wajib"): guard ini TIDAK PERNAH membuat
 * fitur sama sekali tidak bisa dipakai. Yang dibatasi hanya SEBERAPA SERING
 * bisa dipakai GRATIS per hari — begitu kuota habis, jalan pintasnya adalah
 * nonton satu iklan buat dapat satu kredit pemakaian lagi, bukan menunggu
 * besok atau harus beli membership. Member (parameter `isMember` true di
 * semua fungsi publik kelas ini) selalu mendapat [RewardGateResult.Allowed]
 * tanpa iklan sama sekali, sesuai preferensi "member = no ads" produk ini.
 *
 * DESAIN "featureKey" GENERIK: satu instance [RewardGate] bisa dipakai
 * untuk BERAPA PUN fitur berbeda, cukup beri [featureKey] string unik yang
 * berbeda tiap fitur (mis. "kill_background_apps", "game_profile_instant_apply")
 * — kuotanya dilacak terpisah per featureKey (lihat [RewardQuotaState]),
 * jadi menambah titik reward-gate baru TIDAK PERNAH perlu kelas/DataStore
 * key baru, cukup panggil [checkAccess]/[watchAdForCredit] dengan featureKey
 * baru dari fitur yang mau dipasangi.
 *
 * CONTOH PEMAKAIAN (di titik fitur yang mau di-gate, mis. tombol Kill
 * Background Apps — lihat TweakViewModel). `isMember` di sini didapat dari
 * `MembershipViewModel.status.value == MembershipUiStatus.ACTIVE` (atau
 * StateFlow yang sama diamati langsung dari Compose kalau gate dipanggil
 * dari sana):
 * ```
 * when (rewardGate.checkAccess(featureKey = "kill_background_apps", isMember = membershipActive, freeUsesPerDay = 2)) {
 *     RewardGateResult.Allowed -> {
 *         rewardGate.consumeUse(featureKey = "kill_background_apps", isMember = membershipActive, freeUsesPerDay = 2)
 *         // ...jalankan aksi kill background apps seperti biasa...
 *     }
 *     RewardGateResult.RequiresAd -> {
 *         // ...tampilkan tombol "Tonton Iklan untuk Pakai Lagi", panggil watchAdForCredit saat ditekan...
 *     }
 * }
 * ```
 */
class RewardGate(
    private val preferences: AetherXPreferences,
    private val adManager: RewardedAdManager,
) {

    /** Hasil dari [watchAdForCredit] — dipakai UI untuk tahu apakah boleh lanjut jalankan aksi setelahnya. */
    sealed interface WatchAdResult {
        /** Iklan selesai ditonton penuh, satu kredit sudah ditambahkan — aksi boleh langsung dijalankan. */
        data object CreditGranted : WatchAdResult

        /** Pengguna menutup/skip iklan sebelum selesai — TIDAK ada kredit, aksi TIDAK boleh dijalankan. */
        data object Cancelled : WatchAdResult

        /** Tidak ada iklan siap ditampilkan saat ini (masih memuat / gagal load). */
        data object AdNotReady : WatchAdResult
    }

    /**
     * Cek apakah [featureKey] boleh dipakai SEKARANG tanpa iklan. TIDAK
     * mengubah/mengonsumsi kuota apa pun sendiri — panggilan ini murni
     * pengecekan (aman dipanggil berkali-kali, mis. tiap kali UI perlu tahu
     * status tombol). Setelah mendapat [RewardGateResult.Allowed] dan aksi
     * BENAR-BENAR dijalankan, pemanggil WAJIB memanggil [consumeUse] untuk
     * mengurangi kuota — dipisah sengaja dari fungsi ini supaya UI bisa
     * menampilkan status tombol (mis. "2x gratis tersisa hari ini") tanpa
     * mengonsumsi kuota hanya karena menggambar ulang layar.
     */
    suspend fun checkAccess(featureKey: String, isMember: Boolean, freeUsesPerDay: Int): RewardGateResult {
        if (isMember) return RewardGateResult.Allowed

        val state = preferences.getRewardQuota(featureKey, today())
        val hasFreeUseLeft = state.freeUsesToday < freeUsesPerDay
        val hasCreditLeft = state.extraCredits > 0
        return if (hasFreeUseLeft || hasCreditLeft) RewardGateResult.Allowed else RewardGateResult.RequiresAd
    }

    /**
     * Sisa kuota GRATIS (bukan kredit iklan) untuk [featureKey] hari ini —
     * murni untuk ditampilkan di UI (mis. "2x gratis tersisa"), tidak
     * dipakai untuk logika akses (lihat [checkAccess]).
     */
    suspend fun remainingFreeUses(featureKey: String, freeUsesPerDay: Int): Int {
        val state = preferences.getRewardQuota(featureKey, today())
        return (freeUsesPerDay - state.freeUsesToday).coerceAtLeast(0)
    }

    /**
     * Catat SATU pemakaian [featureKey] — WAJIB dipanggil tepat sekali
     * setiap kali aksi yang di-gate benar-benar dijalankan setelah
     * [checkAccess] mengembalikan [RewardGateResult.Allowed]. Member (
     * [isMember] true) tidak mencatat apa pun (tidak ada kuota untuk
     * member sama sekali). Mengutamakan MENGURANGI kredit ekstra dulu
     * (kalau ada) sebelum menambah hitungan kuota gratis — supaya kredit
     * hasil nonton iklan tidak "kedaluwarsa" begitu saja kalau kuota gratis
     * hari itu kebetulan belum habis saat kredit didapat.
     */
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

    /**
     * Tampilkan rewarded ad untuk [featureKey] dan, kalau ditonton sampai
     * selesai, tambahkan satu kredit ekstra. TIDAK menjalankan aksi apa pun
     * sendiri — pemanggil yang menerima [WatchAdResult.CreditGranted]
     * bertanggung jawab menjalankan aksinya sendiri lalu memanggil
     * [consumeUse] seperti biasa (kredit yang baru ditambahkan otomatis
     * akan dipakai oleh [consumeUse] karena logikanya memprioritaskan
     * kredit ekstra lebih dulu).
     */
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
        // Zona waktu device (bukan UTC) supaya "hari ini" cocok dengan
        // ekspektasi pengguna — reset kuota terjadi tengah malam waktu
        // lokal mereka, bukan waktu UTC yang bisa beda beberapa jam.
        val DATE_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
