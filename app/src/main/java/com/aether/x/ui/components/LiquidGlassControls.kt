/*
 * AetherX integration of the component patterns from Kyant0/AndroidLiquidGlass.
 * Upstream component reference (KMP branch):
 * https://github.com/Kyant0/AndroidLiquidGlass/tree/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components
 * The shared library engine is io.github.kyant0:backdrop.
 */
package com.aether.x.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** Shared Backdrop recorded from the AetherX aurora background. */
val LocalAetherLiquidBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }

/** Returns the real screen backdrop; dialog windows use a safe local canvas instead. */
@Composable
fun rememberAetherLiquidBackdrop(): Backdrop {
    val shared = LocalAetherLiquidBackdrop.current
    val palette = LocalAetherPalette.current
    val isSeparateWindow = LocalView.current.parent is DialogWindowProvider
    val fallback = rememberCanvasBackdrop {
        drawRect(palette.bg)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.22f),
                    palette.accentSoft.copy(alpha = 0.09f),
                    Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
    return if (isSeparateWindow) fallback else (shared ?: fallback)
}

private fun effectStrength(glassLevel: Float): Float = glassLevel.coerceIn(0f, 1f)

/**
 * Liquid Toggle following the repository's spring-driven track/thumb implementation.
 * Width, height, drag rubberiness, highlight and lens all use the upstream component pattern.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedState = rememberUpdatedState(checked)
    val onChangeState = rememberUpdatedState(onCheckedChange)
    val enabledState = rememberUpdatedState(enabled)
    val selected = remember { { selectedState.value } }
    val onSelect = remember {
        { value: Boolean -> if (enabledState.value) onChangeState.value(value) }
    }
    ToggleFromBackdrop(
        selected = selected,
        onSelect = onSelect,
        backdrop = rememberAetherLiquidBackdrop(),
        modifier = modifier.alpha(if (enabled) 1f else 0.48f),
        enabled = enabled,
    )
}

@Composable
private fun ToggleFromBackdrop(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val style = LocalAetherStyle.current
    val glass = style.glassLevel.coerceIn(0f, 1f)
    val accentColor = if (isLightTheme) Color(0xFF34C759) else Color(0xFF30D158)
    val baseTrack = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val trackColor = baseTrack.copy(alpha = baseTrack.alpha * (0.72f + 0.28f * glass))

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val selectedState = rememberUpdatedState(selected)
    val onSelectState = rememberUpdatedState(onSelect)
    val enabledState = rememberUpdatedState(enabled)
    val currentSelected = remember { { selectedState.value() } }
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (enabledState.value) {
                    if (didDrag) {
                        fraction = if (targetValue >= 0.5f) 1f else 0f
                        onSelectState.value(fraction == 1f)
                        didDrag = false
                    } else {
                        fraction = if (currentSelected()) 0f else 1f
                        onSelectState.value(fraction == 1f)
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentSelected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .background(lerp(trackColor, accentColor, dampedDragAnimation.value))
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val progress = dampedDragAnimation.value
                    val paddingPx = 2f.dp.toPx()
                    translationX = if (isLtr) lerp(paddingPx, paddingPx + dragWidth, progress)
                    else lerp(-paddingPx, -(paddingPx + dragWidth), progress)
                }
                .semantics {
                    role = Role.Switch
                    if (!enabledState.value) disabled()
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (style.blur > 0.01f) {
                            blur((2f + 15f * style.blur.coerceIn(0f, 1f)).dp.toPx() * (1f - progress))
                        }
                        val lensScale = effectStrength(glass)
                        lens(5f.dp.toPx() * progress * lensScale, 10f.dp.toPx() * progress * lensScale, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(40.dp, 24.dp),
        )
    }
}

/** Liquid Button adapted from the repository's interactive capsule button. */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: androidx.compose.foundation.BorderStroke? = null,
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = LiquidActionButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    contentPadding = contentPadding,
    colors = colors,
    border = border,
    isInteractive = isInteractive,
    outlined = false,
    content = content,
)

@Composable
fun LiquidOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: androidx.compose.foundation.BorderStroke = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    isInteractive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = LiquidActionButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    contentPadding = contentPadding,
    colors = colors,
    border = border,
    isInteractive = isInteractive,
    outlined = true,
    content = content,
)

