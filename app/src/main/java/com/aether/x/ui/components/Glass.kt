package com.aether.x.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.aether.x.data.CardStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle

/**
 * HazeState latar ([AetherBackground]) untuk card. Sumbernya adalah lapisan
 * latar (aurora + orb) yang menjadi SIBLING konten, jadi card boleh mem-blur-nya.
 */
val LocalCardHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * v3.8 — permukaan card sesuai gaya pilihan user (Pengaturan -> Tampilan).
 * SEMUA gaya sekarang memblur latar di belakang card (Haze); gaya hanya
 * menentukan seberapa pekat tint dan bagaimana tepinya:
 *  - GLASS   : tint tipis + garis tepi berkilau  (blur paling terlihat).
 *  - SOLID   : tint pekat + garis tepi tipis.
 *  - OUTLINE : tint sangat tipis + garis tepi aksen.
 *  - SOFT    : tint tonal berwarna aksen, tanpa garis tepi.
 * Slider blur = kekuatan blur; di 0 blur mati dan card kembali ke isi biasa.
 */
@Composable
fun Modifier.aetherCardSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = LocalAetherPalette.current.accent,
): Modifier {
    val p = LocalAetherPalette.current
    val s = LocalAetherStyle.current
    val b = s.blur
    val haze = LocalCardHaze.current
    val blurOn = haze != null && b > 0.01f
    val radius = cardBlurRadiusFor(b).dp

    val frost = if (blurOn) {
        Modifier.hazeEffect(state = haze) {
            blurRadius = radius
            noiseFactor = 0.04f
            // Haze 1.x needs an explicit backdrop color. Keeping it lightly
            // tinted avoids ROM/device-dependent transparent-only panels.
            backgroundColor = p.surface.copy(alpha = 0.18f)
        }
    } else {
        Modifier
    }
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (p.isDark) 0.16f else 0.90f),
            p.stroke.copy(alpha = if (p.isDark) 0.55f else 0.70f),
        ),
    )

    return when (s.cardStyle) {
        CardStyle.GLASS -> this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        p.surface.copy(alpha = if (blurOn) 0.62f - 0.14f * b else 0.94f),
                        p.surface.copy(alpha = if (blurOn) 0.54f - 0.12f * b else 0.88f),
                    ),
                ),
            )
            .then(frost)
            .border(1.dp, rim, shape)

        CardStyle.SOLID -> this
            .clip(shape)
            .background(p.surface.copy(alpha = if (blurOn) 0.86f else 1f))
            .then(frost)
            .border(1.dp, p.stroke.copy(alpha = 0.8f), shape)

        CardStyle.OUTLINE -> this
            .clip(shape)
            .background(p.surface.copy(alpha = if (blurOn) 0.36f else 0f))
            .then(frost)
            .border(1.2.dp, accent.copy(alpha = 0.55f), shape)

        CardStyle.SOFT -> this
            .clip(shape)
            .background(
                lerp(p.surface, accent, if (p.isDark) 0.14f else 0.09f)
                    .copy(alpha = if (blurOn) 0.72f else 1f),
            )
            .then(frost)
    }
}

/**
 * HazeState "root" milik seluruh UI app (dipasang di MainActivity). Dialog
 * (jendela terpisah) memakainya untuk mem-blur isi layar di belakangnya.
 * null = blur tidak tersedia (mis. preview) -> panel jatuh ke tint pekat.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Radius blur (dp) dialog/navbar dari slider 0f..1f. 0 berarti blur dimatikan. */
fun blurRadiusFor(blur: Float): Float = 10f + 50f * blur

/** Radius blur card: sedikit lebih kecil supaya bentuk orb latar masih terbaca di balik card. */
fun cardBlurRadiusFor(blur: Float): Float = 8f + 34f * blur

