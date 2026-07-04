package com.aether.x.core.ads

import android.app.Activity

/** Hasil dari satu percobaan menampilkan rewarded ad, lihat [RewardedAdManager.show]. */
sealed interface RewardedAdResult {
    /** Iklan tampil sampai selesai DAN reward layak diberikan (lihat catatan di [show]). */
    data object Rewarded : RewardedAdResult

    /** Pengguna menutup iklan sebelum selesai / sebelum reward callback terpicu — JANGAN beri reward. */
    data object Cancelled : RewardedAdResult

    /** Tidak ada iklan siap ditampilkan saat ini (belum selesai load, atau gagal load sebelumnya). */
    data object NotReady : RewardedAdResult

    /** Gagal menampilkan karena error lain (network, SDK belum init, dsb). */
    data class Failed(val reason: String) : RewardedAdResult
}

/**
 * Abstraksi rewarded ad — SENGAJA tidak mengekspos tipe/API spesifik Unity
 * Ads (mis. `UnityAds.Listener`) ke pemanggil ([RewardGate]) supaya:
 * - [RewardGate] dan UI (tombol "Tonton Iklan untuk...") tidak perlu tahu
 *   atau berubah kalau suatu saat jaringan iklan diganti/ditambah (mis.
 *   AdMob sebagai mediasi/fallback kalau Unity Ads gagal load).
 * - Gampang dibuat implementasi palsu ([NoOpRewardedAdManager]) untuk build
 *   debug/testing tanpa perlu App ID/Placement ID Unity Ads asli.
 *
 * Siklus pemakaian yang diharapkan pemanggil:
 * 1. Panggil [preload] sedini mungkin (mis. saat layar yang punya tombol
 *    reward pertama kali dibuka) supaya iklan SUDAH siap saat pengguna
 *    benar-benar menekan tombolnya — [show] yang harus menunggu load dari
 *    nol akan terasa lambat/janggal di tengah alur pakai fitur.
 * 2. Amati [isReady] untuk memutuskan apakah tombol "Tonton Iklan" boleh
 *    aktif atau perlu menunjukkan status memuat.
 * 3. Panggil [show] saat pengguna menekan tombol tsb. HANYA beri reward
 *    (jalankan aksi yang di-gate) kalau hasilnya persis [RewardedAdResult.Rewarded]
 *    — semua hasil lain berarti TIDAK BOLEH memberi reward.
 */
interface RewardedAdManager {

    /** Apakah ada rewarded ad yang sudah selesai dimuat dan siap ditampilkan sekarang. */
    val isReady: Boolean

    /**
     * Mulai memuat rewarded ad di background (non-blocking, tidak
     * menampilkan apa pun). Aman dipanggil berkali-kali — implementasi
     * wajib no-op kalau sudah ada iklan siap atau sedang dalam proses load.
     */
    fun preload()

    /**
     * Tampilkan rewarded ad yang sudah dimuat. WAJIB dipanggil dari
     * [activity] yang sedang foreground (persyaratan umum SDK iklan).
     * [onResult] dipanggil TEPAT SEKALI dengan hasil akhir — implementasi
     * bertanggung jawab memetakan callback SDK asli (mis.
     * `onUnityAdsShowComplete` dengan `UnityAdsShowCompletionState.COMPLETED`
     * vs `SKIPPED`) ke salah satu [RewardedAdResult] di atas dengan benar,
     * supaya pemanggil tidak pernah perlu tahu detail SDK.
     */
    fun show(activity: Activity, onResult: (RewardedAdResult) -> Unit)
}

/**
 * Implementasi "tidak ada iklan" — dipakai sebagai default aman selama
 * kredensial Unity Ads (Game ID/Placement ID) belum dipasang, dan cocok
 * untuk build debug/testing supaya reward-gate bisa diuji tanpa perlu
 * inventory iklan sungguhan. SELALU melaporkan [RewardedAdResult.NotReady]
 * — mendorong pemanggil menampilkan state "iklan belum tersedia" alih-alih
 * diam-diam menganggap reward berhasil.
 */
class NoOpRewardedAdManager : RewardedAdManager {
    override val isReady: Boolean = false
    override fun preload() = Unit
    override fun show(activity: Activity, onResult: (RewardedAdResult) -> Unit) {
        onResult(RewardedAdResult.NotReady)
    }
}
