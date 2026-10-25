package com.aether.x.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Existing AetherX slider API, now rendered with the Kyant0 LiquidSlider capsule thumb and spring
 * motion. Value snapping, semantics, and the finish callback remain available to current screens.
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
    val latestValueState = rememberUpdatedState(value)
    val latestRangeState = rememberUpdatedState(valueRange)
    val latestStepsState = rememberUpdatedState(steps)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)

    val valueReader = remember { { latestValueState.value } }
    val quantize = remember {
        { raw: Float ->
            val range = latestRangeState.value
            val countSteps = latestStepsState.value
            val min = range.start
            val max = range.endInclusive
            val span = (max - min).coerceAtLeast(0.0001f)
            val clamped = raw.coerceIn(min, max)
            if (countSteps <= 0) clamped else {
                val intervals = (countSteps + 1).coerceAtLeast(1)
                val fraction = ((clamped - min) / span).coerceIn(0f, 1f)
                val snapped = (fraction * intervals + 0.5f).toInt().coerceIn(0, intervals)
                (min + snapped / intervals.toFloat() * span).coerceIn(min, max)
            }
        }
    }
    val changeProxy = remember {
        { next: Float -> latestOnValueChange.value(quantize(next)) }
    }
    val finishProxy = remember {
        { latestOnValueChangeFinished.value?.invoke() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                    steps = steps,
                )
                setProgress { requested ->
                    latestOnValueChange.value(quantize(requested))
                    latestOnValueChangeFinished.value?.invoke()
                    true
                }
            },
    ) {
        LiquidSlider(
            value = valueReader,
            onValueChange = changeProxy,
            valueRange = valueRange,
            visibilityThreshold = ((valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f) * 0.001f),
            modifier = Modifier.fillMaxSize(),
            steps = steps,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            onValueChangeFinished = finishProxy,
        )
    }
}
