/*
 * Adapted from Kyant0/AndroidLiquidGlass KMP catalog components (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 */
package com.aether.x.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.LocalAetherPalette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

// Keep optical strength stable across Clear/Thin/Balanced/Dense/Solid.
// Liquid Glass Level only changes tint/opacity.
private const val NAVBAR_LENS_STRENGTH = 0.62f

/** Blur minimum kaca navbar (dp). Elemen di belakang navbar selalu ter-blur, tidak pernah tajam. */
internal const val NAV_MIN_BLUR_DP = 18f

/** Continuous selection progress driven by the moving capsule, avoiding stale tab colors. */
internal val LocalLiquidBottomTabSelectionProgress =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

/** State gesture non-snapshot; hanya disentuh dari callback gesture. */
private class TabDragState {
    var target = 0f
    var travelled = 0f
}

/**
 * Liquid BottomTabs (pola capsule/pill Kyant0/AndroidLiquidGlass).
 *
 * Interaksi:
 * - Tekan/tahan di tab mana pun: kapsul langsung berjalan (spring) ke tab itu tanpa menunggu lepas.
 * - Geser: kapsul mengikuti jari. Lepas: pindah ke tab terdekat dan [onTabSelected] dipanggil.
 * - Tap singkat: sama dengan tahan lalu lepas (juga memanggil [onTabSelected] untuk tab yang sama).
 *
 * Tinggi kapsul = [barHeight] - 8dp (inset 4dp rata di semua sisi, konsentris dengan navbar).
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 56f.dp,
    content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount <= 0) return
    val style = LocalAetherStyle.current
    val palette = LocalAetherPalette.current
    val glass = style.glassLevel.coerceIn(0f, 1f)
    val capsuleHeight = barHeight - 8f.dp
    val sizeFactor = capsuleHeight.value / 56f
    // Idle capsule: warna KONTRAS + wash accent supaya selalu terlihat di semua Glass Level.
    val idleCapsuleFill = palette.textPrimary.copy(alpha = 0.10f + 0.06f * glass)
    val idleCapsuleAccent = palette.accent.copy(alpha = 0.12f)
    val containerColor = palette.surface.copy(alpha = liquidGlassTintAlpha(glass))
    val navBlurDp = maxOf(blurRadiusFor(style.blur.coerceIn(0f, 1f)), NAV_MIN_BLUR_DP)
    // Gradient tint vertikal: atas lebih bening, bawah lebih pekat.
    val containerBrush = Brush.verticalGradient(
        listOf(
            containerColor.copy(alpha = (containerColor.alpha * 0.70f).coerceIn(0f, 1f)),
            containerColor.copy(alpha = (containerColor.alpha * 1.15f).coerceIn(0f, 1f)),
        ),
    )
    val tabsBackdrop = rememberLayerBackdrop()
    val selectedTabReaderState = rememberUpdatedState(selectedTabIndex)
    val onTabSelectedState = rememberUpdatedState(onTabSelected)
    val selectedTabReader = remember { { selectedTabReaderState.value() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }
        val tapSlopPx = with(density) { 8f.dp.toPx() }
        val tabWidthState = rememberUpdatedState(tabWidth)
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val fraction = (offsetAnimation.value / width).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabReader) { mutableIntStateOf(selectedTabReader()) }
        val drag = remember { TabDragState() }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabReader().coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { position ->
                    // Tahan di tab tujuan -> kapsul langsung "berjalan" ke sana (tanpa menunggu lepas).
                    val w = tabWidthState.value.coerceAtLeast(1f)
                    val column = (position.x / w).toInt().coerceIn(0, tabsCount - 1)
                    val index = if (isLtr) column else tabsCount - 1 - column
                    drag.target = index.toFloat()
                    drag.travelled = 0f
                    updateValue(drag.target)
                },
                onDragStopped = {
                    val targetIndex = drag.target.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    val isTap = drag.travelled < tapSlopPx
                    val changed = targetIndex != currentIndex
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    // Tap pada tab yang sama tetap diteruskan (mis. Dashboard mereset sub-tab).
                    if (changed || isTap) onTabSelectedState.value(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    val w = tabWidthState.value.coerceAtLeast(1f)
                    drag.travelled += abs(dragAmount.x)
                    drag.target = (drag.target + dragAmount.x / w * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    updateValue(drag.target)
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        // Perubahan dari luar (mis. navigasi programatik) menggerakkan kapsul.
        LaunchedEffect(selectedTabReader) {
            snapshotFlow { selectedTabReader() }.collectLatest { index -> currentIndex = index.coerceIn(0, tabsCount - 1) }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                if (abs(dampedDragAnimation.targetValue - index) > 0.001f) {
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
            }
        }

        // Track the capsule position continuously; tab icon/label colors use this progress.
        val selectionProgress: (Int) -> Float = { index ->
            (1f - abs(dampedDragAnimation.value - index.toFloat())).coerceIn(0f, 1f)
        }

        // Warna base row mengikuti tab yang sedang di bawah kapsul saja (tanpa pewarnaan setengah).
        val baseSelectionProgress: (Int) -> Float = { index ->
            if (dampedDragAnimation.value.fastRoundToInt() == index) 1f else 0f
        }

        // Base row: navbar kaca. Ikon di luar kapsul TIDAK ikut membesar saat ditahan.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { 1f },
            LocalLiquidBottomTabSelectionProgress provides baseSelectionProgress,
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(navBlurDp.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerBrush) },
                    )
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // Layer "pita" navbar untuk lensa: salinan navbar yang LEBIH KECIL (setinggi kapsul idle)
        // + ikon/label membesar 1.2x dan SEMUA berwarna accent. Tidak terlihat langsung (alpha 0);
        // hanya tampak lewat lensa kapsul, jadi ikon/label yang dilewati kapsul ikut berwarna.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
            LocalLiquidBottomTabSelectionProgress provides { 1f },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                            vibrancy()
                            blur(navBlurDp.dp.toPx())
                            if (progress > 0.001f) {
                                lens(
                                    14f.dp.toPx() * progress * sizeFactor,
                                    14f.dp.toPx() * progress * sizeFactor,
                                )
                            }
                        },
                        // Rim tipis seperti navbar asli supaya bentuk pita terbaca di dalam kapsul.
                        highlight = { Highlight.Default.copy(alpha = 0.55f) },
                        onDrawSurface = {
                            // Tint yang sama dengan navbar asli (bening), cukup dibedakan dari latar
                            // di dalam kapsul lewat rim highlight + sedikit angkat warna.
                            drawRect(containerBrush)
                            drawRect(palette.textPrimary.copy(alpha = 0.05f))
                        },
                    )
                    .height(capsuleHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                    else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // Chromatic lens hanya saat interaksi. Pembiasan sengaja TIPIS (< celah antara
                        // tepi kapsul dan pita navbar ~9dp): lensa yang kuat menarik isi pita dari
                        // dalam sampai menutupi celah, sehingga pita terlihat memenuhi kapsul.
                        // Pembesaran ikon berasal dari skala 1.2x pada pita, bukan dari lensa.
                        if (progress > 0.001f) {
                            lens(
                                8f.dp.toPx() * progress * sizeFactor,
                                6f.dp.toPx() * progress * sizeFactor,
                                chromaticAberration = true,
                            )
                        }
                    },
                    // Tanpa highlight/glow putih di tepi kapsul.
                    highlight = { Highlight.Default.copy(alpha = 0f) },
                    shadow = {
                        Shadow(alpha = 0.10f + 0.48f * dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        // Skala pegas penuh (underdamped, overshoot) seragam di X/Y supaya lengkung
                        // kapsul tetap sebangun dengan navbar. Idle = 1f (inset 4dp rata).
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        val idle = 1f - progress
                        // Idle: warna kontras agar kapsul terlihat. Ditahan: memudar -> pita navbar
                        // yang dibiaskan lensa terlihat di dalam kapsul.
                        drawRect(idleCapsuleFill, alpha = idle)
                        drawRect(idleCapsuleAccent, alpha = idle)
                        if (progress > 0f) {
                            drawRect(palette.accent.copy(alpha = 0.025f * progress))
                        }
                    },
                )
                .height(capsuleHeight)
                .fillMaxWidth(1f / tabsCount),
        )

        // Foreground: ikon tajam untuk idle + SATU-SATUNYA penangkap gesture (tab tidak lagi
        // memakai clickable, karena gesture induk meng-consume gerakan kecil dan membatalkan klik).
        // Memudar saat ditahan agar lensa di dalam kapsul terlihat; tetap hit-testable.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { 1f },
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        alpha = 1f - dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                    }
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .then(dampedDragAnimation.modifier),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** One selectable tab used inside LiquidBottomTabs. Sentuhan ditangani oleh LiquidBottomTabs. */
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            // Hanya semantics (TalkBack); tidak menambah pointer input.
            .semantics {
                role = Role.Tab
                semanticsOnClick(action = { onClick(); true })
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val targetScale = scale()
                scaleX = targetScale
                scaleY = targetScale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * Mewarnai konten tab dari warna dasar ke [to] mengikuti posisi kapsul, di fase DRAW.
 * Sebelumnya warna dihitung di composition sehingga setiap frame animasi memicu recomposition
 * semua tab di ketiga layer (berat, terasa tidak responsif). Konten tab harus digambar dengan
 * warna dasar (mis. textSecondary); modifier ini meng-crossfade-nya ke [to].
 */
@Composable
fun Modifier.liquidTabTint(index: Int, to: Color): Modifier {
    val progressOf = LocalLiquidBottomTabSelectionProgress.current
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val p = progressOf(index).coerceIn(0f, 1f)
            if (p > 0f) drawRect(to.copy(alpha = to.alpha * p), blendMode = BlendMode.SrcAtop)
        }
}
