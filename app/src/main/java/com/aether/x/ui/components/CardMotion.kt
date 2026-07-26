package com.aether.x.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Efek masuk untuk kartu: fade-in + slide naik dari bawah, muncul berurutan
 * (staggered) berdasarkan [index]. Dipakai di root modifier tiap Card/Row/Column
 * kartu pada satu layar.
 *
 * Contoh: Modifier.cardEnterAnimation(index = 0)
 */
@Composable
fun Modifier.cardEnterAnimation(
    index: Int,
    baseDelayMillis: Int = 60,
    durationMillis: Int = 420,
): Modifier {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = index * baseDelayMillis,
            easing = EaseOutCubic,
        ),
        label = "cardEnterAnimation",
    )
    return this
        .alpha(progress)
        .offset(y = ((1f - progress) * 24).dp)
}

/**
 * Efek "mengecil" halus saat kartu ditekan (press feedback). Dipakai bersama
 * `.clickable(interactionSource = interactionSource, indication = null) { ... }`
 * pada elemen yang sama, dengan interactionSource yang dibagikan lewat
 * [rememberPressScaleInteractionSource].
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120, easing = EaseOutCubic),
        label = "pressScale",
    )
    return this.scale(scale)
}

@Composable
fun rememberPressScaleInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }
