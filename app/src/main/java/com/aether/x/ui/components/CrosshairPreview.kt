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
import androidx.compose.ui.unit.dp
import com.aether.x.data.CrosshairStyle

/**
 * Preview mini crosshair yang dipakai di layar Settings, menggambar bentuk
 * yang sama persis dengan yang akan tampil di overlay sungguhan
 * ([com.aether.x.core.overlay.CrosshairView]), supaya WYSIWYG.
 */
@Composable
fun CrosshairPreview(
    style: CrosshairStyle,
    color: Color,
    sizeDp: Float,
    thicknessDp: Float,
    opacityPercent: Int,
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
        val thickness = thicknessDp
        val drawColor = color.copy(alpha = opacityPercent / 100f)
        val stroke = Stroke(width = thickness, cap = StrokeCap.Round)

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
                // Model baru: silang dengan gap + titik solid di tengah —
                // lihat KDoc implementasi identik di CrosshairView.onDraw
                // (WYSIWYG, gambar preview ini HARUS sama persis dengan
                // overlay sungguhan).
                val gap = r * 0.35f
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                drawCircle(drawColor, radius = thickness * 1.4f, center = Offset(cx, cy))
            }
            CrosshairStyle.T_SHAPE -> {
                // Model baru: bentuk "T" terbalik — lihat KDoc implementasi
                // identik di CrosshairView.onDraw.
                drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(drawColor, Offset(cx, cy), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            // BARU: belah ketupat terbuka — implementasi HARUS identik
            // dengan StyleIconButton di CrosshairSettingsSection.kt dan
            // dengan case yang sama di CrosshairView.onDraw (di luar zip
            // ini) supaya WYSIWYG antara icon pilihan, preview, dan overlay
            // sungguhan di layar game.
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
            // BARU: kotak terbuka (bracket 4 sudut) — sama seperti di atas,
            // implementasi harus identik dengan StyleIconButton & CrosshairView.
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
            // FITUR BARU: 4 chevron "V" dari tiap sisi mengarah ke pusat —
            // implementasi identik dengan StyleIconButton
            // (CrosshairSettingsSection.kt) dan CrosshairView.onDraw
            // (core/overlay) supaya WYSIWYG.
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
            // FITUR BARU: dua lingkaran konsentris, gaya sniper-scope —
            // implementasi identik dengan StyleIconButton & CrosshairView.
            CrosshairStyle.DOUBLE_RING -> {
                drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = stroke)
                drawCircle(drawColor, radius = r * 0.55f, center = Offset(cx, cy), style = stroke)
            }
        }
    }
}
