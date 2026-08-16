package com.aether.x.core.ads

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.aether.x.core.security.SecretStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnityRewardedAdManager(private val testMode: Boolean) : RewardedAdManager {

    private companion object {
        const val TAG = "UnityRewardedAdManager"

        // GAME_ID/PLACEMENT_ID disimpan terenkripsi (lihat SecretStrings.kt)
        // supaya tidak muncul plaintext di classes.dex — bukan `const val`
        // lagi karena nilainya baru ada setelah dekripsi runtime (lazy,
        // sekali per proses). Payload digenerate lewat tools/encode_secret.py.
        val GAME_ID: String by lazy {
            SecretStrings.reveal("BloZVjORRRZKCCSHVQsXdIKajPEPAjnhYWS8i7FmY3ihhG4=")
        }

        val PLACEMENT_ID: String by lazy {
            SecretStrings.reveal("95y4ip+mnmdWhDtW9DzNb+Ptg9wjq1t2ZEP7GJ3KoSpjCawD8KHTXtGbK1A=")
        }

        const val INITIAL_RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val MAX_RETRY_ATTEMPTS = 6
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob: Job? = null

    @Volatile
    private var consecutiveFailures = 0

    override val isReady: Boolean get() = loaded

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

        retryJob?.cancel()
        retryJob = null

        requestLoad()
    }

    private fun requestLoad() {
        UnityAds.load(PLACEMENT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                loaded = true
                consecutiveFailures = 0
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?,
            ) {
                loaded = false
                Log.w(TAG, "Gagal memuat rewarded ad: $error / $message")
                scheduleRetry()
            }
        })
    }

    private fun scheduleRetry() {
        if (consecutiveFailures >= MAX_RETRY_ATTEMPTS) {
            Log.w(
                TAG,
                "Rewarded ad gagal load $MAX_RETRY_ATTEMPTS kali berturut-turut — " +
                    "berhenti retry otomatis, menunggu trigger eksternal (mis. show() " +
                    "berikutnya) untuk mencoba lagi.",
            )
            return
        }
        val attempt = consecutiveFailures
        consecutiveFailures++

        val delayMillis = (INITIAL_RETRY_DELAY_MILLIS shl attempt)
            .coerceAtMost(MAX_RETRY_DELAY_MILLIS)

        retryJob?.cancel()
        retryJob = retryScope.launch {
            delay(delayMillis)
            if (!loaded) {
                requestLoad()
            }
        }
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

        loaded = false

        UnityAds.show(activity, PLACEMENT_ID, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?,
            ) {
                onResult(RewardedAdResult.Failed(message ?: error?.name.orEmpty()))
                preload()
            }

            override fun onUnityAdsShowStart(placementId: String?) = Unit

            override fun onUnityAdsShowClick(placementId: String?) = Unit

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?,
            ) {

                val result = if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    RewardedAdResult.Rewarded
                } else {
                    RewardedAdResult.Cancelled
                }
                onResult(result)
                preload()
            }
        })
    }
}
