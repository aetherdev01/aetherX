package com.aether.x

import android.app.Application
import com.aether.x.core.ads.AdMobInterstitialAdManager
import com.aether.x.core.ads.FallbackInterstitialAdManager
import com.aether.x.core.ads.UnityInterstitialAdManager
import com.aether.x.core.ads.InterstitialAdGate
import com.aether.x.core.ads.InterstitialAdManager
import com.aether.x.core.ads.RewardedAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.FcmTokenRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AetherXApp : Application() {

    companion object {
        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }

        val rewardedAdManager: RewardedAdManager by lazy {
            UnityRewardedAdManager(testMode = BuildConfig.DEBUG)
        }

        // Interstitial memakai AdMob sebagai provider utama dan Unity Ads
        // sebagai fallback. Keduanya dipreload sejak aplikasi dibuka.
        val interstitialAdManager: InterstitialAdManager by lazy {
            FallbackInterstitialAdManager(
                adMob = AdMobInterstitialAdManager(testMode = BuildConfig.DEBUG),
                unity = UnityInterstitialAdManager(testMode = BuildConfig.DEBUG),
            )
        }

        val interstitialAdGate: InterstitialAdGate by lazy {
            InterstitialAdGate(interstitialAdManager)
        }

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        PrivilegeManager.init(this)

        FcmTokenRepository.subscribeToDefaultTopics()
        appScope.launch {
            FcmTokenRepository.syncTokenToFirestore(this@AetherXApp)
        }
    }
}
