package com.aether.x

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.aether.x.ui.components.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aether.x.BuildConfig
import com.aether.x.core.ads.FallbackInterstitialAdManager
import com.aether.x.core.ads.UnityRewardedAdManager
import com.aether.x.core.monitor.GameProfileMonitorService
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.data.AetherXPreferences
import com.aether.x.data.AppPreferences
import com.aether.x.data.DarkModePref
import com.aether.x.ui.main.MainScreen
import com.aether.x.ui.maintenance.MaintenanceGate
import com.aether.x.ui.navigation.AetherXRoutes
import com.aether.x.ui.onboarding.OnboardingFlow
import com.aether.x.ui.onboarding.PermissionSetupScreen
import com.aether.x.ui.onboarding.SplashScreen
import com.aether.x.ui.theme.AetherXTheme
import com.aether.x.ui.update.UpdateGate
import kotlinx.coroutines.flow.Flow
import com.aether.x.core.locale.LocaleManager
import android.content.Context

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        (AetherXApp.rewardedAdManager as? UnityRewardedAdManager)?.initialize(this)
        (AetherXApp.interstitialAdManager as? FallbackInterstitialAdManager)?.initialize(this, this)

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
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (prefsValue.darkModePref) {
                    DarkModePref.SYSTEM -> systemDark
                    DarkModePref.LIGHT -> false
                    DarkModePref.DARK -> true
                }

                // Ikon status bar & navigation bar mengikuti tema APP (bukan tema
                // sistem), supaya tetap terbaca saat tema app dan HP berbeda.
                SideEffect {
                    val barStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                }

                AetherXTheme(
                    darkTheme = darkTheme,
                    accent = prefsValue.accentPreset,
                    customAccentArgb = prefsValue.customAccentArgb,
                    blurStrength = prefsValue.blurStrength,
                    liquidGlassLevel = prefsValue.liquidGlassLevel,
                    hapticsEnabled = prefsValue.hapticsEnabled,
                    reduceMotionEnabled = prefsValue.reduceMotionEnabled,
                    glassLensEnabled = prefsValue.glassLensEnabled,
                    glassLensStrength = prefsValue.glassLensStrength,
                ) {
                    AetherXRoot(
                        onboardingCompleted = prefsValue.onboardingCompleted,
                        preferences = preferences,
                        appPrefs = prefsValue,
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
    appPrefs: AppPreferences,
) {
    val navController = rememberNavController()

    val startDestination = AetherXRoutes.SPLASH_MAIN

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val privilegeStatus by PrivilegeManager.status.collectAsStateWithLifecycle()
    LaunchedEffect(privilegeStatus.rootGranted) {
        // Butuh berjalan meski belum ada profil tweak: service yang sama juga
        // merekam sesi game otomatis. Sampling metrik dibuat berkala dan ringan.
        val isRoot = privilegeStatus.rootGranted
        if (isRoot) {
            GameProfileMonitorService.start(context)
        } else {
            GameProfileMonitorService.stop(context)
        }
    }

    // v3.8 — HazeState root: seluruh UI app adalah SUMBER blur; dialog (jendela
    // terpisah) mem-blur isi layar di belakangnya lewat state ini.
    val rootHaze = rememberHazeState(blurEnabled = true)

    CompositionLocalProvider(LocalHazeState provides rootHaze) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .hazeSource(rootHaze),
    ) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AetherXRoutes.SPLASH_MAIN) {
            // Splash memakai tema aplikasi yang sudah dipilih user (terang/gelap + aksen).
            SplashScreen(
                onSplashFinished = {
                    val destination = if (onboardingCompleted) AetherXRoutes.MAIN else AetherXRoutes.PERMISSION_ONBOARDING
                    navController.navigate(destination) {
                        popUpTo(AetherXRoutes.SPLASH_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(AetherXRoutes.PERMISSION_ONBOARDING) {
            // Halaman 1 = Setup izin, halaman 2 = layer geser-ke-atas (chromatic aberration)
            // dengan Dashboard di belakangnya. Dashboard dikomposisi di dalam alur ini supaya
            // tidak ada kedipan saat layer terbuka — jadi tidak perlu navigate ke MAIN lagi.
            OnboardingFlow(
                onSetupDone = {
                    preferences.setOnboardingCompleted(true)
                    // Onboarding baru = instalasi baru, bukan update dari versi lama —
                    // jangan tampilkan sheet "Apa yang Baru" ke user yang belum pernah
                    // pakai versi sebelumnya (lihat WhatsNewViewModel). Disimpan SEBELUM
                    // Dashboard dikomposisi di belakang layer.
                    preferences.setLastSeenChangelogVersionCode(BuildConfig.VERSION_CODE)
                },
                dashboard = {
                    MainScreen(
                        onManageAccess = { navController.navigate(AetherXRoutes.MANAGE_ACCESS) },
                    )
                },
            )
        }
        composable(AetherXRoutes.MAIN) {
            MainScreen(
                onManageAccess = { navController.navigate(AetherXRoutes.MANAGE_ACCESS) },
            )
        }
        composable(AetherXRoutes.MANAGE_ACCESS) {
            PermissionSetupScreen(
                onContinue = { navController.popBackStack() },
                requireAccessToContinue = false,
            )
        }
    }
    }

    MaintenanceGate()

    UpdateGate()
    }
}
