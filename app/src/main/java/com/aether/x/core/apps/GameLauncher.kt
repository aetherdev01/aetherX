package com.aether.x.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class DetectedGame(
    val packageName: String,
    val displayName: String,
)

object GameLauncher {

    const val PACKAGE_FREE_FIRE = "com.dts.freefireth"
    const val PACKAGE_FREE_FIRE_MAX = "com.dts.freefire.max"

    private val knownGames = listOf(
        PACKAGE_FREE_FIRE to "Free Fire",
        PACKAGE_FREE_FIRE_MAX to "Free Fire MAX",
    )

    fun detectInstalled(context: Context): List<DetectedGame> {
        val pm = context.packageManager
        return knownGames.mapNotNull { (pkg, name) ->
            if (isInstalled(pm, pkg)) DetectedGame(pkg, name) else null
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
