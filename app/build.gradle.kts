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
        versionCode   = 31100002
        versionName   = "3.11.0-beta.2"

        // Nomor run CI (GitHub Actions) — dipakai untuk bagian "rXXX" di nama APK.
        // Di lokal (bukan CI) akan fallback ke "0" karena env var ini tidak ada.
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"

        base.archivesName = "AXKM_v$versionName.r$ciRunNumber" + "_$versionCode"
        // Satu APK universal yang memuat native library untuk kedua ABI.
        // Tidak membuat APK terpisah per-ABI.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
        }

    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    // ABI splits dinonaktifkan: hasil APK memuat arm64-v8a dan armeabi-v7a.

    // Biar pilihan bahasa di dalam app (ID/EN) tetap jalan di App Bundle
    // walau bahasa sistem HP berbeda.
    bundle {
        language {
            enableSplit = false
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
            useLegacyPackaging = true
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

    // ── Compose (animation & ui-graphics sudah ikut transitive lewat ui/material3,
    //    ui-tooling & ui-tooling-preview dibuang karena tidak ada @Preview) ──────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ── Activity & Navigation ─────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // ── DataStore (penyimpanan preferensi tweak & onboarding) ────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Privilege backend: libsu (mode root — Magisk / KernelSU / APatch) ────
    implementation(libs.libsu.core)

    // ── Firebase: Firestore (ID pengguna, lisensi, maintenance, update),
    //    Cloud Messaging (push), Auth anonim + Realtime Database (presence). ─
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // ── Ads: Unity Ads (rewarded + fallback interstitial) & AdMob (interstitial) ──
    implementation(libs.unity.ads)
    implementation(libs.play.services.ads)

    // ── Reorder kartu dashboard dengan long-press + drag ─────────────────────
    implementation(libs.reorderable)
    implementation(libs.haze)
    // Kyant0 AndroidLiquidGlass (Backdrop engine) used by LiquidToggle/Button/Slider/BottomTabs.
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
}
