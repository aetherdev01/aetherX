package com.aether.x.core.apps

import android.content.Context
import android.content.Intent
import com.aether.x.data.AetherXPreferences

object GameLaunchTracker {

    suspend fun launchAndTrack(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        val launched = try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
        if (launched) {
            AetherXPreferences(context.applicationContext).recordGameLaunched(packageName)
        }
        return launched
    }
}
