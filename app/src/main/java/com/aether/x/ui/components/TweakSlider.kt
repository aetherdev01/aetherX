package com.aether.x.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs

/**
 * Liquid-glass slider inspired by the iOS 26 reference.
 * The native Material slider remains underneath so dragging, semantics and
 * accessibility stay intact; the visual thumb is a separate, animated glass pill.
 */
@Composable
fun TweakSlider(
    label: String,
    description: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current
    val palette = LocalAetherPalette.current
    val style = LocalAetherStyle.current
    val haze = LocalBackdropHaze.current ?: LocalCardHaze.current ?: LocalHazeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isDragging by remember { mutableStateOf(false) }
    val stretchTarget = when {
        isDragging -> 1f
        isPressed -> 0.62f
        else -> 0f
    }
    val stretch by animateFloatAsState(
        targetValue = stretchTarget,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 470f),
        label = "liquidGlassSliderStretch",
    )
    var lastReportedValue by remember { mutableFloatStateOf(value) }
    var dragDirection by remember { mutableIntStateOf(1) }
    val glassShape = RoundedCornerShape(percent = 50)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, bottom = 7.dp, end = 8.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            val horizontalInset = 12.dp
            val usableSpan = (maxWidth - horizontalInset * 2f).coerceAtLeast(0.dp)
            val rangeSpan = range.endInclusive - range.start
            val fraction = if (rangeSpan > 0f) {
                ((value - range.start) / rangeSpan).coerceIn(0f, 1f)
            } else {
                0f
            }
            val thumbCenter = horizontalInset + usableSpan * fraction
            val thumbWidth = 34.dp + 18.dp * stretch
            val thumbHeight = 31.dp + 6.dp * stretch
            // The slight trailing offset gives the pill a soft, elastic follow-through.
            val trailingOffset = if (isDragging) 3.dp * stretch * -dragDirection.toFloat() else 0.dp
            val thumbOffset = thumbCenter - thumbWidth / 2 + trailingOffset
            val glowStrength = if (isDragging) 0.34f else if (isPressed) 0.25f else 0.15f
            val activeTrack = palette.accent
            val inactiveTrack = palette.stroke.copy(alpha = if (palette.isDark) 0.45f else 0.36f)

            // Track and diffused accent glow are separate from the interaction layer.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val left = horizontalInset.toPx()
                        val right = (size.width - horizontalInset.toPx()).coerceAtLeast(left)
                        val span = right - left
                        val centerY = size.height / 2f
                        val thumbX = left + span * fraction
                        val thinTrack = 5.dp.toPx()
                        val trackTop = centerY - thinTrack / 2f
                        val radius = (if (isDragging) 58.dp else 44.dp).toPx()

                        // Soft radial bloom behind the glass capsule.
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeTrack.copy(alpha = glowStrength),
                                    activeTrack.copy(alpha = glowStrength * 0.36f),
                                    Color.Transparent,
                                ),
                                center = Offset(thumbX, centerY),
                                radius = radius,
                            ),
                            radius = radius,
                            center = Offset(thumbX, centerY),
                        )

                        // A low, diffuse cyan line sits beneath the sharp active track.
                        if (thumbX > left) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        activeTrack.copy(alpha = 0.05f),
                                        activeTrack.copy(alpha = if (isDragging) 0.30f else 0.17f),
                                    ),
                                    startX = left,
                                    endX = thumbX,
                                ),
                                topLeft = Offset(left, centerY - 7.dp.toPx()),
                                size = Size(thumbX - left, 14.dp.toPx()),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                            )
                        }

                        drawRoundRect(
                            color = inactiveTrack,
                            topLeft = Offset(left, trackTop),
                            size = Size(span, thinTrack),
                            cornerRadius = CornerRadius(thinTrack / 2f, thinTrack / 2f),
                        )
                        if (thumbX > left) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        activeTrack.copy(alpha = 0.84f),
                                        activeTrack,
                                    ),
                                    startX = left,
                                    endX = thumbX,
                                ),
                                topLeft = Offset(left, trackTop),
                                size = Size(thumbX - left, thinTrack),
                                cornerRadius = CornerRadius(thinTrack / 2f, thinTrack / 2f),
                            )
                        }
                    },
            )

            // Native Slider supplies touch input and accessibility; its own visuals are hidden.
            Slider(
                value = value,
                onValueChange = { newValue ->
                    isDragging = true
                    val delta = newValue - lastReportedValue
                    if (abs(delta) > 0.0001f) dragDirection = if (delta > 0f) 1 else -1
                    lastReportedValue = newValue
                    onValueChange(newValue)
                },
                onValueChangeFinished = {
                    isDragging = false
                    haptic.performIfEnabled(hapticEnabled, HapticFeedbackType.LongPress)
                    onValueChangeFinished?.invoke()
                },
                valueRange = range,
                steps = steps,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxSize(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )

            // Real backdrop blur (Haze) + a soft frosted tint, with a cyan liquid rim.
            val backdropBlur = if (haze != null && style.blur > 0.01f) {
                Modifier.hazeEffect(state = haze) {
                    blurRadius = (22f + 20f * style.blur).dp
                    noiseFactor = 0.035f
                    backgroundColor = palette.surface.copy(alpha = 0.12f)
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(glassShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (palette.isDark) 0.48f else 0.82f),
                                palette.surface.copy(alpha = if (palette.isDark) 0.34f else 0.54f),
                                palette.surface.copy(alpha = if (palette.isDark) 0.22f else 0.36f),
                            ),
                        ),
                        glassShape,
                    )
                    .then(backdropBlur)
                    .border(
                        width = 1.15.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                activeTrack.copy(alpha = 0.86f),
                                Color.White.copy(alpha = 0.78f),
                            ),
                        ),
                        shape = glassShape,
                    )
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        val h = size.height
                        val cyan = activeTrack.copy(alpha = if (isDragging) 0.96f else 0.78f)
                        val rimWidth = (1.15f + 0.25f * stretch) * density

                        // Signature cyan liquid contour around the left side of the glass.
                        val leftContour = Path().apply {
                            moveTo(w * 0.29f, h * 0.10f)
                            cubicTo(w * 0.12f, h * 0.10f, w * 0.09f, h * 0.28f, w * 0.09f, h * 0.50f)
                            cubicTo(w * 0.09f, h * 0.72f, w * 0.12f, h * 0.90f, w * 0.29f, h * 0.90f)
                        }
                        drawPath(
                            path = leftContour,
                            color = cyan,
                            style = Stroke(width = rimWidth, cap = StrokeCap.Round),
                        )

                        val topLiquidLine = Path().apply {
                            moveTo(w * 0.25f, h * 0.20f)
                            cubicTo(w * 0.31f, h * 0.12f, w * 0.40f, h * 0.12f, w * 0.54f, h * 0.12f)
                            lineTo(w * 0.69f, h * 0.12f)
                        }
                        val bottomLiquidLine = Path().apply {
                            moveTo(w * 0.25f, h * 0.80f)
                            cubicTo(w * 0.31f, h * 0.88f, w * 0.40f, h * 0.88f, w * 0.54f, h * 0.88f)
                            lineTo(w * 0.69f, h * 0.88f)
                        }
                        drawPath(topLiquidLine, cyan.copy(alpha = 0.85f), style = Stroke(width = rimWidth, cap = StrokeCap.Round))
                        drawPath(bottomLiquidLine, cyan.copy(alpha = 0.85f), style = Stroke(width = rimWidth, cap = StrokeCap.Round))

                        // A clean specular reflection makes the capsule read as glass, not a flat pill.
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.04f),
                                    Color.White.copy(alpha = if (isDragging) 0.54f else 0.40f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                                startX = w * 0.18f,
                                endX = w * 0.78f,
                            ),
                            topLeft = Offset(w * 0.19f, h * 0.07f),
                            size = Size(w * 0.61f, h * 0.22f),
                            cornerRadius = CornerRadius(h * 0.11f, h * 0.11f),
                        )
                    },
            )
        }
    }
}
