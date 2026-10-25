package com.aether.x.ui.components

import android.os.SystemClock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.aether.x.ui.theme.LocalAetherStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slider AetherX mempertahankan gesture, throttling, nilai/steps, dan aksesibilitas
 * semula. Track tetap ringan digambar pada Canvas, sedangkan thumb memakai Backdrop
 * untuk blur/refraction dengan intensitas yang mengikuti pengaturan Tampilan.
 */
@Composable
fun StaticAetherSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val minValue = valueRange.start
    val maxValue = valueRange.endInclusive
    val span = (maxValue - minValue).coerceAtLeast(0.0001f)

    fun quantize(rawValue: Float): Float {
        val clamped = rawValue.coerceIn(minValue, maxValue)
        if (steps <= 0) return clamped
        val intervalCount = (steps + 1).coerceAtLeast(1)
        val fraction = ((clamped - minValue) / span).coerceIn(0f, 1f)
        val quantizedFraction = (fraction * intervalCount).roundToInt() / intervalCount.toFloat()
        return (minValue + quantizedFraction * span).coerceIn(minValue, maxValue)
    }

    fun updateFromX(x: Float, width: Float, inset: Float) {
        val usableWidth = (width - inset * 2f).coerceAtLeast(1f)
        val fraction = ((x - inset) / usableWidth).coerceIn(0f, 1f)
        latestOnValueChange(quantize(minValue + fraction * span))
    }

    val style = LocalAetherStyle.current
    val liquidBackdrop = rememberAetherLiquidBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(
        liquidBackdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop -> drawBackdrop() },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(minValue, maxValue),
                    range = valueRange,
                    steps = steps,
                )
                setProgress { requested ->
                    latestOnValueChange(quantize(requested))
                    latestOnValueChangeFinished?.invoke()
                    true
                }
            }
            .pointerInput(minValue, maxValue, steps, height) {
                val inset = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var horizontalDragStarted = false
                    var ended = false
                    var cancelledByVerticalScroll = false
                    // Limit callback frequency during a drag. Some consumers persist
                    // values or update expensive effects (for example live blur) on
                    // every callback, which can make the slider feel sticky.
                    var lastEmitUptime = 0L
                    val minEmitIntervalMs = 24L
                    while (!ended) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!horizontalDragStarted && abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                                cancelledByVerticalScroll = true
                                break
                            }
                            if (!horizontalDragStarted && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                                horizontalDragStarted = true
                            }
                            if (horizontalDragStarted) {
                                if (SystemClock.uptimeMillis() - lastEmitUptime >= minEmitIntervalMs) {
                                    updateFromX(change.position.x, size.width.toFloat(), inset)
                                    lastEmitUptime = SystemClock.uptimeMillis()
                                }
                                change.consume()
                            }
                        } else {
                            if (!cancelledByVerticalScroll) {
                                // Always apply the final position so throttling never
                                // loses the user's exact release value. A tap also updates.
                                updateFromX(change.position.x, size.width.toFloat(), inset)
                                latestOnValueChangeFinished?.invoke()
                            }
                            ended = true
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val progress = ((value.coerceIn(minValue, maxValue) - minValue) / span).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop),
        ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val left = inset
            val right = (size.width - inset).coerceAtLeast(left)
            val trackWidth = (right - left).coerceAtLeast(1f)
            val fraction = ((value.coerceIn(minValue, maxValue) - minValue) / span).coerceIn(0f, 1f)
            val thumbX = left + trackWidth * fraction
            val centerY = size.height / 2f
            val trackHeight = 12.dp.toPx()
            val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(left, centerY - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = trackRadius,
            )
            if (thumbX > left) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(left, centerY - trackHeight / 2f),
                    size = Size((thumbX - left).coerceAtLeast(0f), trackHeight),
                    cornerRadius = trackRadius,
                )
            }
            drawCircle(
                color = activeColor,
                radius = 3.5.dp.toPx(),
                center = Offset(right, centerY),
            )
        }
        }
        // The moving thumb uses Backdrop's refraction/blur engine; slider range,
        // gesture throttling and accessibility remain controlled by the original slider.
        Box(
            modifier = Modifier
                .offset(x = ((maxWidth - 24.dp).coerceAtLeast(0.dp) * progress))
                .size(width = 24.dp, height = 34.dp)
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        if (style.blur > 0.01f) {
                            blur((blurRadiusFor(style.blur) * 0.30f).dp.toPx())
                        }
                        val level = style.glassLevel.coerceIn(0f, 1f)
                        lens(
                            (5f + 10f * level).dp.toPx(),
                            (8f + 16f * level).dp.toPx(),
                            chromaticAberration = level > 0.6f,
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.18f + 0.30f * style.glassLevel) },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.16f + 0.20f * style.glassLevel))
                    },
                )
                .border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.45f + 0.30f * style.glassLevel),
                    shape = RoundedCornerShape(50),
                ),
        )
    }
}
