package com.aether.x.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, bottom = 7.dp, end = 8.dp),
        )
        val activeTrack = MaterialTheme.colorScheme.primary
        val inactiveTrack = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
        val thumbOutline = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = {
                haptic.performIfEnabled(hapticEnabled, HapticFeedbackType.LongPress)
                onValueChangeFinished?.invoke()
            },
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Custom track + thumb gives the slider a clean iOS-inspired look,
                    // while the native Slider keeps dragging, keyboard and semantics.
                    val horizontalInset = 11.dp.toPx()
                    val trackHeight = 6.dp.toPx()
                    val centerY = size.height / 2f
                    val left = horizontalInset
                    val right = (size.width - horizontalInset).coerceAtLeast(left)
                    val span = right - left
                    val rangeSpan = range.endInclusive - range.start
                    val fraction = if (rangeSpan > 0f) {
                        ((value - range.start) / rangeSpan).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val thumbX = left + span * fraction
                    val trackTop = centerY - trackHeight / 2f
                    val trackSize = Size(span, trackHeight)
                    val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                    drawRoundRect(
                        color = inactiveTrack,
                        topLeft = Offset(left, trackTop),
                        size = trackSize,
                        cornerRadius = corner,
                    )
                    if (thumbX > left) {
                        drawRoundRect(
                            color = activeTrack,
                            topLeft = Offset(left, trackTop),
                            size = Size(thumbX - left, trackHeight),
                            cornerRadius = corner,
                        )
                    }

                    val thumbRadius = 9.dp.toPx()
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.13f),
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY + 1.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY),
                    )
                    drawCircle(
                        color = thumbOutline,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
            colors = SliderDefaults.colors(
                // The real Material track/thumb are transparent; the custom drawing
                // above controls their appearance without replacing native input.
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
