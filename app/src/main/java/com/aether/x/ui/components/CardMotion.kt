package com.aether.x.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.cardEnterAnimation(
    index: Int,
    baseDelayMillis: Int = 42,
    durationMillis: Int = 460,
): Modifier {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = index * baseDelayMillis,
            easing = EaseOutCubic,
        ),
        label = "cardEnterAnimation",
    )
    return this.alpha(progress).scale(0.985f + progress * 0.015f)
        .offset(y = ((1f - progress) * 14).dp)
}

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 150, easing = EaseOutCubic),
        label = "pressScale",
    )
    return this.scale(scale)
}

@Composable
fun rememberPressScaleInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }
