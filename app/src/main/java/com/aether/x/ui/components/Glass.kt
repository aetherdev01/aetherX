package com.aether.x.ui.components

import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
 * HazeState tambahan yang juga bersumber dari lapisan latar aurora. Layar yang
 * punya panel blur sendiri (mis. drawer Tweak) menambahkan kontennya sebagai
 * sumber kedua (zIndex lebih tinggi) pada state ini, sehingga area kosong pun
 * ikut terblur — bukan hanya area yang ada kontennya.
 */
val LocalBackdropHaze = staticCompositionLocalOf<HazeState?> { null }

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
    /**
     * true = tint digambar SETELAH blur sehingga selalu merata di seluruh panel
     * (dipakai drawer). false = tint di bawah blur (perilaku lama dialog).
     */
    tintAfterBlur: Boolean = false,
): Modifier {
    val p = LocalAetherPalette.current
    val b = LocalAetherStyle.current.blur
    val blurOn = hazeState != null && b > 0.01f
    val radius = (blurRadiusFor(b) * blurBoost).dp
    // Keep the tint deliberately light so the captured content remains
    // visibly blurred instead of looking like a merely translucent surface.
    val top = if (blurOn) ((0.54f - 0.14f * b) * tintScale).coerceAtLeast(minAlpha) else 0.97f
    val bottom = if (blurOn) ((0.49f - 0.14f * b) * tintScale).coerceAtLeast(minAlpha - 0.04f) else 0.95f
    val tint = Brush.verticalGradient(
        listOf(p.surface.copy(alpha = top), p.surface.copy(alpha = bottom)),
    )
    return this
        .clip(shape)
        // Default: tint sebelum Haze (dialog). Untuk drawer, tint di bawah blur
        // tertutup hasil blur yang opaque sehingga area tertentu terlihat
        // transparan/tidak merata -> pakai tintAfterBlur.
        .then(if (tintAfterBlur) Modifier else Modifier.background(tint))
        .then(
            if (blurOn) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = radius
                    noiseFactor = 0.03f
                    backgroundColor = p.surface.copy(alpha = 0.22f)
                }
            } else {
                Modifier
            },
        )
        .then(if (tintAfterBlur) Modifier.background(tint) else Modifier)
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
 * Gambar pita aurora (tanpa warna dasar). Dipakai oleh [AetherBackground] dan
 * oleh [AuroraBlurLayer] (salinan yang di-blur untuk latar navbar), sehingga
 * keduanya selalu identik.
 */
fun DrawScope.drawAuroraLayers(
    w: Float,
    h: Float,
    phase: Float,
    accent: Color,
    soft: Color,
    accentMid: Color,
    dark: Boolean,
) {
                    // Gerak campuran menggeser pita sekaligus mengubah kurva dan
                    // ketebalannya; efeknya lebih mirip jelly yang meregang/memantul.
                    val flex = kotlin.math.sin(phase.toDouble() * Math.PI).toFloat()
                    val sway = kotlin.math.sin(phase.toDouble() * Math.PI * 2.0).toFloat()
                    val shift = (phase - 0.5f) * w * 0.30f + sway * w * 0.055f
                    val glowAlpha = if (dark) 0.23f else 0.16f

                    // Diffuse color fields are linear/diagonal, not circular blobs.
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = glowAlpha),
                                Color.Transparent,
                                soft.copy(alpha = glowAlpha * 0.65f),
                                Color.Transparent,
                            ),
                            start = Offset(-w * 0.25f + shift, 0f),
                            end = Offset(w * 0.9f + shift, h),
                        ),
                    )

                    fun flowingBand(
                        y: Float,
                        amplitude: Float,
                        colorA: Color,
                        colorB: Color,
                        alpha: Float,
                        phaseShift: Float,
                    ) {
                        val dx = shift * phaseShift
                        val dynamicAmplitude = amplitude * (0.82f + 0.24f * flex)
                        val cy = y + sway * amplitude * 0.34f * phaseShift
                        val a = dynamicAmplitude
                        val path = Path().apply {
                            moveTo(-w * 0.18f + dx, cy - a * 0.15f)
                            cubicTo(
                                w * 0.16f + dx, cy - a * (1.10f + 0.25f * flex),
                                w * 0.40f + dx, cy + a * (1.02f + 0.22f * sway),
                                w * 0.70f + dx, cy + a * 0.12f,
                            )
                            cubicTo(
                                w * 0.91f + dx, cy - a * (0.48f + 0.14f * sway),
                                w * 1.04f + dx, cy + a * 0.30f,
                                w * 1.18f + dx, cy + a * 0.04f,
                            )
                            lineTo(w * 1.18f + dx, cy + a * 1.8f)
                            cubicTo(
                                w * 0.82f + dx, cy + a * (1.12f + 0.12f * flex),
                                w * 0.48f + dx, cy + a * 2.05f,
                                w * 0.15f + dx, cy + a * 1.22f,
                            )
                            cubicTo(
                                -w * 0.02f + dx, cy + a * 0.78f,
                                -w * 0.08f + dx, cy + a * 1.0f,
                                -w * 0.18f + dx, cy + a * 1.1f,
                            )
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colorA.copy(alpha = alpha),
                                    colorB.copy(alpha = alpha * 0.72f),
                                    Color.Transparent,
                                ),
                                start = Offset(0f + dx, cy - a),
                                end = Offset(w + dx, cy + a * 1.8f),
                            ),
                        )
                    }

                    flowingBand(
                        y = h * 0.19f,
                        amplitude = h * 0.075f,
                        colorA = accent,
                        colorB = soft,
                        alpha = if (dark) 0.28f else 0.22f,
                        phaseShift = 1f,
                    )
                    flowingBand(
                        y = h * 0.54f,
                        amplitude = h * 0.060f,
                        colorA = accentMid,
                        colorB = accent,
                        alpha = if (dark) 0.17f else 0.13f,
                        phaseShift = -0.72f,
                    )
                    flowingBand(
                        y = h * 0.84f,
                        amplitude = h * 0.050f,
                        colorA = accentMid,
                        colorB = soft,
                        alpha = if (dark) 0.13f else 0.095f,
                        phaseShift = 0.48f,
                    )

                    // A few subtle flowing contour lines make motion perceptible
                    // without using circles, particles, or high-contrast decoration.
                    repeat(3) { index ->
                        val y = h * (0.28f + index * 0.22f) + shift * (0.025f + index * 0.008f)
                        val line = Path().apply {
                            moveTo(-w * 0.1f, y)
                            cubicTo(w * 0.22f, y - h * 0.035f, w * 0.48f, y + h * 0.045f, w * 0.72f, y - h * 0.005f)
                            cubicTo(w * 0.88f, y - h * 0.025f, w * 1.02f, y + h * 0.018f, w * 1.1f, y)
                        }
                        drawPath(
                            path = line,
                            color = (if (index % 2 == 0) accent else soft).copy(alpha = if (dark) 0.10f else 0.075f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.cornerPathEffect(18.dp.toPx()),
                            ),
                        )
                    }
}

