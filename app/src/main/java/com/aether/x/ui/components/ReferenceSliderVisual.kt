package com.aether.x.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    thumbScale: Float = 1f,
    thumbLift: Dp = 0.dp,
    thumbPopColor: Color = Color.Transparent,
    trackHeight: Dp = 14.dp,
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
    val trackHeightPx = trackHeight.toPx()
    val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

    // Full track: subtle accent tint, not glass or glow.
    drawRoundRect(
        color = inactiveColor,
        topLeft = Offset(left, centerY - trackHeightPx / 2f),
        size = Size(span, trackHeightPx),
        cornerRadius = trackCorner,
    )

    // Filled portion follows the value and stays flush with the thumb.
    if (centerX > left) {
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(left, centerY - trackHeightPx / 2f),
            size = Size((centerX - left).coerceAtLeast(0f), trackHeightPx),
            cornerRadius = trackCorner,
        )
    }

    // End marker shown in the reference image.
    drawCircle(
        color = activeColor,
        radius = 3.5.dp.toPx(),
        center = Offset(right, centerY),
    )

    // Thumb expands and lifts with the spring animation while the user holds it.
    val thumbWidth = 8.dp.toPx() * thumbScale.coerceIn(1f, 1.35f)
    val thumbHeight = (42.dp.toPx() * thumbScale.coerceIn(1f, 1.35f)).coerceAtMost(size.height + 10.dp.toPx())
    val thumbCenterY = centerY - thumbLift.toPx()
    if (thumbPopColor.alpha > 0f) {
        drawRoundRect(
            color = thumbPopColor,
            topLeft = Offset(centerX - thumbWidth * 1.55f / 2f, thumbCenterY - thumbHeight * 0.62f),
            size = Size(thumbWidth * 1.55f, thumbHeight * 1.24f),
            cornerRadius = CornerRadius(thumbWidth, thumbWidth),
        )
    }
    drawRoundRect(
        color = activeColor,
        topLeft = Offset(centerX - thumbWidth / 2f, thumbCenterY - thumbHeight / 2f),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
    )
}
