package com.aether.x.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Lightweight FPS history chart with a labeled Y axis and dashed guide lines. */
@Composable
fun RealtimeLineChart(
    values: List<Float>,
    maxValue: Float,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val safeMax = maxValue.coerceAtLeast(1f)
    val fractions = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.height(140.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                fractions.forEach { fraction ->
                    Text(
                        text = (safeMax * fraction).roundToInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        fontWeight = if (fraction == 1f || fraction == 0f) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp),
            ) {
                val width = size.width
                val height = size.height
                val dashed = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                fractions.forEach { fraction ->
                    val y = height * (1f - fraction)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashed,
                    )
                }
                // Subtle vertical guides make elapsed sample spacing easier to compare.
                for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                    val x = width * fraction
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = dashed,
                    )
                }
                if (values.isEmpty()) return@Canvas

                fun yFor(value: Float): Float {
                    val fraction = (value.coerceIn(0f, safeMax) / safeMax).coerceIn(0f, 1f)
                    return height * (1f - fraction)
                }
                if (values.size == 1) {
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = Offset(width / 2f, yFor(values.first())),
                    )
                    return@Canvas
                }

                val linePath = Path()
                val fillPath = Path()
                val stepX = width / (values.size - 1).coerceAtLeast(1)
                values.forEachIndexed { index, value ->
                    val x = stepX * index
                    val y = yFor(value)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(stepX * (values.size - 1), height)
                fillPath.close()
                drawPath(fillPath, color = lineColor.copy(alpha = 0.12f), style = Fill)
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                values.forEachIndexed { index, value ->
                    drawCircle(
                        color = lineColor,
                        radius = if (index == values.lastIndex) 3.dp.toPx() else 1.6.dp.toPx(),
                        center = Offset(stepX * index, yFor(value)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Awal", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("Terbaru", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}
