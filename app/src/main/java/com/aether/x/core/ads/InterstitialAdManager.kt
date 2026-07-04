package com.aether.x.core.ads

import android.app.Activity

/** Hasil dari satu percobaan menampilkan interstitial ad, lihat [InterstitialAdManager.show]. */
sealed interface InterstitialAdResult {
    /** Iklan tampil (sampai selesai atau ditutup pengguna) tanpa error SDK. */
    data object Shown : InterstitialAdResult

    /** Tidak ada iklan siap ditampilkan saat ini (belum selesai load, atau gagal load sebelumnya). */
    data object NotReady : InterstitialAdResult

    /** Gagal menampilkan karena error lain (network, SDK belum init, dsb). */
    data class Failed(val reason: String) : InterstitialAdResult
}

/**
 * Abstraksi interstitial ad — SENGAJA tidak mengekspos tipe/API spesifik
 * Unity Ads ke pemanggil ([InterstitialAdGate]), dengan alasan yang sama
 * seperti [RewardedAdManager] (lihat KDoc di sana): gampang diganti/di-mock,
 * dan [NoOpInterstitialAdManager] sebagai default aman sebelum kredensial
 * dipasang.
 *
 * Berbeda dari [RewardedAdManager]: interstitial TIDAK PERNAH memberi
 * reward apa pun — pemanggil tidak perlu membedakan "selesai ditonton" vs
 * "di-skip", cukup tahu apakah iklan berhasil ditampilkan atau tidak, lalu
 * lanjutkan alur normal aplikasi di kedua kasus itu (lihat KDoc
 * [InterstitialAdGate]: interstitial TIDAK PERNAH memblokir aksi yang
 * sudah terjadi, hanya tampil SETELAHNYA sebagai transisi).
 */
interface InterstitialAdManager {

    /** Apakah ada interstitial ad yang sudah selesai dimuat dan siap ditampilkan sekarang. */
    val isReady: Boolean

    /**
     * Mulai memuat interstitial ad di background (non-blocking, tidak
     * menampilkan apa pun). Aman dipanggil berkali-kali — implementasi
     * wajib no-op kalau sudah ada iklan siap atau sedang dalam proses load.
     */
    fun preload()

    /**
     * Tampilkan interstitial ad yang sudah dimuat. WAJIB dipanggil dari
     * [activity] yang sedang foreground. [onResult] dipanggil TEPAT SEKALI
     * dengan hasil akhir, setelah iklan ditutup (baik ditonton penuh maupun
     * di-skip — keduanya [InterstitialAdResult.Shown], karena tidak ada
     * reward yang perlu dibedakan).
     */
    fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit)
}

/**
 * Implementasi "tidak ada iklan" — dipakai sebagai default aman selama
 * kredensial Unity Ads belum dipasang, dan cocok untuk build debug/testing.
 * SELALU melaporkan [InterstitialAdResult.NotReady].
 */
class NoOpInterstitialAdManager : InterstitialAdManager {
    override val isReady: Boolean = false
    override fun preload() = Unit
    override fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit) {
        onResult(InterstitialAdResult.NotReady)
    }
}
