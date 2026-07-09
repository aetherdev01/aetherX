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

        // Titik verifikasi signature KEDUA yang independen dari yang di
        // AetherXApp.onCreate (lihat SignatureGuard.kt untuk kenapa). Kalau
        // seseorang berhasil melewati/nge-nop-kan titik pertama tapi tidak
        // sadar ada titik kedua di sini, app tetap force-close begitu
        // Activity ini dibuat.
        SignatureGuard.verifyOrDieAgain(this)

        // Inisialisasi SDK rewarded & interstitial ads di sini (bukan di
        // AetherXApp) karena UnityRewardedAdManager.initialize() dan
        // UnityInterstitialAdManager.initialize() butuh parameter Activity,
        // dan MainActivity adalah titik PERTAMA di app ini yang punya
        // instance Activity sungguhan — Application (AetherXApp) tidak
        // punya. Aman dipanggil di setiap onCreate MainActivity (mis.
        // setelah rotasi konfigurasi/re-create) karena initialize() sendiri
        // sudah no-op kalau sebelumnya sudah pernah berhasil (lihat
        // implementasinya). Bukan hal kritis-keamanan seperti SignatureGuard
        // di atas, jadi aman ditaruh setelahnya, sebelum UI dirender.
        (AetherXApp.rewardedAdManager as? UnityRewardedAdManager)?.initialize(this)
        (AetherXApp.interstitialAdManager as? UnityInterstitialAdManager)?.initialize(this)

        // Splash tetap tampil sampai status onboarding selesai dibaca dari DataStore,
        // supaya tidak ada "flash" layar kosong sebelum tujuan navigasi ditentukan.
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
    // Lisensi TIDAK LAGI jadi gerbang wajib di sini — statusnya dicek dan
    // diaktivasi dari tab Membership tersendiri (lihat MembershipViewModel),
    // semua fitur
    // tetap terbuka sebelum/tanpa lisensi aktif.
    //
    // REWORK (lihat perintah rework — "kenapa splash screen yang ada logo
    // dan loading nya hanya saat awal setup, setelah setup splash screen
    // nya ga muncul"): startDestination SEKARANG SELALU [AetherXRoutes.SPLASH_MAIN]
    // di SETIAP cold start, terlepas dari status [onboardingCompleted] —
    // sebelumnya splash HANYA ada di alur onboarding awal (jadi hanya
    // pernah dilihat sekali seumur hidup app, saat setup awal). SplashScreen
    // composable sendiri TIDAK PERNAH mengubah status onboarding (lihat
    // KDoc SplashScreen) — ia murni loading singkat (refresh status
    // privilese + resolve user ID), jadi aman dipanggil berulang setiap
    // kali app dibuka. Tujuan SETELAH splash selesai (onDone) ditentukan
    // dari [onboardingCompleted]: ke [AetherXRoutes.PERMISSION_ONBOARDING]
    // kalau belum, atau langsung [AetherXRoutes.MAIN] kalau sudah.
    val startDestination = AetherXRoutes.SPLASH_MAIN

    // Observer ON_RESUME ini melengkapi (BUKAN menggantikan) refresh yang
    // sudah dilakukan SplashScreen di cold start: SplashScreen hanya
    // berjalan sekali per proses app (saat NavHost pertama kali menampilkan
    // SPLASH_MAIN), sedangkan observer ini menangani kasus app di-RESUME
    // dari background (mis. pengguna sempat pindah ke app lain lalu kembali)
    // tanpa proses AetherX ikut mati — status privilese tetap perlu
    // di-refresh di titik itu juga, di halaman manapun pengguna sedang
    // berada, supaya tidak "basi" (mis. root terlihat hilang padahal masih
    // diizinkan).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PrivilegeManager.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto start/stop GameProfileMonitorService: service ini hanya berguna
    // (dan hanya boleh berjalan — lihat perintah pengguna: "khusus root
    // saja") kalau ADA minimal satu Game Profile tersimpan DAN backend
    // privilese aktif saat ini adalah Root. Direaksikan di sini (app-wide,
    // bukan di layar Game Profile saja) supaya tweak profil tetap
    // aktif/terpantau walau pengguna sedang membuka tab lain (Membership,
    // Settings) atau app di-background — persis kebutuhan "tweak aktif
    // ketika game dibuka, reset kalau game ditutup dari recent apps"
    // walaupun AetherX sendiri tidak sedang dilihat pengguna saat itu.
    val context = LocalContext.current
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
            // Splash sekarang hanya loading singkat (cek status yang sudah
            // ada + koneksi database), tidak lagi memicu dialog izin apapun
            // — lihat KDoc SplashScreen. Tujuan setelahnya bergantung pada
            // onboardingCompleted: pengguna BARU (belum pernah setup) lanjut
            // ke Izin Akses; pengguna LAMA (sudah pernah setup, ini cold
            // start biasa) langsung ke MAIN — TAPI tetap melihat splash
            // logo+loading yang sama setiap kali, bukan hanya sekali di
            // awal seperti sebelumnya.
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
                    // popUpTo merujuk PERMISSION_ONBOARDING sendiri (bukan
                    // SPLASH_MAIN) karena SPLASH_MAIN sudah di-pop inclusive
                    // saat splash pindah ke sini — SPLASH_MAIN tidak lagi
                    // ada di back stack di titik ini.
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
            com.aether.x.ui.booster.GameBoosterScreen()
        }
    }

    // Dipasang PALING TERAKHIR (di luar NavHost, bukan di dalam salah satu
    // composable rute) supaya secara komposisi selalu digambar PALING ATAS,
    // menutupi layar rute manapun yang sedang aktif — termasuk saat onboarding
    // masih berjalan. MaintenanceGate menggambar dirinya sendiri sebagai no-op
    // (return awal) kalau mode maintenance sedang tidak aktif, jadi aman
    // dipasang permanen di sini tanpa biaya tambahan saat tidak dipakai.
    MaintenanceGate()

    // UpdateGate: sama seperti MaintenanceGate (dipasang di root, no-op kalau
    // tidak ada versi baru), TAPI dialognya BISA di-dismiss — update di
    // AetherX selalu opsional, tidak pernah memblokir pemakaian aplikasi.
    // Dipasang SETELAH MaintenanceGate supaya kalau kedua kondisi aktif
    // bersamaan, dialog maintenance (blocking) tetap yang paling atas/menang
    // secara visual.
    UpdateGate()
}
