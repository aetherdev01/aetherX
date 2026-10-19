package com.aether.x.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact slider styling matching the original AetherX reference:
 * rounded filled track, pale inactive track, vertical accent thumb and end dot.
 * Keep a real Material Slider above this drawing for touch handling/accessibility.
 */
fun Modifier.referenceSliderVisual(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
): Modifier = drawBehind {
    val inset = 12.dp.toPx()
    val left = inset
    val right = (size.width - inset).coerceAtLeast(left)
    val span = right - left
    val rangeSpan = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSpan > 0f) {
        ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    } else {
        0f
    }
    val centerX = left + span * fraction
    val centerY = size.height / 2f
    val trackHeight = 14.dp.toPx()
    val trackCorner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

    // Full track: subtle accent tint, not glass or glow.
    drawRoundRect(
        color = inactiveColor,
        topLeft = Offset(left, centerY - trackHeight / 2f),
        size = Size(span, trackHeight),
        cornerRadius = trackCorner,
    )

    // Filled portion follows the value and stays flush with the thumb.
    if (centerX > left) {
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(left, centerY - trackHeight / 2f),
            size = Size((centerX - left).coerceAtLeast(0f), trackHeight),
            cornerRadius = trackCorner,
        )
    }

    // End marker shown in the reference image.
    drawCircle(
        color = activeColor,
        radius = 3.5.dp.toPx(),
        center = Offset(right, centerY),
    )

    // Tall, narrow thumb matching the earlier AetherX slider.
    val thumbWidth = 8.dp.toPx()
    val thumbHeight = 42.dp.toPx().coerceAtMost(size.height - 2.dp.toPx())
    drawRoundRect(
        color = activeColor,
        topLeft = Offset(centerX - thumbWidth / 2f, centerY - thumbHeight / 2f),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
    )
}
