package com.aether.x.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle

/**
 * v3.6 — permukaan card sesuai gaya pilihan user (Pengaturan -> Tampilan):
 *  - GLASS   : kaca transparan bergradasi + garis tepi berkilau. Makin besar
 *              slider blur, makin transparan (aurora latar makin tembus).
 *  - SOLID   : putih/gelap pekat + garis tepi tipis.
 *  - OUTLINE : tanpa isi, hanya garis tepi berwarna aksen.
 *  - SOFT    : isi tonal berwarna aksen tipis, tanpa garis tepi.
 *
 * Sengaja TIDAK memakai blur backdrop per-card: blur real-time di belakang
 * banyak card yang di-scroll terbukti membuat jank di perangkat mid-range.
 * Tampilan kaca di card dicapai lewat transparansi + highlight di atas
 * latar aurora ([AetherBackground]); blur sungguhan dipakai di dialog.
 */
@Composable
fun Modifier.aetherCardSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = LocalAetherPalette.current.accent,
): Modifier {
    val p = LocalAetherPalette.current
    val s = LocalAetherStyle.current
    val b = s.blur
    return when (s.cardStyle) {
        CardStyle.GLASS -> this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        p.surface.copy(alpha = 0.94f - 0.16f * b),
                        p.surface.copy(alpha = 0.88f - 0.16f * b),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (p.isDark) 0.16f else 0.90f),
                        p.stroke.copy(alpha = if (p.isDark) 0.55f else 0.70f),
                    ),
                ),
                shape = shape,
            )

        CardStyle.SOLID -> this
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.stroke.copy(alpha = 0.8f), shape)

        CardStyle.OUTLINE -> this
            .clip(shape)
            .border(1.2.dp, accent.copy(alpha = 0.50f), shape)

        CardStyle.SOFT -> this
            .clip(shape)
            .background(lerp(p.surface, accent, if (p.isDark) 0.12f else 0.07f))
    }
}

/**
 * HazeState "root" milik seluruh UI app (dipasang di MainActivity). Dialog
 * (jendela terpisah) memakainya untuk mem-blur isi layar di belakangnya.
 * null = blur tidak tersedia (mis. preview) -> panel jatuh ke tint pekat.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Radius blur (dp) dari slider 0f..1f. 0 berarti blur dimatikan. */
fun blurRadiusFor(blur: Float): Float = 6f + 34f * blur

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
    minAlpha: Float = 0.60f,
    hazeState: HazeState? = LocalHazeState.current,
): Modifier {
    val p = LocalAetherPalette.current
    val b = LocalAetherStyle.current.blur
    val blurOn = hazeState != null && b > 0.01f
    val radius = blurRadiusFor(b).dp
    val top = if (blurOn) (0.84f - 0.20f * b).coerceAtLeast(minAlpha) else 0.97f
    val bottom = if (blurOn) (0.80f - 0.18f * b).coerceAtLeast(minAlpha - 0.04f) else 0.95f
    return this
        .clip(shape)
        .then(
            if (blurOn) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = radius
                    noiseFactor = 0.03f
                }
            } else {
                Modifier
            },
        )
        .background(
            Brush.verticalGradient(
                listOf(p.surface.copy(alpha = top), p.surface.copy(alpha = bottom)),
            ),
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
 * Latar "aurora": dua-tiga gumpalan cahaya lembut berwarna aksen di atas
 * warna dasar tema. Memberi sesuatu yang "tembus" di belakang card kaca dan
 * navbar; digambar di fase draw sekali (tanpa animasi) jadi sangat ringan.
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
    val dark = p.isDark
    val bg = p.bg

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                val w = size.width
                val h = size.height
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
                blob(accent, (if (dark) 0.26f else 0.20f) * k, w * 0.08f, h * 0.04f, w * 0.95f)
                blob(soft, (if (dark) 0.16f else 0.18f) * k, w * 0.98f, h * 0.46f, w * 0.80f)
                blob(premium, (if (dark) 0.08f else 0.07f) * k, w * 0.10f, h * 0.92f, w * 0.85f)
            },
        content = content,
    )
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
