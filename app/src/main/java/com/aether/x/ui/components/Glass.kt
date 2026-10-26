package com.aether.x.ui.components

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * HazeState latar ([AetherBackground]) untuk card. Sumbernya adalah lapisan
 * latar grid ambient yang menjadi SIBLING konten, jadi card boleh mem-blur-nya.
 */
val LocalCardHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * HazeState tambahan yang juga bersumber dari lapisan latar grid ambient. Layar yang
 * punya panel blur sendiri (mis. drawer Tweak) menambahkan kontennya sebagai
 * sumber kedua (zIndex lebih tinggi) pada state ini, sehingga area kosong pun
 * ikut terblur — bukan hanya area yang ada kontennya.
 */
val LocalBackdropHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Shared Liquid Glass surface for cards and nested panels. The old persisted
 * card-style preference is intentionally ignored so no screen falls back to a
 * solid/soft card while the app is using the Liquid Glass visual system.
 * Blur Intensity controls the Haze radius; Liquid Glass Level controls the surface tint and opacity.
 */
@Composable
fun Modifier.aetherCardSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = LocalAetherPalette.current.accent,
): Modifier {
    val p = LocalAetherPalette.current
    val s = LocalAetherStyle.current
    val b = s.blur
    val glass = s.glassLevel
    val glassAlpha = liquidGlassTintAlpha(glass)
    val haze = LocalCardHaze.current
    val blurOn = haze != null && b > 0.01f
    val radius = cardBlurRadiusFor(b).dp

    val frost = if (blurOn) {
        Modifier.hazeEffect(state = haze!!) {
            blurRadius = radius
            noiseFactor = 0.04f
            // Keep Haze's own base tint almost neutral. The dedicated overlay
            // below is the actual Liquid Glass Level control, so blur no longer
            // hides the difference between Clear, Thin, Balanced, Dense and Solid.
            backgroundColor = p.surface.copy(alpha = 0.035f)
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
    val baseSurface = Brush.verticalGradient(
        listOf(
            p.surface.copy(alpha = if (blurOn) 0.025f else 0.40f + 0.48f * glass),
            p.surface.copy(alpha = if (blurOn) 0.02f else 0.34f + 0.42f * glass),
        ),
    )
    val levelTint = Brush.verticalGradient(
        listOf(
            p.surface.copy(alpha = (glassAlpha * 0.92f).coerceIn(0f, 0.88f)),
            p.surface.copy(alpha = (glassAlpha * 0.76f).coerceIn(0f, 0.82f)),
        ),
    )

    // Semua kartu tetap memakai Liquid Glass. Saat blur aktif, tint level sengaja
    // digambar SETELAH Haze agar hasil blur tidak menutupi perubahan opacity.
    return this
        .clip(shape)
        .background(baseSurface)
        .then(frost)
        .then(if (blurOn) Modifier.background(levelTint) else Modifier)
        .drawWithContent {
            drawContent()
            val inset = 18.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.02f),
                        Color.White.copy(alpha = if (p.isDark) 0.25f else 0.72f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                    startX = inset,
                    endX = size.width - inset,
                ),
                start = Offset(inset, 1.dp.toPx()),
                end = Offset(size.width - inset, 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .border(1.dp, rim, shape)
}

/**
 * HazeState "root" milik seluruh UI app (dipasang di MainActivity). Dialog
 * (jendela terpisah) memakainya untuk mem-blur isi layar di belakangnya.
 * null = blur tidak tersedia (mis. preview) -> panel jatuh ke tint pekat.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Opacity tint Liquid Glass. Dikeluarkan sebagai helper bersama supaya kartu,
 * dialog, drawer, dan navbar memiliki rentang visual yang konsisten.
 * 0 = sangat bening, 1 = hampir solid. Radius blur tetap diatur terpisah.
 */
fun liquidGlassTintAlpha(level: Float): Float =
    0.04f + 0.80f * level.coerceIn(0f, 1f)

/** Radius blur (dp) dialog/navbar dari slider 0f..1f. 0 berarti blur dimatikan. */
fun blurRadiusFor(blur: Float): Float = 6f + 30f * blur

/** Radius card lebih kecil agar Liquid Glass tetap berwarna tanpa memaksa blur radius besar. */
fun cardBlurRadiusFor(blur: Float): Float = 6f + 22f * blur

/**
 * Panel blur serbaguna (dialog, drawer). Isi di belakang panel di-BLUR
 * sungguhan lewat Haze (`hazeEffect`), lalu diberi tint yang dikendalikan oleh
 * Liquid Glass Level. Slider Blur Intensity menentukan radius blur secara terpisah;
 * bila blur tidak tersedia, permukaan tetap memakai tint sebagai fallback.
 *
 * [hazeState] harus berupa state yang sumbernya (`hazeSource`) adalah
 * SIBLING panel ini, bukan induknya. Dialog memakai state root.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    minAlpha: Float = 0.04f,
    hazeState: HazeState? = LocalHazeState.current,
    /** Pengali radius blur (drawer memakai > 1 supaya isi di belakangnya benar-benar kabur). */
    blurBoost: Float = 1f,
    /** Pengali kepekatan tint (< 1 = lebih bening, blur lebih terlihat). */
    tintScale: Float = 1f,
    /**
     * true = tint digambar SETELAH blur sehingga level opacity tetap terlihat.
     * false = tint di bawah blur untuk kasus khusus yang memerlukan perilaku lama.
     */
    tintAfterBlur: Boolean = true,
): Modifier {
    val p = LocalAetherPalette.current
    val style = LocalAetherStyle.current
    val b = style.blur
    val glass = style.glassLevel
    val blurOn = hazeState != null && b > 0.01f
    val radius = (blurRadiusFor(b) * blurBoost).coerceAtMost(38f).dp
    // Liquid Glass Level controls the post-blur tint over a broad opacity range.
    // Previously minAlpha=0.30f flattened the first two stops into almost the same
    // value, so users could not see any meaningful change at low levels.
    val levelAlpha = liquidGlassTintAlpha(glass)
    val floor = minAlpha.coerceIn(0.02f, 0.12f)
    val top = if (blurOn) (levelAlpha * tintScale).coerceAtLeast(floor).coerceAtMost(0.94f) else (0.30f + 0.67f * glass)
    val bottom = if (blurOn) (levelAlpha * 0.82f * tintScale).coerceAtLeast(floor * 0.82f).coerceAtMost(0.90f) else (0.25f + 0.70f * glass)
    val tint = Brush.verticalGradient(
        listOf(p.surface.copy(alpha = top), p.surface.copy(alpha = bottom)),
    )
    return this
        .clip(shape)
        // Default tint berada di atas hasil Haze supaya perubahan Liquid Glass
        // Level tetap terlihat. Kasus khusus boleh memilih tint sebelum Haze.
        .then(if (tintAfterBlur) Modifier else Modifier.background(tint))
        .then(
            if (blurOn) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = radius
                    noiseFactor = 0.03f
                    // Keep Haze's baseline tint low. Level-specific tint is painted
                    // as a separate background after the effect to remain visible.
                    backgroundColor = p.surface.copy(alpha = 0.035f)
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
 * Ambient grid background (tanpa warna dasar). Dipakai oleh [AetherBackground] dan
 * [AmbientGridBlurLayer], sehingga backdrop yang dibaca elemen Liquid Glass selalu identik.
 */
fun DrawScope.drawAetherGridLayers(
    w: Float,
    h: Float,
    phase: Float,
    accent: Color,
    soft: Color,
    accentMid: Color,
    dark: Boolean,
) {
    if (w <= 0f || h <= 0f) return

    // Quiet, premium grid: the base itself remains pure theme black/white-ish;
    // the motion lives in the grid offset and an extremely soft moving glow.
    val p = phase.coerceIn(0f, 1f)
    val angle = p.toDouble() * Math.PI * 2.0
    val cell = 42.dp.toPx()
    val halfCell = cell / 2f
    val shiftX = (kotlin.math.sin(angle).toFloat() * cell * 0.12f) + (p - 0.5f) * cell * 0.10f
    val shiftY = kotlin.math.cos(angle * 0.62).toFloat() * cell * 0.07f
    val minorColor = (if (dark) Color.White else Color(0xFF343746)).copy(alpha = if (dark) 0.032f else 0.040f)
    val majorColor = (if (dark) Color.White else Color(0xFF343746)).copy(alpha = if (dark) 0.058f else 0.060f)
    val minorStroke = 0.65.dp.toPx()
    val majorStroke = 0.95.dp.toPx()

    // A restrained glow drifts behind the grid, never becoming an opaque blob.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = if (dark) 0.075f else 0.060f),
                soft.copy(alpha = if (dark) 0.025f else 0.018f),
                Color.Transparent,
            ),
            center = Offset(
                x = w * (0.16f + 0.68f * p),
                y = h * (0.22f + 0.13f * kotlin.math.sin(angle).toFloat()),
            ),
            radius = maxOf(w, h) * 0.72f,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, accentMid.copy(alpha = if (dark) 0.018f else 0.012f), Color.Transparent),
            center = Offset(w * (0.82f - 0.50f * p), h * 0.82f),
            radius = maxOf(w, h) * 0.48f,
        ),
    )

    val startXIndex = kotlin.math.floor((-shiftX) / halfCell).toInt()
    var gx = startXIndex
    while (true) {
        val x = gx * halfCell + shiftX
        if (x > w + halfCell) break
        val isMajor = gx % 2 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = if (isMajor) majorStroke else minorStroke,
        )
        gx++
    }

    val startYIndex = kotlin.math.floor((-shiftY) / halfCell).toInt()
    var gy = startYIndex
    while (true) {
        val y = gy * halfCell + shiftY
        if (y > h + halfCell) break
        val isMajor = gy % 2 == 0
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = if (isMajor) majorStroke else minorStroke,
        )
        gy++
    }

    // Sparse intersection points create depth without turning the background into a pattern-heavy wallpaper.
    val dotStep = cell * 2f
    val firstDotX = kotlin.math.floor((-shiftX) / dotStep).toInt()
    val firstDotY = kotlin.math.floor((-shiftY) / dotStep).toInt()
    for (ix in firstDotX..(kotlin.math.ceil((w - shiftX) / dotStep).toInt())) {
        for (iy in firstDotY..(kotlin.math.ceil((h - shiftY) / dotStep).toInt())) {
            if ((ix + iy) % 4 != 0) continue
            val x = ix * dotStep + shiftX
            val y = iy * dotStep + shiftY
            val pulse = (kotlin.math.sin(angle + ix * 0.73 + iy * 0.51).toFloat() + 1f) * 0.5f
            drawCircle(
                color = accent.copy(alpha = (0.045f + pulse * 0.055f) * (if (dark) 1f else 0.82f)),
                radius = (0.8f + pulse * 0.65f).dp.toPx(),
                center = Offset(x, y),
            )
        }
    }

    // A low-contrast scan line glides slowly across the grid and gives the background life.
    val scanX = w * (0.12f + 0.76f * p)
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, accent.copy(alpha = if (dark) 0.055f else 0.040f), Color.Transparent),
            startX = scanX - 26.dp.toPx(),
            endX = scanX + 26.dp.toPx(),
        ),
        start = Offset(scanX, 0f),
        end = Offset(scanX, h),
        strokeWidth = 1.dp.toPx(),
    )
}

