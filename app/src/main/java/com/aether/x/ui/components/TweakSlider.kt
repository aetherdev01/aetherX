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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Original AetherX slider style, restored from the glass-pill redesign. */
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
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = activeColor.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.14f else 0.28f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = activeColor,
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, bottom = 7.dp, end = 8.dp),
        )
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
                .referenceSliderVisual(
                    value = value,
                    valueRange = range,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                ),
            colors = SliderDefaults.colors(
                // Use the real Slider for interaction and accessibility, while the
                // custom drawing above supplies the requested visual appearance.
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
