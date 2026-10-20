package com.aether.x.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActive = isPressed || isDragged
    // A deliberately visible spring: the track and thumb pop upward on touch,
    // then rebound when released instead of using a nearly imperceptible scale.
    val sliderScale by animateFloatAsState(
        targetValue = if (isActive) 1.09f else 1f,
        animationSpec = spring(
            dampingRatio = if (isActive) Spring.DampingRatioMediumBouncy else Spring.DampingRatioLowBouncy,
            stiffness = if (isActive) Spring.StiffnessMediumLow else Spring.StiffnessLow,
        ),
        label = "sliderSpringPop",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isActive) 1.30f else 1f,
        animationSpec = spring(
            dampingRatio = if (isActive) Spring.DampingRatioMediumBouncy else Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderThumbSpring",
    )
    val liftDp by animateFloatAsState(
        targetValue = if (isActive) 4.5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderLiftSpring",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (isActive) 20.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderTrackSpring",
    )

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
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(sliderScale)
                .shadow(
                    elevation = if (isActive) 9.dp else 0.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                )
                .referenceSliderVisual(
                    value = value,
                    valueRange = range,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    thumbScale = thumbScale,
                    thumbLift = liftDp.dp,
                    thumbPopColor = activeColor.copy(alpha = if (isActive) 0.18f else 0f),
                    trackHeight = trackHeight,
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
