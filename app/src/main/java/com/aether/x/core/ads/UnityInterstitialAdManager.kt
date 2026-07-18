package com.aether.x.core.ads

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

        // FITUR BARU (retry otomatis — lihat perintah rework "iklan jarang
        // muncul, preload perlu retry"): SEBELUM ini, sekali
        // onUnityAdsFailedToLoad terpanggil (mis. network blip sesaat,
        // timeout ke server Unity), [loaded] cuma jadi false dan TIDAK ADA
        // percobaan ulang otomatis — iklan baru dicoba lagi kalau ada
        // trigger LAIN yang kebetulan memanggil preload() (habis show()
        // sukses/gagal, atau initialize() pertama kali). Kalau gagal load
        // terjadi di luar momen itu, slot iklan bisa "kosong" berkepanjangan
        // walau jaringan sebenarnya sudah pulih dalam hitungan detik.
        //
        // Retry sekarang exponential backoff (2s, 4s, 8s, ... dibatasi
        // [MAX_RETRY_DELAY_MILLIS]) dibatasi [MAX_RETRY_ATTEMPTS] kali
        // berturut-turut SEBELUM menyerah menunggu trigger eksternal lagi
        // (bukan retry tanpa batas — kalau device benar-benar offline total
        // atau memang tidak ada inventory iklan sama sekali, retry berulang
        // tanpa henti cuma buang baterai/data tanpa hasil). Backoff exponential
        // (bukan interval tetap) supaya tidak membebani server Unity Ads
        // dengan burst request kalau gangguannya memang di sisi mereka.
        const val INITIAL_RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val MAX_RETRY_ATTEMPTS = 6
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var loaded = false

    // Scope KHUSUS retry — SupervisorJob supaya satu percobaan retry gagal
    // (exception tak terduga) tidak mematikan kemampuan retry berikutnya.
    // Dispatchers.Main karena UnityAds.load() aman dipanggil dari main
    // thread (SDK-nya sendiri melakukan network I/O di background).
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retryJob: Job? = null

    @Volatile
    private var consecutiveFailures = 0

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

        // Trigger EKSTERNAL (bukan retry job internal) memanggil preload()
        // langsung — batalkan retry job yang mungkin masih menunggu delay
        // supaya tidak terjadi DUA panggilan UnityAds.load() bertumpuk
        // (satu dari sini, satu lagi begitu delay retry sebelumnya habis).
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
                Log.w(TAG, "Gagal memuat interstitial ad: $error / $message")
                scheduleRetry()
            }
        })
    }

    /**
     * Jadwalkan percobaan [requestLoad] berikutnya dengan exponential
     * backoff — lihat catatan [MAX_RETRY_ATTEMPTS] di companion object
     * kenapa ini dibatasi (bukan retry tanpa batas).
     */
    private fun scheduleRetry() {
        if (consecutiveFailures >= MAX_RETRY_ATTEMPTS) {
            Log.w(
                TAG,
                "Interstitial ad gagal load $MAX_RETRY_ATTEMPTS kali berturut-turut — " +
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
            // loaded bisa saja sudah true di sini kalau trigger eksternal
            // lain (mis. show() sukses dari SESI iklan LAIN yang kebetulan
            // load lebih cepat) sempat berhasil duluan sebelum delay retry
            // ini habis — cek ulang supaya tidak double-load tanpa guna.
            if (!loaded) {
                requestLoad()
            }
        }
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
