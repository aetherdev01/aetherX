package com.aether.x.core.ads

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Implementasi [RewardedAdManager] memakai Unity Ads (`com.unity3d.ads:unity-ads`).
 *
 * *** WAJIB DIISI SEBELUM DIPAKAI DI BUILD RELEASE ***
 * [GAME_ID] dan [PLACEMENT_ID] di bawah masih placeholder kosong. Isi
 * dengan nilai asli dari Unity Ads Dashboard (unity.com/products/unity-ads
 * -> Monetization -> project AetherX -> Ad Units) sebelum build release:
 * - GAME_ID: satu per platform (Android/iOS berbeda), bukan per placement.
 * - PLACEMENT_ID: id ad unit rewarded yang dibuat khusus untuk fitur ini
 *   (disarankan buat placement TERPISAH dari placement lain kalau nanti
 *   ada lebih dari satu titik reward-gate, supaya statistik fill-rate/eCPM
 *   tiap fitur bisa dipantau terpisah di dashboard Unity Ads).
 *
 * Dependency Gradle yang perlu ditambahkan (SUDAH ditambahkan di
 * `app/build.gradle.kts` via `implementation(libs.unity.ads)` — lihat
 * `gradle/libs.versions.toml` entri `unityAds`):
 * ```
 * // app/build.gradle(.kts)
 * implementation("com.unity3d.ads:unity-ads:4.+")
 * ```
 *
 * Dipakai lewat [RewardedAdManager] (interface), BUKAN dirujuk langsung
 * sebagai `UnityRewardedAdManager` di tempat lain — lihat KDoc interface
 * untuk alasannya (gampang diganti/di-mock).
 */
class UnityRewardedAdManager(private val testMode: Boolean) : RewardedAdManager {

    private companion object {
        const val TAG = "UnityRewardedAdManager"

        // TODO: isi dengan Game ID Android asli dari Unity Ads Dashboard sebelum rilis.
        const val GAME_ID = ""

        // TODO: isi dengan Placement ID rewarded ad asli dari Unity Ads Dashboard sebelum rilis.
        const val PLACEMENT_ID = ""
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    override val isReady: Boolean get() = loaded

    /**
     * WAJIB dipanggil sekali dengan instance [Activity] yang sedang
     * foreground — lihat [com.aether.x.MainActivity.onCreate], titik
     * PERTAMA di app ini yang punya Activity sungguhan (BUKAN dari
     * [com.aether.x.AetherXApp.onCreate]: Application bukan Activity, dua
     * tipe berbeda yang tidak bisa saling menggantikan di parameter ini —
     * ini persis penyebab error compile "Argument type mismatch: actual
     * type is 'AetherXApp', but 'Activity' was expected" kalau dipanggil
     * dari sana). Aman dipanggil berkali-kali — no-op kalau sudah pernah
     * berhasil init sebelumnya.
     */
    fun initialize(activity: Activity) {
        if (initialized || GAME_ID.isBlank()) {
            if (GAME_ID.isBlank()) {
                Log.w(TAG, "GAME_ID belum diisi — lihat TODO di UnityRewardedAdManager. Guard reward-gate akan selalu skip.")
            }
            return
        }
        UnityAds.initialize(activity, GAME_ID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                initialized = true
                Log.d(TAG, "Unity Ads initialized")
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
                Log.w(TAG, "Gagal memuat rewarded ad: $error / $message")
            }
        })
    }

    override fun show(activity: Activity, onResult: (RewardedAdResult) -> Unit) {
        if (!initialized) {
            onResult(RewardedAdResult.Failed("Unity Ads belum siap (init belum selesai)"))
            return
        }
        if (!loaded) {
            onResult(RewardedAdResult.NotReady)
            return
        }

        // Tandai belum siap SEBELUM show — mencegah pemanggil lain memanggil
        // show() lagi terhadap instance iklan yang sama sebelum ini selesai.
        loaded = false

        UnityAds.show(activity, PLACEMENT_ID, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?,
            ) {
                onResult(RewardedAdResult.Failed(message ?: error?.name.orEmpty()))
                preload() // siapkan iklan berikutnya untuk percobaan selanjutnya.
            }

            override fun onUnityAdsShowStart(placementId: String?) = Unit

            override fun onUnityAdsShowClick(placementId: String?) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?,
            ) {
                // HANYA state COMPLETED yang layak diberi reward — SKIPPED
                // berarti pengguna menutup/skip sebelum iklan selesai.
                val result = if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    RewardedAdResult.Rewarded
                } else {
                    RewardedAdResult.Cancelled
                }
                onResult(result)
                preload() // siapkan iklan berikutnya untuk percobaan selanjutnya.
            }
        })
    }
}