/**
 * Panel blur serbaguna (dialog, drawer). Isi di belakang panel di-BLUR
 * sungguhan lewat Haze (`hazeEffect`), lalu diberi tint tipis supaya teks
 * tetap terbaca. Slider blur menentukan radius blur; di 0 blur mati dan
 * panel jadi hampir pekat.
 *
 * [hazeState] harus berupa state yang sumbernya (`hazeSource`) adalah
 * SIBLING panel ini, bukan induknya. Dialog memakai state root.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    minAlpha: Float = 0.30f,
    hazeState: HazeState? = LocalHazeState.current,
    /** Pengali radius blur (drawer memakai > 1 supaya isi di belakangnya benar-benar kabur). */
    blurBoost: Float = 1f,
    /** Pengali kepekatan tint (< 1 = lebih bening, blur lebih terlihat). */
    tintScale: Float = 1f,
): Modifier {
    val p = LocalAetherPalette.current
    val b = LocalAetherStyle.current.blur
    val blurOn = hazeState != null && b > 0.01f
    val radius = (blurRadiusFor(b) * blurBoost).dp
    // Keep the tint deliberately light so the captured content remains
    // visibly blurred instead of looking like a merely translucent surface.
    val top = if (blurOn) ((0.54f - 0.14f * b) * tintScale).coerceAtLeast(minAlpha) else 0.97f
    val bottom = if (blurOn) ((0.49f - 0.14f * b) * tintScale).coerceAtLeast(minAlpha - 0.04f) else 0.95f
    return this
        .clip(shape)
        // Draw tint before the Haze effect; putting this after hazeEffect would
        // paint over the captured blur and make the drawer look only translucent.
        .background(
            Brush.verticalGradient(
                listOf(p.surface.copy(alpha = top), p.surface.copy(alpha = bottom)),
            ),
        )
        .then(
            if (blurOn) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = radius
                    noiseFactor = 0.03f
                    backgroundColor = p.surface.copy(alpha = 0.16f)
                }
            } else {
                Modifier
            },
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (p.isDark) 0.18f else 0.90f),
                    p.stroke.copy(alpha = 0.60f),
                ),
            ),
            shape = shape,
        )
}

/**
 * Latar app: warna dasar + aurora + "orb" berwarna dengan tepi tegas. Orb
 * itulah yang di-blur card di atasnya (sehingga blur card KELIHATAN: di luar
 * card orb tegas, di dalam card kabur). Lapisan ini adalah SUMBER blur
 * ([LocalCardHaze]) dan SIBLING konten, bukan induknya, sesuai aturan Haze.
 * Digambar sekali di fase draw (tanpa animasi) jadi ringan.
 */
@Composable
fun AetherBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalAetherPalette.current
    val k = 0.55f + 0.45f * LocalAetherStyle.current.blur
    val accent = p.accent
    val soft = p.accentSoft
    val premium = p.premium
    val success = p.success
    val dark = p.isDark
    val bg = p.bg
    val cardHaze = rememberHazeState(blurEnabled = true)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(cardHaze)
                .background(bg)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val dp = density
                    fun blob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                        val center = Offset(cx, cy)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                    // Orb: tepi tegas (alpha tepi ~60% dari tengah) supaya blur terlihat.
                    fun orb(color: Color, alpha: Float, cx: Float, cy: Float, rDp: Float) {
                        val r = rDp * dp
                        val center = Offset(cx, cy)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.55f)),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                    blob(accent, (if (dark) 0.22f else 0.16f) * k, w * 0.08f, h * 0.04f, w * 0.95f)
                    blob(soft, (if (dark) 0.14f else 0.14f) * k, w * 0.98f, h * 0.46f, w * 0.80f)
                    blob(premium, (if (dark) 0.07f else 0.06f) * k, w * 0.10f, h * 0.92f, w * 0.85f)

                    val a = if (dark) 0.40f else 0.30f
                    orb(accent, a, w * 0.14f, h * 0.20f, 74f)
                    orb(premium, a * 0.85f, w * 0.90f, h * 0.30f, 58f)
                    orb(soft, a, w * 0.34f, h * 0.52f, 88f)
                    orb(success, a * 0.7f, w * 0.88f, h * 0.68f, 66f)
                    orb(accent, a * 0.9f, w * 0.18f, h * 0.84f, 62f)
                    orb(premium, a * 0.7f, w * 0.72f, h * 0.94f, 50f)
                },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalCardHaze provides cardHaze) {
                content()
            }
        }
    }
}

/**
 * Redup (dim) di belakang jendela Dialog. Blur-nya sendiri sekarang dikerjakan
 * Haze pada panel dialog ([glassPanel]) — blur lintas-jendela bawaan platform
 * (FLAG_BLUR_BEHIND) dihapus karena sering dimatikan ROM/penghemat baterai
 * sehingga yang tampil cuma transparan. Dipanggil DI DALAM content Dialog.
 */
@Composable
fun DialogWindowDim() {
    val blur = LocalAetherStyle.current.blur
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window == null) return@SideEffect
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.setDimAmount(0.28f - 0.10f * blur)
    }
}
