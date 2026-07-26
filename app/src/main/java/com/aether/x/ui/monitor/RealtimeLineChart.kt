package com.aether.x.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * RealtimeLineChart — grafik garis ringan berbasis Canvas biasa, dibuat
 * khusus untuk histori CPU/GPU root monitor (lihat RootMonitorViewModel).
 * SENGAJA tidak memakai library chart eksternal (mis. MPAndroidChart) —
 * kebutuhannya cuma satu garis halus + fill gradasi tipis, dan menambah
 * dependency baru untuk itu tidak sepadan dibanding ~60 baris Canvas
 * (project ini juga sudah punya beberapa Canvas custom lain, lihat
 * CrosshairPreview.kt/GameBoosterSidebarContent.kt untuk pola serupa).
 *
 * @param values histori nilai (0f..maxValue), item terlama di index 0,
 *   terbaru di index terakhir — grafik digambar kiri (lama) ke kanan (baru).
 * @param maxValue nilai maksimum sumbu Y (mis. 100f untuk persen).
 * @param lineColor warna garis & fill (fill memakai alpha rendah dari warna ini).
 */
@Composable
fun RealtimeLineChart(
    values: List<Float>,
    maxValue: Float,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val width = size.width
        val height = size.height

        // Grid horizontal tipis di 25/50/75% — referensi visual skala,
        // bukan angka presisi (tidak ada label sumbu, biar tetap ringkas).
        val gridStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = height * (1f - fraction)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = gridStroke.width,
                pathEffect = gridStroke.pathEffect,
            )
        }

        if (values.size < 2) return@Canvas

        val stepX = width / (values.size - 1).coerceAtLeast(1)

        fun yFor(value: Float): Float {
            val clamped = value.coerceIn(0f, maxValue)
            val fraction = if (maxValue > 0f) clamped / maxValue else 0f
            return height * (1f - fraction)
        }

        val linePath = Path()
        val fillPath = Path()
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

        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.16f),
            style = Fill,
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
