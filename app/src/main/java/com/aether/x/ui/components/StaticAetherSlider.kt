package com.aether.x.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slider statis AetherX. Thumb dan track digambar langsung tanpa animate*AsState,
 * spring, efek scale, atau animasi tekan/lepas. Gesture tetap mendukung drag,
 * tap, touch target yang besar, dan aksesibilitas progress slider.
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                role = Role.Slider
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
                                updateFromX(change.position.x, size.width.toFloat(), inset)
                                change.consume()
                            }
                        } else {
                            if (!cancelledByVerticalScroll) {
                                // A simple tap selects immediately; a drag uses its final position.
                                updateFromX(change.position.x, size.width.toFloat(), inset)
                                latestOnValueChangeFinished?.invoke()
                            }
                            ended = true
                        }
                    }
                }
            },
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
            val thumbWidth = 7.dp.toPx()
            val thumbHeight = 34.dp.toPx().coerceAtMost(size.height - 2.dp.toPx())
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
            )
        }
    }
}
