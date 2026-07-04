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
 * [GAME_ID] dan [PLACEMENT_ID] di bawah sudah diisi dari Unity Ads
 * Dashboard (unity.com/products/unity-ads -> Monetization -> project
 * AetherX -> Ad Units):
 * - GAME_ID: Game ID Android project ini (satu per platform, bukan per
 *   placement — JANGAN keliru dengan Game ID iOS kalau project punya dua).
 * - PLACEMENT_ID: id ad unit rewarded ("Rewarded_Android"). Kalau nanti
 *   ada titik reward-gate lain yang mau dipantau fill-rate/eCPM-nya
 *   terpisah, buat placement baru di dashboard dan sesuaikan konstanta ini
 *   (atau naikkan jadi parameter constructor kalau perlu lebih dari satu
 *   rewarded placement aktif bersamaan).
 *
 * Kalau error muncul saat init/load berupa sesuatu seperti "game id belum
 * didefinisikan" / ad unit tidak ditemukan padahal GAME_ID & PLACEMENT_ID
 * di atas sudah benar, itu HAMPIR PASTI bukan bug kode — cek di Unity Ads
 * Dashboard: (1) GAME_ID ini benar Game ID *Android* project (bukan iOS),
 * (2) ad unit "Rewarded_Android" statusnya "Live" (bukan draft), (3)
 * package name Android di dashboard cocok dengan applicationId app ini,
 * (4) kalau project/ad unit baru dibuat, tunggu propagasi (bisa sampai
 * beberapa jam) sebelum dicoba lagi.
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

        // Game ID Android dari Unity Ads Dashboard (project AetherX).
        const val GAME_ID = "6091240"

        // Placement ID ad unit rewarded dari Unity Ads Dashboard.
        const val PLACEMENT_ID = "Rewarded_Android"
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