/** Fase animasi aurora (0f..1f, bolak-balik). Dibaca di fase draw saja -> tanpa recomposition. */
class AuroraBackdrop(val motion: State<Float>)

val LocalAuroraBackdrop = staticCompositionLocalOf<AuroraBackdrop?> { null }

/**
 * Salinan latar aurora yang di-BLUR sungguhan (RenderEffect bawaan Compose, API 31+),
 * tanpa Haze dan tanpa bergantung pada flag blur OS. Aurora digambar ulang persis
 * pada posisi layar yang sama dengan latar asli, lalu di-blur dan dipotong ke [shape].
 * Pakai sebagai latar kaca (mis. navbar); beri tint tipis di atasnya.
 */
@Composable
fun AuroraBlurLayer(
    blurRadius: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val backdrop = LocalAuroraBackdrop.current
    val p = LocalAetherPalette.current
    val accent = p.accent
    val soft = p.accentSoft
    val accentMid = lerp(accent, soft, 0.52f)
    val bg = p.bg
    val dark = p.isDark
    var origin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(Size.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { c ->
                origin = c.positionInRoot()
                rootSize = c.findRootCoordinates().size.toSize()
            }
            .clip(shape)
            .graphicsLayer {
                this.alpha = alpha
                val r = blurRadius.toPx()
                renderEffect = BlurEffect(r, r, TileMode.Clamp)
            }
            .drawBehind {
                drawRect(bg)
                val rs = rootSize
                if (rs.width > 0f && rs.height > 0f) {
                    val o = origin
                    withTransform({ translate(-o.x, -o.y) }) {
                        drawAuroraLayers(
                            rs.width, rs.height,
                            backdrop?.motion?.value ?: 0.5f,
                            accent, soft, accentMid, dark,
                        )
                    }
                }
            },
    )
}

/**
 * Latar app: pita jelly/aurora yang mengalir dan berubah bentuk perlahan.
 * Intensitas mode terang sengaja dinaikkan agar warna aksen tetap terlihat di atas
 * permukaan putih; seluruh pita memakai palet aksen aktif dan otomatis berubah saat
 * preset aksen atau mode tema diganti. Tidak memakai orb/lingkaran dekoratif.
 * Lapisan ini juga menjadi sumber blur card (LocalCardHaze).
 */
@Composable
fun AetherBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalAetherPalette.current
    val accent = p.accent
    val soft = p.accentSoft
    // Semua warna latar berasal dari aksen aktif supaya tidak muncul warna asing
    // ketika user mengganti preset aksen.
    val accentMid = lerp(accent, soft, 0.52f)
    val dark = p.isDark
    val bg = p.bg
    val cardHaze = rememberHazeState(blurEnabled = true)
    val backdropHaze = rememberHazeState(blurEnabled = true)
    val motionTransition = rememberInfiniteTransition(label = "aether-background-motion")
    val motionState = motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aether-background-phase",
    )
    val motion by motionState
    val auroraBackdrop = remember(motionState) { AuroraBackdrop(motionState) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(cardHaze)
                .hazeSource(backdropHaze, zIndex = 0f)
                .background(bg)
                .drawBehind { drawAuroraLayers(size.width, size.height, motion, accent, soft, accentMid, dark) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalCardHaze provides cardHaze,
                LocalBackdropHaze provides backdropHaze,
                LocalAuroraBackdrop provides auroraBackdrop,
            ) {
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
