/*
 * Adapted from Kyant0/AndroidLiquidGlass KMP catalog components (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 */
package com.aether.x.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
// This is the Balanced-style lens amount; Liquid Glass Level only changes tint/opacity.
private const val NAVBAR_LENS_STRENGTH = 0.62f

/** Continuous selection progress driven by the moving capsule, avoiding stale tab colors. */
internal val LocalLiquidBottomTabSelectionProgress =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

/** Liquid BottomTabs copied from the library's capsule/pill interaction pattern. */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount <= 0) return
    val style = LocalAetherStyle.current
    val palette = LocalAetherPalette.current
    val glass = style.glassLevel.coerceIn(0f, 1f)
    // Idle capsule memakai warna KONTRAS (textPrimary + wash accent), bukan palette.surface yang
    // sama dengan navbar, supaya selalu terlihat di semua Glass Level. Lens & geometri tetap.
    val idleCapsuleFill = palette.textPrimary.copy(alpha = 0.10f + 0.06f * glass)
    val idleCapsuleAccent = palette.accent.copy(alpha = 0.12f)
    val containerAlpha = liquidGlassTintAlpha(glass)
    val containerColor = palette.surface.copy(alpha = containerAlpha)
    val tabsBackdrop = rememberLayerBackdrop()
    val selectedTabReaderState = rememberUpdatedState(selectedTabIndex)
    val onTabSelectedState = rememberUpdatedState(onTabSelected)
    val selectedTabReader = remember { { selectedTabReaderState.value() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val fraction = (offsetAnimation.value / width).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val panelOffsetState = rememberUpdatedState(panelOffset)
        val tabWidthState = rememberUpdatedState(tabWidth)
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabReader) { mutableIntStateOf(selectedTabReader()) }
        var notifyParentAfterDrag by remember { androidx.compose.runtime.mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabReader().coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (targetIndex != currentIndex) {
                        notifyParentAfterDrag = true
                        currentIndex = targetIndex
                    }
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    val width = tabWidthState.value.coerceAtLeast(1f)
                    updateValue((targetValue + dragAmount.x / width * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabReader) {
            snapshotFlow { selectedTabReader() }.collectLatest { index -> currentIndex = index.coerceIn(0, tabsCount - 1) }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                if (notifyParentAfterDrag) {
                    notifyParentAfterDrag = false
                    onTabSelectedState.value(index)
                }
            }
        }

        val interactiveHighlight = remember(animationScope, tabWidth) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffsetState.value
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffsetState.value,
                        size.height / 2f,
                    )
                },
            )
        }

        // Track the capsule position continuously; tab icon/label colors use this progress.
        // That keeps color transitions synchronized with sliding instead of waiting for drag end.
        val selectionProgress: (Int) -> Float = { index ->
            (1f - abs(dampedDragAnimation.value - index.toFloat())).coerceIn(0f, 1f)
        }

        // Base row: ikon di luar kapsul TIDAK ikut membesar saat ditahan.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { 1f },
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            if (style.blur > 0.01f) {
                                blur(blurRadiusFor(style.blur.coerceIn(0f, 1f)).dp.toPx())
                            }
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(64f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // Layer yang dibiaskan lensa: hanya ini yang membesar (1.2x), dan hanya terlihat di dalam kapsul.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
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
                            vibrancy()
                            if (style.blur > 0.01f) {
                                blur(blurRadiusFor(style.blur.coerceIn(0f, 1f)).dp.toPx())
                            }
                        },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
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
                        // Chromatic lens is interaction-only and always uses Balanced-like strength.
                        if (progress > 0.001f) {
                            val lensWidth = (24f + 8f * progress) * NAVBAR_LENS_STRENGTH * progress
                            val lensHeight = (28f + 10f * progress) * NAVBAR_LENS_STRENGTH * progress
                            lens(
                                lensWidth.dp.toPx(),
                                lensHeight.dp.toPx(),
                                chromaticAberration = true,
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        Highlight.Default.copy(alpha = 0.30f + 0.50f * progress)
                    },
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
                        // Skala pegas penuh (underdamped, ada overshoot) seperti upstream, seragam di X/Y
                        // supaya lengkung kapsul tetap sebangun dengan navbar. Idle = 1f (inset 4dp rata).
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // Idle shows a stable capsule tint. During interaction the fill fades
                        // so the navbar backdrop is visible through the moving lens.
                        drawRect(idleCapsuleFill, alpha = 1f - progress)
                        drawRect(idleCapsuleAccent, alpha = 1f - progress)
                        if (progress > 0f) {
                            drawRect(palette.accent.copy(alpha = 0.025f * progress))
                        }
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount),
        )

        // Foreground controls stay crisp and saturated above the optical capsule. Keeping the
        // gestures on this top row also avoids the glass layer intercepting tab taps/drags.
        // Foreground crisp hanya untuk idle: memudar saat ditahan agar isi navbar yang dibiaskan
        // lensa di dalam kapsul terlihat (sebelumnya tertutup ikon tajam ini). Tetap hit-testable.
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
                    .height(64f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** One selectable tab used inside LiquidBottomTabs. */
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
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
