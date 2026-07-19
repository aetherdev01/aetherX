package com.aether.x.core.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val supportedRefreshRates: List<Float>,
) {
    val maxRefreshRate: Float get() = supportedRefreshRates.maxOrNull() ?: 60f
    val aspectRatio: Float get() = heightPx.toFloat() / widthPx.toFloat()
}

object DisplayInfoProvider {

    fun read(context: Context): DisplayInfo {

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val metrics = context.resources.displayMetrics
        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        val refreshRates = try {
            when {
                display == null -> listOf(60f)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    display.supportedModes.map { it.refreshRate }.distinct().sorted()
                else -> listOf(display.refreshRate)
            }
        } catch (t: Throwable) {
            listOf(60f)
        }.ifEmpty { listOf(60f) }

        return DisplayInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = densityDpi,
            supportedRefreshRates = refreshRates,
        )
    }
}
