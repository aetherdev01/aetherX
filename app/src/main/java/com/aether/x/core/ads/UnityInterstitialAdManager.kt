package com.aether.x.core.ads

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Implementasi [InterstitialAdManager] memakai Unity Ads. Lihat KDoc
 * [UnityRewardedAdManager] untuk penjelasan lengkap kenapa [initialize]
 * butuh [Activity] (bukan dipanggil dari [com.aether.x.AetherXApp]) — pola
 * dan alasannya identik di sini.
 *
 * SDK Unity Ads diinisialisasi SEKALI SAJA per proses (lihat
 * [UnityAds.initialize] — aman dipanggil berkali-kali dari SDK-nya sendiri,
 * tapi kelas ini juga menjaga idempoten lewat [initialized]). Kalau
 * [UnityRewardedAdManager.initialize] SUDAH dipanggil lebih dulu di
 * [com.aether.x.MainActivity] dengan GAME_ID yang sama, panggilan
 * [initialize] di sini akan tetap aman (Unity Ads SDK sendiri idempoten per
 * GAME_ID) — kelas ini tidak bergantung pada urutan pemanggilan terhadap
 * [UnityRewardedAdManager].
 */
class UnityInterstitialAdManager(private val testMode: Boolean) : InterstitialAdManager {

    private companion object {
        const val TAG = "UnityInterstitialAdManager"

        // Game ID Android dari Unity Ads Dashboard (project AetherX) — sama
        // dengan UnityRewardedAdManager.GAME_ID, karena satu Game ID berlaku
        // untuk seluruh placement di platform yang sama, bukan per placement.
        const val GAME_ID = "6091240"

        // Placement ID ad unit interstitial dari Unity Ads Dashboard.
        const val PLACEMENT_ID = "Interstitial_Android"
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    override val isReady: Boolean get() = loaded

    /**
     * WAJIB dipanggil sekali dengan instance [Activity] yang sedang
     * foreground — lihat [com.aether.x.MainActivity.onCreate]. Aman
     * dipanggil berkali-kali — no-op kalau sudah pernah berhasil init
     * sebelumnya.
     */
    fun initialize(activity: Activity) {
        if (initialized || GAME_ID.isBlank()) {
            if (GAME_ID.isBlank()) {
                Log.w(TAG, "GAME_ID belum diisi — interstitial akan selalu skip.")
            }
            return
        }
        UnityAds.initialize(activity, GAME_ID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                initialized = true
                Log.d(TAG, "Unity Ads initialized (interstitial)")
                preload()
            }

            override fun onInitializationFailed(
                error: UnityAds.UnityAdsInitializationError?,
                message: String?,
            ) {
                Log.e(TAG, "Unity Ads init gagal: $error / $message")
            }
        })
    }

    override fun preload() {
        if (!initialized || GAME_ID.isBlank() || PLACEMENT_ID.isBlank() || loaded) return
        UnityAds.load(PLACEMENT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                loaded = true
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?,
            ) {
                loaded = false
                Log.w(TAG, "Gagal memuat interstitial ad: $error / $message")
            }
        })
    }

    override fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit) {
        if (!initialized) {
            onResult(InterstitialAdResult.Failed("Unity Ads belum siap (init belum selesai)"))
            return
        }
        if (!loaded) {
            onResult(InterstitialAdResult.NotReady)
            return
        }

        // Tandai belum siap SEBELUM show, dengan alasan sama seperti
        // UnityRewardedAdManager.show — mencegah show() dobel terhadap
        // instance iklan yang sama sebelum selesai.
        loaded = false

        UnityAds.show(activity, PLACEMENT_ID, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?,
            ) {
                onResult(InterstitialAdResult.Failed(message ?: error?.name.orEmpty()))
                preload()
            }

            override fun onUnityAdsShowStart(placementId: String?) = Unit

            override fun onUnityAdsShowClick(placementId: String?) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?,
            ) {
                // Tidak ada reward untuk interstitial — COMPLETED maupun
                // SKIPPED sama-sama dianggap "berhasil ditampilkan".
                onResult(InterstitialAdResult.Shown)
                preload()
            }
        })
    }
}
