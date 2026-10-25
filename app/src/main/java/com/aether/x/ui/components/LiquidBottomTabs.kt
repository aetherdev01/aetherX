/*
 * Adapted from Kyant0/AndroidLiquidGlass KMP catalog components (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 */
package com.aether.x.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
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

internal val LocalLiquidBottomTabReadOnly = staticCompositionLocalOf { false }

/** Continuous selection progress driven by the moving capsule, avoiding stale tab colors. */
internal val LocalLiquidBottomTabSelectionProgress =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

/** Press callback shared by the actual tab row; visual-only copies never invoke it. */
internal val LocalLiquidBottomTabPressCallback =
    staticCompositionLocalOf<(Int, Boolean) -> Unit> { { _, _ -> } }

/** Interaction progress used only to scale the icon/label content inside the moving capsule. */
internal val LocalLiquidBottomTabPressProgress =
    staticCompositionLocalOf { 0f }

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
    // Glass Level changes only the capsule tint/opacity. Its geometry and lens recipe stay fixed.
    // Keep a visible minimum tint so the idle capsule does not disappear over the navbar.
    val capsuleTintAlpha = (0.38f + 0.60f * glass).coerceIn(0.38f, 0.98f)
    val containerAlpha = liquidGlassTintAlpha(glass)
    val containerColor = palette.surface.copy(alpha = containerAlpha)
    val capsuleSurfaceColor = palette.surface.copy(alpha = capsuleTintAlpha)
    val navbarShape = remember { RoundedCornerShape(30.dp) }
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
        var navbarPressed by remember { androidx.compose.runtime.mutableStateOf(false) }
        val navbarPressProgress by animateFloatAsState(
            targetValue = if (navbarPressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.48f, stiffness = 520f),
            label = "navbar-spring-press",
        )
        var notifyParentAfterDrag by remember { androidx.compose.runtime.mutableStateOf(false) }
        var didDrag by remember { androidx.compose.runtime.mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabReader().coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 68f / 48f,
                onDragStarted = { navbarPressed = true },
                onDragStopped = {
                    // A simple tap is handled by the tab's clickable. Only a real horizontal
                    // drag may commit the drag target; this prevents tap/drag state races.
                    if (didDrag) {
                        val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        if (targetIndex != currentIndex) {
                            notifyParentAfterDrag = true
                            currentIndex = targetIndex
                        }
                        animateToValue(targetIndex.toFloat())
                        animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    }
                    didDrag = false
                    navbarPressed = false
                },
                onDrag = { _, dragAmount ->
                    if (abs(dragAmount.x) > 0.5f) {
                        didDrag = true
                        val width = tabWidth.coerceAtLeast(1f)
                        updateValue((targetValue + dragAmount.x / width * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                        animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
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
        val tabPressCallback: (Int, Boolean) -> Unit = { index, pressed ->
            if (index in 0 until tabsCount) {
                if (pressed) {
                    navbarPressed = true
                    // Move the capsule on press-down; do not wait for the click/up event.
                    dampedDragAnimation.press()
                    dampedDragAnimation.updateValue(index.toFloat())
                } else {
                    navbarPressed = false
                    dampedDragAnimation.release()
                }
            }
        }
        val navbarGradient = remember(containerAlpha, palette.surface) {
            Brush.verticalGradient(
                colors = listOf(
                    palette.surface.copy(alpha = containerAlpha * 0.62f),
                    palette.surface.copy(alpha = containerAlpha * 0.92f),
                    palette.surface.copy(alpha = containerAlpha * 0.68f),
                ),
            )
        }

        // Glass shell is separated from the tab content so its spring deformation never scales
        // icons/labels. The backdrop remains translucent and samples the real elements behind it.
        Box(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                    val press = navbarPressProgress.coerceIn(0f, 1f)
                    scaleX = 1f + 0.012f * press
                    scaleY = 1f - 0.032f * press
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { navbarShape },
                    effects = {
                        vibrancy()
                        if (style.blur > 0.01f) {
                            blur(blurRadiusFor(style.blur.coerceIn(0f, 1f)).dp.toPx())
                        }
                    },
                    onDrawSurface = { drawRect(brush = navbarGradient) },
                )
                .then(interactiveHighlight.modifier)
                .height(60f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
            LocalLiquidBottomTabPressCallback provides tabPressCallback,
            LocalLiquidBottomTabPressProgress provides dampedDragAnimation.pressProgress,
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(60f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
            LocalLiquidBottomTabReadOnly provides true,
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { navbarShape },
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
                    .height(52f.dp)
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
                // The moving capsule owns press/drag gestures, like the upstream implementation.
                // This avoids the foreground icon layer swallowing horizontal drags.
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { navbarShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // Stronger lens + chromatic separation, enabled only while pressed/sliding.
                        if (progress > 0.001f) {
                            lens(
                                16f.dp.toPx() * progress,
                                24f.dp.toPx() * progress,
                                chromaticAberration = true,
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // Keep the idle capsule outlined/visible; interaction increases the rim.
                        Highlight.Default.copy(alpha = 0.28f + 0.44f * progress)
                    },
                    shadow = {
                        Shadow(alpha = 0.16f + 0.42f * dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        // Preserve the original spring deformation. At rest both scales are 1,
                        // so the capsule is not skewed; press makes it taller than the 60dp bar.
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // The capsule is solid/tinted at rest. On interaction its fill fades away,
                        // exposing the sampled navbar layer inside the liquid lens.
                        drawRect(capsuleSurfaceColor, alpha = 1f - progress)
                        if (progress > 0f) {
                            drawRect(palette.accent.copy(alpha = 0.035f * progress))
                        }
                    },
                )
                .border(
                    width = 1.dp,
                    color = palette.stroke.copy(alpha = 0.42f),
                    shape = navbarShape,
                )
                .height(48f.dp)
                .fillMaxWidth(1f / tabsCount),
        )

        // Foreground controls stay crisp and saturated above the optical capsule. This copy is
        // visual only; accessibility/taps remain on the real row and drag stays on the capsule.
        CompositionLocalProvider(
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
            LocalLiquidBottomTabPressProgress provides dampedDragAnimation.pressProgress,
            // This final crisp copy is visual only; taps are handled by the real row below it
            // and drags are handled by the moving capsule above the backdrop source.
            LocalLiquidBottomTabReadOnly provides true,
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(60f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .clearAndSetSemantics {},
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
    index: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isReadOnlyCopy = LocalLiquidBottomTabReadOnly.current
    val pressCallback = LocalLiquidBottomTabPressCallback.current
    val selectionProgress = LocalLiquidBottomTabSelectionProgress.current(index)
    val pressProgress = LocalLiquidBottomTabPressProgress.current.coerceIn(0f, 1f)
    // Only the tab content currently sitting under the liquid capsule scales. The navbar and
    // inactive tabs remain at their normal size. The moving capsule's spring drives the progress.
    val contentScale = 1f + (0.085f * selectionProgress * pressProgress)
    Column(
        modifier
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            }
            .clip(Capsule())
            // The real tab animates the capsule on press-down. Read-only backdrop/visual copies
            // have no pointer handler and cannot steal taps or start duplicate animations.
            .then(
                if (isReadOnlyCopy) Modifier
                else Modifier.pointerInput(index) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressCallback(index, true)
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            pressCallback(index, false)
                        }
                    }
                },
            )
            .then(
                if (isReadOnlyCopy) Modifier
                else Modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
            )
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
