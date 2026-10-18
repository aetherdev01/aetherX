package com.aether.x.ui.components

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import java.util.function.Consumer

/**
 * Blur NATIVE sistem (Android 12+): `Window.setBackgroundBlurRadius`.
 *
 * Blur ini dikerjakan compositor OS terhadap SEMUA yang ada di belakang window
 * (aurora, konten tab, drawer, dsb.), bukan salinan yang digambar ulang Compose.
 * Syaratnya (lihat dokumentasi AOSP "Window blurs"):
 *  - window harus mengambang (Dialog) dan translusen;
 *  - bentuk area blur mengikuti OUTLINE background drawable window -> kita pasang
 *    GradientDrawable kapsul;
 *  - OS boleh mematikan blur kapan saja (hemat baterai, GPU lemah, dsb.) ->
 *    gunakan [rememberCrossWindowBlurEnabled] dan sediakan fallback.
 */

/** true bila OS sedang mengizinkan blur lintas-window. Berubah saat mode hemat baterai dll. */
@Composable
fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val wm = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember(wm) { mutableStateOf(wm?.isCrossWindowBlurEnabled == true) }
    DisposableEffect(wm) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Consumer<Boolean> { enabled = it }
        wm?.addCrossWindowBlurEnabledListener(executor, listener)
        onDispose { wm?.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/** Penyimpan Window dialog + posisi target, supaya reposisi tidak memicu recomposition. */
class NativeBlurWindowState {
    internal var window: Window? = null
    internal var anchorView: View? = null

    /** Posisi/ukuran target dalam koordinat window activity (px). */
    internal var bounds: Rect? = null

    /** Dipanggil dari onGloballyPositioned penampung; langsung memindahkan window dialog. */
    fun updateBounds(view: View, r: Rect) {
        anchorView = view
        bounds = r
        applyBounds()
    }

    internal fun applyBounds() {
        val w = window ?: return
        val v = anchorView ?: return
        val r = bounds ?: return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val lp = w.attributes
        val x = loc[0] + r.left
        val y = loc[1] + r.top
        if (lp.x == x && lp.y == y && lp.width == r.width() && lp.height == r.height() &&
            lp.gravity == (Gravity.TOP or Gravity.START)
        ) return
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = x
        lp.y = y
        lp.width = r.width()
        lp.height = r.height()
        w.attributes = lp
    }
}

/**
 * Menampilkan [content] di window Dialog sendiri yang menempel tepat di [state.bounds],
 * dengan latar kapsul yang di-blur oleh sistem. Window TIDAK fokus dan menerima sentuhan
 * hanya di dalam areanya, jadi activity di belakangnya tetap interaktif.
 *
 * @param blurRadiusPx radius blur dalam piksel.
 * @param tint warna tint latar (alpha kecil = lebih transparan).
 */
@Composable
fun NativeBlurWindow(
    state: NativeBlurWindowState,
    blurRadiusPx: Int,
    tint: Color,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val tintArgb = tint.toArgb()

        SideEffect {
            if (window == null) return@SideEffect
            state.window = window
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            // NOT_FOCUSABLE + ALT_FOCUSABLE_IM: window tidak merebut fokus, tapi keyboard
            // (IME) tetap digambar DI ATAS window ini (tanpa itu navbar menimpa keyboard).
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            )
            window.setWindowAnimations(0)
            // Outline drawable inilah yang menentukan bentuk area blur.
            val h = state.bounds?.height() ?: 0
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = if (h > 0) h / 2f else 1000f
                setColor(tintArgb)
            }
            window.setBackgroundDrawable(bg)
            window.setBackgroundBlurRadius(blurRadiusPx)
            state.applyBounds()
        }
        DisposableEffect(window) {
            onDispose { if (state.window === window) state.window = null }
        }

        content()
    }
}
