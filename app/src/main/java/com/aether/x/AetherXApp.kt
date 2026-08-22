package com.aether.x

import android.app.Application
import android.os.Process
import com.aether.x.core.ads.InterstitialAdGate
import com.aether.x.core.ads.InterstitialAdManager
import com.aether.x.core.ads.RewardedAdManager
import com.aether.x.core.ads.UnityInterstitialAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.AppCheckInitializer
import com.aether.x.core.security.NativeIntegrityGuard
import com.aether.x.core.security.SignatureGuard
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

        val interstitialAdManager: InterstitialAdManager by lazy {
            UnityInterstitialAdManager(testMode = BuildConfig.DEBUG)
        }

        val interstitialAdGate: InterstitialAdGate by lazy {
            InterstitialAdGate(interstitialAdManager)
        }

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            SignatureGuard.verifyOrDie(this)
        } catch (e: UnsatisfiedLinkError) {
            Process.killProcess(Process.myPid())
            return
        }

        try {
            NativeIntegrityGuard.verifyOrDie(this)
        } catch (e: UnsatisfiedLinkError) {
            Process.killProcess(Process.myPid())
            return
        }

        AppCheckInitializer.init(this)
        PrivilegeManager.init(this)

        FcmTokenRepository.subscribeToDefaultTopics()
        appScope.launch {
            FcmTokenRepository.syncTokenToFirestore(this@AetherXApp)
        }
    }
}
