package com.aether.x

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aether.x.core.ads.UnityInterstitialAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.monitor.GameProfileMonitorService
import com.aether.x.core.permission.PrivilegeBackend
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.core.security.SignatureGuard
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppPreferences
import com.aether.x.data.DarkModePref
import com.aether.x.ui.main.MainScreen
import com.aether.x.ui.maintenance.MaintenanceGate
import com.aether.x.ui.navigation.AetherXRoutes
import com.aether.x.ui.onboarding.PermissionSetupScreen
import com.aether.x.ui.onboarding.SplashScreen
import com.aether.x.ui.theme.AetherXTheme
import com.aether.x.ui.update.UpdateGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SignatureGuard.verifyOrDieAgain(this)

        (AetherXApp.rewardedAdManager as? UnityRewardedAdManager)?.initialize(this)
        (AetherXApp.interstitialAdManager as? UnityInterstitialAdManager)?.initialize(this)

        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        setContent {
            val preferences = remember { AetherXPreferences(applicationContext) }
            val nullablePrefsFlow: Flow<AppPreferences?> = preferences.preferences
            val appPrefs by nullablePrefsFlow.collectAsStateWithLifecycle(initialValue = null)

            LaunchedEffect(appPrefs) {
                if (appPrefs != null) keepSplashScreen = false
            }

            appPrefs?.let { prefsValue ->
                val darkTheme = when (prefsValue.darkModePref) {
                    DarkModePref.SYSTEM -> isSystemInDarkTheme()
                    DarkModePref.LIGHT -> false
                    DarkModePref.DARK -> true
                }
                AetherXTheme(
                    darkTheme = darkTheme,
                ) {
                    AetherXRoot(
                        onboardingCompleted = prefsValue.onboardingCompleted,
                        preferences = preferences,
                    )
                }
            }
        }
    }
}

@Composable
private fun AetherXRoot(
    onboardingCompleted: Boolean,
    preferences: AetherXPreferences,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val startDestination = AetherXRoutes.SPLASH_MAIN

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshAll()

                com.aether.x.core.shizuku.WirelessDebuggingMonitor.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    val appPrefsForService by preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(privilegeStatus.activeBackend, appPrefsForService?.gameProfiles?.keys) {
        val hasProfiles = appPrefsForService?.gameProfiles?.isNotEmpty() == true
        val isRoot = privilegeStatus.activeBackend == PrivilegeBackend.ROOT
        if (hasProfiles && isRoot) {
            GameProfileMonitorService.start(context)
        } else {
            GameProfileMonitorService.stop(context)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AetherXRoutes.SPLASH_MAIN) {

            SplashScreen(
                onDone = {
                    val destination = if (onboardingCompleted) AetherXRoutes.MAIN else AetherXRoutes.PERMISSION_ONBOARDING
                    navController.navigate(destination) {
                        popUpTo(AetherXRoutes.SPLASH_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(AetherXRoutes.PERMISSION_ONBOARDING) {
            PermissionSetupScreen(
                onContinue = {
                    scope.launch { preferences.setOnboardingCompleted(true) }

                    navController.navigate(AetherXRoutes.MAIN) {
                        popUpTo(AetherXRoutes.PERMISSION_ONBOARDING) { inclusive = true }
                    }
                },
                requireAccessToContinue = true,
            )
        }
        composable(AetherXRoutes.MAIN) {
            MainScreen(
                onManageAccess = { navController.navigate(AetherXRoutes.MANAGE_ACCESS) },
                onNavigateToGameBooster = { navController.navigate(AetherXRoutes.GAME_BOOSTER) },
            )
        }
        composable(AetherXRoutes.MANAGE_ACCESS) {
            PermissionSetupScreen(
                onContinue = { navController.popBackStack() },
                requireAccessToContinue = false,
            )
        }
        composable(AetherXRoutes.GAME_BOOSTER) {
            com.aether.x.ui.booster.GameBoosterScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }

    MaintenanceGate()

    UpdateGate()
}
