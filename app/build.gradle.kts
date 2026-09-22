plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// ── Signing config dari local.properties ─────────────────────────────────────
val localProperties = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.associate {
        val (k, v) = it.split("=", limit = 2).let { p -> p[0] to (p.getOrElse(1) { "" }) }
        k.trim() to v.trim()
    }
    ?: emptyMap()

android {
    namespace   = "com.aether.x"
    compileSdk  = 36

    defaultConfig {
        applicationId = "com.aether.x"
        minSdk        = 31
        targetSdk     = 36
        versionCode   = 30501209
        versionName   = "3.5"

        // Nomor run CI (GitHub Actions) — dipakai untuk bagian "rXXX" di nama APK.
        // Di lokal (bukan CI) akan fallback ke "0" karena env var ini tidak ada.
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"

        base.archivesName = "AXKM_v$versionName.r$ciRunNumber" + "_$versionCode"
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            arguments += listOf("-DANDROID_STL=none")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties["STORE_FILE"]
            if (!storeFilePath.isNullOrBlank()) {
                storeFile     = rootProject.file(storeFilePath)
                storePassword = localProperties["STORE_PASSWORD"]
                keyAlias      = localProperties["KEY_ALIAS"]
                keyPassword   = localProperties["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE" // ganti "NONE" kalau mau nol info native sama sekali
            }
        }
    }

    // Split APK per-ABI supaya user tidak download native code arm64 DAN
    // armeabi-v7a sekaligus dalam satu APK universal. Kalau distribusi lewat
    // App Bundle (.aab), blok ini bisa diabaikan — Play Store sudah otomatis
    // melakukan hal yang sama per-device.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // NOTE: pengaturan JVM target & opt-in compiler ada di blok top-level
    // `kotlin { compilerOptions { ... } }` di bawah file ini (bukan di sini),
    // karena API `android.kotlinOptions` sudah deprecated di Kotlin 2.x.

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    // ── Core ─────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── SavedState (FITUR BARU — Game Booster) ───────────────────────────────
    // Dideklarasikan eksplisit (bukan hanya transitive lewat lifecycle-runtime-
    // ktx) karena dipakai LANGSUNG oleh ComposeOverlayLifecycleOwner
    // (SavedStateRegistryController, setViewTreeSavedStateRegistryOwner) untuk
    // menjalankan ComposeView floating sidebar Game Booster yang di-attach
    // manual ke WindowManager dari Service, bukan dari Activity/Fragment biasa
    // yang sudah otomatis menyediakan owner ini.
    implementation(libs.androidx.savedstate)

    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // ── Haze: blur kaca real-time di belakang navbar (efek "Liquid Glass"
    //    ala iOS 26). hazeSource() dipasang di konten layar, hazeEffect()
    //    di navbar — lihat AetherBottomNavBar.kt & MainScreen.kt. ─────────
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // ── Activity & Navigation ─────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // ── DataStore (penyimpanan preferensi tweak & onboarding) ────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Privilege backend: libsu (mode root — Magisk / KernelSU / APatch).
    //    v3.1: AetherX sekarang PURE ROOT — Shizuku dan seluruh mode
    //    non-root DIHAPUS TOTAL, hanya backend ini yang tersisa. ───────────
    implementation(libs.libsu.core)

    // ── Firebase: counter Firestore untuk ID pengguna global berurutan ───────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)

    // ── Firebase Auth (anonymous sign-in) + Cloud Functions — dipakai
    // data/LicenseRepository.kt untuk verifikasi lisensi lewat callable
    // function di server (Firebase.auth, Firebase.functions).
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)

    // ── Firebase Cloud Messaging: notifikasi push realtime (maintenance/
    // update/membership) yang tetap sampai walau aplikasi di-background atau
    // ditutup total — lihat core/messaging/AetherXFirebaseMessagingService.kt
    // dan data/FcmTokenRepository.kt.
    implementation(libs.firebase.messaging)

    // ── Ads: Unity Ads (rewarded ads untuk fitur non-member) ─────────────────
    // Dipakai lewat abstraksi RewardedAdManager (lihat core/ads/RewardedAdManager.kt)
    // — bukan direferensikan langsung di luar core/ads/UnityRewardedAdManager.kt.
    // WAJIB isi GAME_ID & PLACEMENT_ID asli di UnityRewardedAdManager.kt sebelum
    // build release (lihat TODO di file itu dan core/ads/README.md).
    implementation(libs.unity.ads)
    implementation(libs.reorderable) 
}
