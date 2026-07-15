package com.aether.x.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.aether.x.data.CrosshairStyle
import kotlin.math.roundToInt

/**
 * View kustom yang menggambar crosshair (garis silang/titik/lingkaran) sesuai
 * gaya, warna, ukuran, ketebalan, dan opasitas yang dipilih pengguna.
 * Dipakai oleh [CrosshairOverlayService] sebagai konten window overlay.
 */
class CrosshairView(context: Context) : View(context) {

    var style: CrosshairStyle = CrosshairStyle.CROSS
        set(value) { field = value; invalidate() }

    /** Ukuran "jangkauan" crosshair dalam px, dari pusat ke ujung garis/lingkaran. */
    var crosshairSizePx: Float = 48f
        set(value) { field = value; requestLayout(); invalidate() }

    var thicknessPx: Float = 6f
        set(value) { field = value; invalidate() }

    /** Warna dasar ARGB (opacity 0-100 diaplikasikan terpisah lewat [opacityPercent]). */
    var colorArgb: Long = 0xFF00FF66
        set(value) { field = value; updatePaintColor() }

    var opacityPercent: Int = 100
        set(value) { field = value.coerceIn(0, 100); updatePaintColor() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        updatePaintColor()
    }

    private fun updatePaintColor() {
        val baseColor = colorArgb.toInt()
        val alpha = ((opacityPercent / 100f) * 255f).roundToInt().coerceIn(0, 255)
        val withAlpha = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        paint.color = withAlpha
        fillPaint.color = withAlpha
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // View selalu persegi, cukup besar untuk memuat crosshair + sedikit padding.
        val dimension = ((crosshairSizePx + thicknessPx) * 2.4f).toInt().coerceAtLeast(1)
        setMeasuredDimension(dimension, dimension)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.strokeWidth = thicknessPx
        val cx = width / 2f
        val cy = height / 2f
        val r = crosshairSizePx

        when (style) {
            CrosshairStyle.CROSS -> {
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            }
            CrosshairStyle.PLUS_GAP -> {
                val gap = r * 0.35f
                canvas.drawLine(cx - r, cy, cx - gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy - gap, paint)
                canvas.drawLine(cx, cy + gap, cx, cy + r, paint)
            }
            CrosshairStyle.X_SHAPE -> {
                val d = r * 0.7071f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
            }
            CrosshairStyle.DOT -> {
                canvas.drawCircle(cx, cy, thicknessPx * 1.6f, fillPaint)
            }
            CrosshairStyle.CIRCLE -> {
                canvas.drawCircle(cx, cy, r, paint)
            }
            CrosshairStyle.CIRCLE_DOT -> {
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawCircle(cx, cy, thicknessPx * 1.6f, fillPaint)
            }
            CrosshairStyle.CROSS_DOT -> {
                // Model baru: silang dengan gap (mirip PLUS_GAP) + titik solid
                // di tengah — populer di game FPS mobile karena titik tengah
                // membantu akurasi bidik tanpa menutupi target kecil.
                val gap = r * 0.35f
                canvas.drawLine(cx - r, cy, cx - gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy - gap, paint)
                canvas.drawLine(cx, cy + gap, cx, cy + r, paint)
                canvas.drawCircle(cx, cy, thicknessPx * 1.4f, fillPaint)
            }
            CrosshairStyle.T_SHAPE -> {
                // Model baru: bentuk "T" terbalik (garis horizontal penuh +
                // satu garis vertikal ke bawah saja, TANPA garis ke atas) —
                // umum dipakai di game battle royale supaya area di atas
                // titik bidik tidak tertutup crosshair.
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy, cx, cy + r, paint)
            }
            CrosshairStyle.DIAMOND -> {
                // FITUR BARU: belah ketupat terbuka — implementasi HARUS
                // identik dengan StyleIconButton (CrosshairSettingsSection.kt)
                // dan CrosshairPreview.kt supaya WYSIWYG.
                val gap = r * 0.3f
                val d = r * 0.7071f
                val gapD = gap * 0.7071f
                canvas.drawLine(cx - gapD, cy - gapD, cx - d, cy - d, paint)
                canvas.drawLine(cx - d, cy - d, cx, cy - r, paint)
                canvas.drawLine(cx, cy - r, cx + d, cy - d, paint)
                canvas.drawLine(cx + d, cy - d, cx + gapD, cy - gapD, paint)
                canvas.drawLine(cx + gapD, cy + gapD, cx + d, cy + d, paint)
                canvas.drawLine(cx + d, cy + d, cx, cy + r, paint)
                canvas.drawLine(cx, cy + r, cx - d, cy + d, paint)
                canvas.drawLine(cx - d, cy + d, cx - gapD, cy + gapD, paint)
            }
            CrosshairStyle.SQUARE -> {
                // FITUR BARU: kotak bracket 4 sudut — implementasi HARUS
                // identik dengan StyleIconButton & CrosshairPreview.
                val s = r * 0.85f
                val corner = s * 0.5f
                canvas.drawLine(cx - s, cy - s, cx - s + corner, cy - s, paint)
                canvas.drawLine(cx - s, cy - s, cx - s, cy - s + corner, paint)
                canvas.drawLine(cx + s, cy - s, cx + s - corner, cy - s, paint)
                canvas.drawLine(cx + s, cy - s, cx + s, cy - s + corner, paint)
                canvas.drawLine(cx - s, cy + s, cx - s + corner, cy + s, paint)
                canvas.drawLine(cx - s, cy + s, cx - s, cy + s - corner, paint)
                canvas.drawLine(cx + s, cy + s, cx + s - corner, cy + s, paint)
                canvas.drawLine(cx + s, cy + s, cx + s, cy + s - corner, paint)
            }
        }
    }
}
