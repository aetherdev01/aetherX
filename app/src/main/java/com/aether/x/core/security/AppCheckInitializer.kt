package com.aether.x.core.security

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

// CATATAN: LicenseRepository.kt sekarang akses Firestore langsung, TIDAK lagi
// lewat Cloud Functions yang mewajibkan App Check — jadi file ini sebenarnya
// tidak lagi krusial untuk alur lisensi. Dibiarkan tetap ada (dipanggil dari
// AetherXApp.onCreate) karena tidak fatal kalau gagal (cuma Log.e), dan kalau
// suatu saat App Check dipakai lagi untuk fitur lain, initializer-nya sudah
// siap. TIDAK PAKAI DebugAppCheckProviderFactory di sini — itu butuh
// dependency tambahan (firebase-appcheck-debug) yang tidak ada di
// build.gradle.kts, dan sudah tidak relevan sejak LicenseRepository lepas
// dari App Check.
object AppCheckInitializer {

    private const val TAG = "AppCheckInitializer"

    fun init(context: Context) {
        runCatching {
            Firebase.initialize(context)
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }.onFailure { e ->

            Log.e(TAG, "Gagal memasang App Check provider", e)
        }
    }
}
