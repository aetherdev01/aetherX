package com.aether.x.core.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledGameEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

object GameProfileCatalog {

    private const val ASSET_FILE_NAME = "gamelist.txt"

    @Volatile
    private var cachedPackageNames: List<String>? = null

    private suspend fun readCatalogPackageNames(context: Context): List<String> {
        cachedPackageNames?.let { return it }
        return withContext(Dispatchers.IO) {
            val names = try {
                context.assets.open(ASSET_FILE_NAME).bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') }
                        .distinct()
                        .toList()
                }
            } catch (t: Throwable) {
                emptyList()
            }
            cachedPackageNames = names
            names
        }
    }

    suspend fun loadInstalledGames(context: Context): List<InstalledGameEntry> =
        withContext(Dispatchers.IO) {
            val catalog = readCatalogPackageNames(context)
            val pm = context.packageManager
            catalog.mapNotNull { packageName -> loadEntryIfInstalled(pm, packageName) }
                .sortedBy { it.label.lowercase() }
        }

    suspend fun isKnownGamePackage(context: Context, packageName: String): Boolean =
        readCatalogPackageNames(context).contains(packageName)

    suspend fun loadIconForPackage(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                drawableToImageBitmap(pm.getApplicationIcon(appInfo))
            } catch (t: Throwable) {
                null
            }
        }

    private fun loadEntryIfInstalled(pm: PackageManager, packageName: String): InstalledGameEntry? {
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (t: Throwable) {
            packageName
        }
        val icon = try {
            drawableToImageBitmap(pm.getApplicationIcon(appInfo))
        } catch (t: Throwable) {
            return null
        }
        return InstalledGameEntry(packageName = packageName, label = label, icon = icon)
    }

    internal fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
