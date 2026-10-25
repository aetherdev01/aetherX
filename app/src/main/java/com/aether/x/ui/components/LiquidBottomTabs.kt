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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
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
    // Level 0 is the clearest glass; as the tint gets more solid, refraction fades.
    val lensScale = (1f - glass).coerceIn(0f, 1f)
    val accentColor = palette.accent
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
                    val width = tabWidth.coerceAtLeast(1f)
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
                        if (lensScale > 0.001f) {
                            lens(24f.dp.toPx() * lensScale, 24f.dp.toPx() * lensScale)
                        }
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }) {
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
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            if (style.blur > 0.01f) {
                                blur(blurRadiusFor(style.blur.coerceIn(0f, 1f)).dp.toPx())
                            }
                            // Keep the duplicate backdrop in sync with settings, while
                            // preserving some refraction at rest (not only while pressed).
                            if (lensScale > 0.001f) {
                                val lensWidth = (18f + 6f * progress) * lensScale
                                val lensHeight = (20f + 8f * progress) * lensScale
                                lens(lensWidth.dp.toPx(), lensHeight.dp.toPx())
                            }
                        },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
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
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // Refractive colour fringe remains visible while idle, not only
                        // during a press. More-solid glass reduces this effect via settings.
                        if (lensScale > 0.001f) {
                            // Refraction grows while the capsule is held or sliding.
                            val lensWidth = (18f + 10f * progress) * lensScale
                            val lensHeight = (22f + 14f * progress) * lensScale
                            lens(
                                lensWidth.dp.toPx(),
                                lensHeight.dp.toPx(),
                                chromaticAberration = true,
                            )
                        }
                    },
                    highlight = {
                        val strength = (0.26f + 0.42f * lensScale + 0.20f * dampedDragAnimation.pressProgress)
                            .coerceIn(0.18f, 0.90f)
                        Highlight.Default.copy(alpha = strength)
                    },
                    shadow = {
                        Shadow(alpha = 0.12f + 0.48f * dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = (4f + 5f * progress).dp * lensScale,
                            alpha = (0.12f + 0.24f * lensScale + 0.48f * progress).coerceIn(0f, 0.9f),
                        )
                    },
                    layerBlock = {
                        // Idle: capsule stays within the navbar height. While held/sliding,
                        // it grows past the navbar edges like a raised liquid lens.
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        scaleX = 1.06f + 0.17f * progress + (dampedDragAnimation.scaleX - 1f) * 0.10f
                        scaleY = 0.96f + 0.38f * progress + (dampedDragAnimation.scaleY - 1f) * 0.06f
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        val idleTint = palette.surface.copy(alpha = 0.07f + 0.12f * glass)
                        drawRect(idleTint, alpha = 1f - progress)
                        drawRect(palette.accent.copy(alpha = 0.035f * lensScale * progress))
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount),
        )
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