/** Fase animasi grid ambient (0f..1f, bolak-balik). Dibaca saat menggambar latar. */
class AmbientGridBackdrop(val motion: State<Float>)

val LocalAmbientGridBackdrop = staticCompositionLocalOf<AmbientGridBackdrop?> { null }

/**
 * Salinan lapisan grid ambient yang di-BLUR sungguhan (RenderEffect bawaan Compose, API 31+),
 * tanpa Haze dan tanpa bergantung pada flag blur OS. Grid ambient digambar ulang persis
 * pada posisi layar yang sama dengan latar asli, lalu di-blur dan dipotong ke [shape].
 * Pakai sebagai latar kaca (mis. navbar); beri tint tipis di atasnya.
 */
@Composable
fun AmbientGridBlurLayer(
    blurRadius: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val backdrop = LocalAmbientGridBackdrop.current
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
                        drawAetherGridLayers(
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
 * Latar app minimal dengan grid kotak halus, offset animasi lambat, titik persimpangan
 * yang sangat redup, dan cahaya aksen yang bergerak pelan. Warna dasar tetap mengikuti
 * tema terang/gelap, tanpa pita aurora tebal atau blur tambahan. Lapisan ini tetap menjadi
 * sumber backdrop Haze dan Liquid Glass untuk kartu, drawer, serta navbar.
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
    // One recorded backdrop source is shared by all Backdrop-based liquid controls.
    val liquidBackdrop = rememberLayerBackdrop()

    // Low-RAM phones keep the same quiet grid but stop repainting the background
    // continuously. Regular devices retain slow ambient motion.
    val context = LocalContext.current.applicationContext
    val lowRamDevice = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memoryInfo)
        manager?.isLowRamDevice == true || memoryInfo.totalMem in 1L..3_500_000_000L
    }
    val motionState: State<Float> = if (lowRamDevice) {
        remember { mutableStateOf(0.48f) }
    } else {
        val motionTransition = rememberInfiniteTransition(label = "aether-background-motion")
        motionTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "aether-background-phase",
        )
    }
    val motion by motionState
    val ambientGridBackdrop = remember(motionState) { AmbientGridBackdrop(motionState) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop)
                .hazeSource(cardHaze)
                .hazeSource(backdropHaze, zIndex = 0f)
                .background(bg)
                .drawBehind { drawAetherGridLayers(size.width, size.height, motion, accent, soft, accentMid, dark) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalCardHaze provides cardHaze,
                LocalBackdropHaze provides backdropHaze,
                LocalAmbientGridBackdrop provides ambientGridBackdrop,
                LocalAetherLiquidBackdrop provides liquidBackdrop,
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
