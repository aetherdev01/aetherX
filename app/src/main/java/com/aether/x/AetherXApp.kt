package com.aether.x

import android.app.Application
import com.aether.x.core.ads.AdMobInterstitialAdManager
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

        // v3.5: interstitial pindah dari Unity Ads ke AdMob — Unity tetap
        // dipakai untuk rewardedAdManager di atas, tidak dihapus.
        val interstitialAdManager: InterstitialAdManager by lazy {
            AdMobInterstitialAdManager(testMode = BuildConfig.DEBUG)
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
