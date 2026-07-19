package com.aether.x.core.appmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aether.x.core.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManagerCatalog {

    private const val WHITELIST_ASSET_FILE_NAME = "app_manager_bloatware_whitelist.txt"

    @Volatile
    private var cachedWhitelist: Set<String>? = null

    private suspend fun readWhitelist(context: Context): Set<String> {
        cachedWhitelist?.let { return it }
        return withContext(Dispatchers.IO) {
            val names = try {
                context.assets.open(WHITELIST_ASSET_FILE_NAME).bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') }
                        .toSet()
                }
            } catch (t: Throwable) {
                emptySet()
            }
            cachedWhitelist = names
            names
        }
    }

    suspend fun loadManageableApps(context: Context, executor: ShellExecutor): List<InstalledAppEntry> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val whitelist = readWhitelist(context)
            val frozenPackages = readFrozenPackageNames(executor)

            @Suppress("DEPRECATION")
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val ownPackageName = context.packageName

            val thirdParty = allApps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ownPackageName }
                .mapNotNull { toEntry(pm, it, AppOrigin.THIRD_PARTY, frozenPackages) }
                .sortedBy { it.label.lowercase() }

            val bloatware = allApps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 && it.packageName in whitelist }
                .mapNotNull { toEntry(pm, it, AppOrigin.KNOWN_BLOATWARE, frozenPackages) }
                .sortedBy { it.label.lowercase() }

            thirdParty + bloatware
        }

    private suspend fun readFrozenPackageNames(executor: ShellExecutor): Set<String> {
        val result = executor.exec("pm list packages -d")
        return result.output
            .mapNotNull { line -> line.trim().removePrefix("package:").takeIf { it.isNotBlank() } }
            .toSet()
    }

    suspend fun isPackageFrozen(executor: ShellExecutor, packageName: String): Boolean {
        return packageName in readFrozenPackageNames(executor)
    }

    private fun toEntry(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        origin: AppOrigin,
        frozenPackages: Set<String>,
    ): InstalledAppEntry? {
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (t: Throwable) {
            appInfo.packageName
        }
        val icon = try {
            drawableToImageBitmap(pm.getApplicationIcon(appInfo))
        } catch (t: Throwable) {
            return null
        }
        return InstalledAppEntry(
            packageName = appInfo.packageName,
            label = label,
            icon = icon,
            origin = origin,
            isFrozen = appInfo.packageName in frozenPackages,
        )
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
