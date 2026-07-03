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
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.aether.x"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 110
        versionName   = "1.1 Beta"

        base.archivesName = "AetherX-v$versionName"
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        // Native signature guard (lihat app/src/main/cpp/sigcheck.cpp dan
        // SignatureGuard.kt) — hash signing cert dibandingkan di sisi native
        // supaya tidak muncul sebagai string plain di DEX/Kotlin bytecode.
        externalNativeBuild {
            cmake {
                cppFlags += ""
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
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
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

dependencies {
    // ── Core ─────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // ── Activity & Navigation ─────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // ── DataStore (penyimpanan preferensi tweak & onboarding) ────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Privilege backend: Shizuku (mode non-root) ───────────────────────────
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // ── Privilege backend: libsu (mode root — Magisk / KernelSU / APatch) ───
    implementation(libs.libsu.core)

    // ── Firebase: counter Firestore untuk ID pengguna global berurutan ───────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)

    // ── Firebase App Check (Play Integrity) — GUARD anti brute-force lisensi ─
    // Menandatangani setiap request Firestore dengan bukti bahwa request ini
    // datang dari APK asli yang ditandatangani dengan kunci rilis kita dan
    // lolos verifikasi integritas Google Play — bukan dari script/curl/APK
    // hasil modifikasi. Firestore rules (lihat firestore.rules, fungsi
    // isVerifiedApp()) menolak SEMUA request yang tidak membawa token App
    // Check yang valid. Dependency ini TIDAK memakai version catalog (libs.*)
    // karena entrinya belum ada di gradle/libs.versions.toml — koordinat Maven
    // ditulis langsung di sini, cukup selaraskan versinya dengan BOM Firebase
    // yang sudah dipakai (libs.firebase.bom) kalau nanti ingin dipindah ke
    // catalog.
    implementation("com.google.firebase:firebase-appcheck-playintegrity:18.0.0")
}
