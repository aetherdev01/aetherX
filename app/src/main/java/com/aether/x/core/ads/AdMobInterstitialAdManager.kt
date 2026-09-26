package com.aether.x.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aether.x.core.security.SecretStrings
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementasi [InterstitialAdManager] pakai Google AdMob — menggantikan
 * [UnityInterstitialAdManager] sebagai provider interstitial (lihat
 * `core/ads/README.md`). Pola retry/lifecycle sengaja dibuat mirip
 * [UnityInterstitialAdManager] supaya konsisten, walau API AdMob berbeda
 * (load pakai [Context], bukan singleton SDK-wide seperti Unity).
 */
class AdMobInterstitialAdManager(private val testMode: Boolean) : InterstitialAdManager {

    private companion object {
        const val TAG = "AdMobInterstitialAdManager"

        // AD_UNIT_ID disimpan terenkripsi (lihat SecretStrings.kt) — pola
        // sama dengan GAME_ID/PLACEMENT_ID di UnityInterstitialAdManager,
        // supaya tidak muncul plaintext di classes.dex. Payload digenerate
        // lewat tools/encode_secret.py dari:
        // "ca-app-pub-5043818314955328/8307695341"
        val AD_UNIT_ID: String by lazy {
            SecretStrings.reveal(
                "LKDEhCZKEbMkM7sqWO7jv1EyRZ+Ziu9JqB5UWyOzjbFuWJPTjeujomoIBWo1j8o8zm2XarxSonx+cHz70/sGI815"
            )
        }

        // Ad unit interstitial TEST resmi dari Google. Dipakai kalau
        // testMode = true supaya build debug tidak generate invalid traffic
        // di ad unit asli — PENTING selama ad unit di atas masih berstatus
        // "in review" di AdMob console (ad unit baru butuh masa peninjauan
        // sebelum mulai serve iklan asli; sebelum lolos, load() dari ad unit
        // asli bisa saja gagal/fill rate nol — itu NORMAL, bukan bug kode).
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        const val INITIAL_RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val MAX_RETRY_ATTEMPTS = 6
    }

    private val adUnitId: String get() = if (testMode) TEST_AD_UNIT_ID else AD_UNIT_ID

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob: Job? = null

    @Volatile
    private var consecutiveFailures = 0

    override val isReady: Boolean get() = interstitialAd != null

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext

        MobileAds.initialize(context) {
            initialized = true
            Log.d(TAG, "AdMob initialized (interstitial)")
            preload()
        }
    }

    override fun preload() {
        if (!initialized || interstitialAd != null) return

        retryJob?.cancel()
        retryJob = null

        requestLoad()
    }

    private fun requestLoad() {
        val context = appContext ?: return

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    consecutiveFailures = 0
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Gagal memuat interstitial ad: ${error.code} / ${error.message}")
                    scheduleRetry()
                }
            },
        )
    }

    private fun scheduleRetry() {
        // Sama seperti UnityInterstitialAdManager: retry tidak pernah
        // menyerah selama proses masih hidup — delay di-cap di
        // MAX_RETRY_DELAY_MILLIS, attempt count cuma dipakai buat backoff
        // eksponensial, bukan buat berhenti total.
        val attempt = consecutiveFailures.coerceAtMost(MAX_RETRY_ATTEMPTS)
        consecutiveFailures++

        val delayMillis = (INITIAL_RETRY_DELAY_MILLIS shl attempt)
            .coerceAtMost(MAX_RETRY_DELAY_MILLIS)

        retryJob?.cancel()
        retryJob = retryScope.launch {
            delay(delayMillis)

            if (interstitialAd == null) {
                requestLoad()
            }
        }
    }

    override fun show(activity: Activity, onResult: (InterstitialAdResult) -> Unit) {
        if (!initialized) {
            onResult(InterstitialAdResult.Failed("AdMob belum siap (init belum selesai)"))
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            onResult(InterstitialAdResult.NotReady)
            return
        }

        interstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onResult(InterstitialAdResult.Failed(error.message))
                preload()
            }

            override fun onAdDismissedFullScreenContent() {
                onResult(InterstitialAdResult.Shown)
                preload()
            }
        }

        ad.show(activity)
    }
}
