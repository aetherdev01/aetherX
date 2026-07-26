package com.aether.x.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.aether.x.data.CrosshairStyle

private const val PREVIEW_THICKNESS_DP = 3f

@Composable
fun CrosshairPreview(
    style: CrosshairStyle,
    color: Color,
    sizeDp: Float,
    rotationDegrees: Int = 0,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = sizeDp.coerceAtMost(size.minDimension / 2.4f)
        val thickness = PREVIEW_THICKNESS_DP
        val drawColor = color
        val stroke = Stroke(width = thickness, cap = StrokeCap.Round)

        rotate(degrees = rotationDegrees.toFloat(), pivot = Offset(cx, cy)) {
        when (style) {
            CrosshairStyle.CROSS -> {
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.PLUS_GAP -> {
                val gap = r * 0.35f
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.X_SHAPE -> {
                val d = r * 0.7071f
                drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
            }
            CrosshairStyle.DOT -> {
                drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
            }
            CrosshairStyle.CIRCLE -> {
                drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = stroke)
            }
            CrosshairStyle.CIRCLE_DOT -> {
                drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = stroke)
                drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
            }
            CrosshairStyle.CROSS_DOT -> {

                val gap = r * 0.35f
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                drawCircle(drawColor, radius = thickness * 1.4f, center = Offset(cx, cy))
            }
            CrosshairStyle.T_SHAPE -> {

                drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }

            CrosshairStyle.DIAMOND -> {
                val gap = r * 0.3f
                val d = r * 0.7071f
                val gapD = gap * 0.7071f
                drawLine(drawColor, Offset(cx - gapD, cy - gapD), Offset(cx - d, cy - d), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx, cy - r), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + d, cy - d), Offset(cx + gapD, cy - gapD), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + gapD, cy + gapD), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + d, cy + d), Offset(cx, cy + r), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + r), Offset(cx - d, cy + d), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx - gapD, cy + gapD), thickness, StrokeCap.Round)
            }

            CrosshairStyle.SQUARE -> {
                val s = r * 0.85f
                val corner = s * 0.5f
                drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s + corner, cy - s), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s, cy - s + corner), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s - corner, cy - s), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s, cy - s + corner), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s + corner, cy + s), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s, cy + s - corner), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s - corner, cy + s), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s, cy + s - corner), thickness, StrokeCap.Round)
            }

            CrosshairStyle.CHEVRON -> {
                val gap = r * 0.35f
                val arm = r * 0.45f
                val tip = r * 0.9f
                drawLine(drawColor, Offset(cx - arm, cy - tip), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - gap), Offset(cx + arm, cy - tip), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - arm, cy + tip), Offset(cx, cy + gap), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + gap), Offset(cx + arm, cy + tip), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - tip, cy - arm), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx - gap, cy), Offset(cx - tip, cy + arm), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + tip, cy - arm), Offset(cx + gap, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + tip, cy + arm), thickness, StrokeCap.Round)
            }

            CrosshairStyle.DOUBLE_RING -> {
                drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = stroke)
                drawCircle(drawColor, radius = r * 0.55f, center = Offset(cx, cy), style = stroke)
            }

            CrosshairStyle.PLUS -> {
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }

            CrosshairStyle.BULLET -> {
                drawCircle(drawColor, radius = r * 0.5f, center = Offset(cx, cy))
            }

            CrosshairStyle.CIRCLE_PLUS -> {
                drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = stroke)
                val armInner = r * 0.15f
                drawLine(drawColor, Offset(cx - r * 0.75f, cy), Offset(cx - armInner, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + armInner, cy), Offset(cx + r * 0.75f, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r * 0.75f), Offset(cx, cy - armInner), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + armInner), Offset(cx, cy + r * 0.75f), thickness, StrokeCap.Round)
            }

            CrosshairStyle.TICK_CROSS -> {
                val tickOuter = r
                val tickInner = r * 0.45f
                drawLine(drawColor, Offset(cx - tickOuter, cy), Offset(cx - tickInner, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + tickInner, cy), Offset(cx + tickOuter, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - tickOuter), Offset(cx, cy - tickInner), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + tickInner), Offset(cx, cy + tickOuter), thickness, StrokeCap.Round)
            }

            CrosshairStyle.CIRCLE_DOT_TICKS -> {
                drawCircle(drawColor, radius = r * 0.7f, center = Offset(cx, cy), style = stroke)
                drawCircle(drawColor, radius = thickness * 1.2f, center = Offset(cx, cy))
                val tickStart = r * 0.7f + thickness * 0.4f
                val tickEnd = r
                drawLine(drawColor, Offset(cx - tickEnd, cy), Offset(cx - tickStart, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + tickStart, cy), Offset(cx + tickEnd, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - tickEnd), Offset(cx, cy - tickStart), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + tickStart), Offset(cx, cy + tickEnd), thickness, StrokeCap.Round)
            }

            CrosshairStyle.CIRCLE_CROSS_TICKS -> {
                drawCircle(drawColor, radius = r * 0.7f, center = Offset(cx, cy), style = stroke)
                drawLine(drawColor, Offset(cx - r * 0.7f, cy), Offset(cx + r * 0.7f, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f), thickness, StrokeCap.Round)
                val tickStart = r * 0.7f + thickness * 0.4f
                val tickEnd = r
                drawLine(drawColor, Offset(cx - tickEnd, cy), Offset(cx - tickStart, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + tickStart, cy), Offset(cx + tickEnd, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - tickEnd), Offset(cx, cy - tickStart), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + tickStart), Offset(cx, cy + tickEnd), thickness, StrokeCap.Round)
            }
        }
        }
    }
}