@Composable
private fun LiquidActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    @Suppress("UNUSED_PARAMETER") shape: Shape,
    contentPadding: PaddingValues,
    colors: ButtonColors,
    border: androidx.compose.foundation.BorderStroke?,
    isInteractive: Boolean,
    outlined: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalAetherStyle.current
    val palette = LocalAetherPalette.current
    val glass = style.glassLevel.coerceIn(0f, 1f)
    val backdrop = rememberAetherLiquidBackdrop()
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
    // ButtonColors.containerColor/contentColor are internal in Material3 and cannot be read here.
    // Resolve the liquid surface from the Aether palette instead.
    val resolvedContentColor = if (outlined) palette.textPrimary else LocalContentColor.current
    val surfaceColor = if (outlined) {
        palette.surface.copy(alpha = 0.07f + 0.14f * glass)
    } else {
        palette.surface.copy(alpha = 0.12f + 0.20f * glass)
    }
    val resolvedBorder = border ?: if (outlined) {
        androidx.compose.foundation.BorderStroke(1.dp, palette.stroke.copy(alpha = 0.78f))
    } else null

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    if (style.blur > 0.01f) {
                        blur((0.5f + 2.5f * style.blur.coerceIn(0f, 1f)).dp.toPx())
                    }
                    val lensScale = effectStrength(glass)
                    lens(12f.dp.toPx() * lensScale, 24f.dp.toPx() * lensScale)
                },
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceIn(0f, 1f)
                        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceIn(0f, 1f)
                    }
                } else null,
                onDrawSurface = { drawRect(surfaceColor) },
            )
            .then(if (resolvedBorder != null) Modifier.border(resolvedBorder, Capsule()) else Modifier)
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (enabled && isInteractive) {
                    Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier)
                } else Modifier,
            )
            .height(48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) { content() }
    }
}

/**
 * Liquid slider with the glass capsule visual, but delegates pointer tracking to
 * Material 3's battle-tested Slider gesture implementation. The previous custom
 * drag modifier could lose pointer ownership inside nested scrollable/settings cards,
 * so the thumb looked draggable but never changed the setting on some screens.
 */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float = 0.001f,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val style = LocalAetherStyle.current
    val accentColor = activeColor ?: if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val fallbackTrack = if (isLightTheme) Color(0xFF787878).copy(alpha = 0.2f) else Color(0xFF787880).copy(alpha = 0.36f)
    val trackColor = inactiveColor ?: fallbackTrack
    val backdrop = rememberAetherLiquidBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val latestValueFunction = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
    val valueReader = remember { { latestValueFunction.value() } }

    fun quantize(raw: Float): Float {
        val min = valueRange.start
        val max = valueRange.endInclusive
        val span = (max - min).coerceAtLeast(0.0001f)
        val clamped = raw.coerceIn(min, max)
        if (steps <= 0) return clamped
        val intervalCount = (steps + 1).coerceAtLeast(1)
        val fraction = ((clamped - min) / span).coerceIn(0f, 1f)
        return (min + (fraction * intervalCount).fastRoundToInt() / intervalCount.toFloat() * span).coerceIn(min, max)
    }

    val currentValue = valueReader().coerceIn(valueRange.start, valueRange.endInclusive)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((currentValue - valueRange.start) / span).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = visibilityThreshold.coerceAtLeast(0.0001f),
        ),
        label = "liquidSliderProgress",
    )
    val glassScale = style.glassLevel.coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxWidth().height(52.dp)) {
        // Track and fill are recorded into their own backdrop for a subtle lens
        // distortion inside the thumb, while all blur/tint values share AetherStyle.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(6.dp)
                .layerBackdrop(trackBackdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(Capsule())
                    .background(trackColor),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(Capsule())
                    .background(accentColor),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - 40.dp).coerceAtLeast(0.dp) * animatedFraction)
                .size(40.dp, 24.dp)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop -> drawBackdrop() },
                    ),
                    shape = { Capsule() },
                    effects = {
                        if (style.blur > 0.01f) {
                            blur((2f + 15f * style.blur.coerceIn(0f, 1f)).dp.toPx())
                        }
                        lens(
                            10f.dp.toPx() * glassScale,
                            14f.dp.toPx() * glassScale,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = 0.52f,
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.16f) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.22f)) },
                ),
        )

        // Fully transparent visual layer; its standard Slider gesture/semantics
        // captures both thumb drags and taps across the full track.
        Slider(
            value = currentValue,
            onValueChange = { latestOnValueChange.value(quantize(it)) },
            modifier = Modifier.fillMaxSize(),
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { latestOnValueChangeFinished.value?.invoke() },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
