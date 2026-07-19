package com.aether.x.core.security

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

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
