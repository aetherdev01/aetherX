package com.aether.x.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.aether.x.data.CrosshairStyle

class CrosshairView(context: Context) : View(context) {

    var style: CrosshairStyle = CrosshairStyle.CROSS
        set(value) { field = value; invalidate() }

    var crosshairSizePx: Float = 48f
        set(value) { field = value; requestLayout(); invalidate() }

    var rotationDegrees: Int = 0
        set(value) { field = value; invalidate() }

    var colorArgb: Long = 0xFF00FF66
        set(value) { field = value; updatePaintColor() }

    /** Ketebalan garis tetap (fitur pengaturan ketebalan dihapus dari UI —
     *  lihat rework Crosshair — cukup satu nilai wajar untuk semua style). */
    private val thicknessPx: Float = 6f

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
        val withAlpha = Color.argb(255, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        paint.color = withAlpha
        fillPaint.color = withAlpha
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val dimension = ((crosshairSizePx + thicknessPx) * 2.4f).toInt().coerceAtLeast(1)
        setMeasuredDimension(dimension, dimension)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.strokeWidth = thicknessPx
        val cx = width / 2f
        val cy = height / 2f
        val r = crosshairSizePx

        val saveCount = canvas.save()
        if (rotationDegrees != 0) {
            canvas.rotate(rotationDegrees.toFloat(), cx, cy)
        }

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

                val gap = r * 0.35f
                canvas.drawLine(cx - r, cy, cx - gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy - gap, paint)
                canvas.drawLine(cx, cy + gap, cx, cy + r, paint)
                canvas.drawCircle(cx, cy, thicknessPx * 1.4f, fillPaint)
            }
            CrosshairStyle.T_SHAPE -> {

                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy, cx, cy + r, paint)
            }
            CrosshairStyle.DIAMOND -> {

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
            CrosshairStyle.CHEVRON -> {

                val gap = r * 0.35f
                val arm = r * 0.45f
                val tip = r * 0.9f

                canvas.drawLine(cx - arm, cy - tip, cx, cy - gap, paint)
                canvas.drawLine(cx, cy - gap, cx + arm, cy - tip, paint)

                canvas.drawLine(cx - arm, cy + tip, cx, cy + gap, paint)
                canvas.drawLine(cx, cy + gap, cx + arm, cy + tip, paint)

                canvas.drawLine(cx - tip, cy - arm, cx - gap, cy, paint)
                canvas.drawLine(cx - gap, cy, cx - tip, cy + arm, paint)

                canvas.drawLine(cx + tip, cy - arm, cx + gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + tip, cy + arm, paint)
            }
            CrosshairStyle.DOUBLE_RING -> {

                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawCircle(cx, cy, r * 0.55f, paint)
            }
            CrosshairStyle.PLUS -> {
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            }
            CrosshairStyle.BULLET -> {
                canvas.drawCircle(cx, cy, r * 0.5f, fillPaint)
            }
            CrosshairStyle.CIRCLE_PLUS -> {

                canvas.drawCircle(cx, cy, r, paint)
                val armInner = r * 0.15f
                canvas.drawLine(cx - r * 0.75f, cy, cx - armInner, cy, paint)
                canvas.drawLine(cx + armInner, cy, cx + r * 0.75f, cy, paint)
                canvas.drawLine(cx, cy - r * 0.75f, cx, cy - armInner, paint)
                canvas.drawLine(cx, cy + armInner, cx, cy + r * 0.75f, paint)
            }
            CrosshairStyle.TICK_CROSS -> {

                val tickOuter = r
                val tickInner = r * 0.45f
                canvas.drawLine(cx - tickOuter, cy, cx - tickInner, cy, paint)
                canvas.drawLine(cx + tickInner, cy, cx + tickOuter, cy, paint)
                canvas.drawLine(cx, cy - tickOuter, cx, cy - tickInner, paint)
                canvas.drawLine(cx, cy + tickInner, cx, cy + tickOuter, paint)
            }
            CrosshairStyle.CIRCLE_DOT_TICKS -> {

                canvas.drawCircle(cx, cy, r * 0.7f, paint)
                canvas.drawCircle(cx, cy, thicknessPx * 1.2f, fillPaint)
                val tickStart = r * 0.7f + thicknessPx * 0.4f
                val tickEnd = r
                canvas.drawLine(cx - tickEnd, cy, cx - tickStart, cy, paint)
                canvas.drawLine(cx + tickStart, cy, cx + tickEnd, cy, paint)
                canvas.drawLine(cx, cy - tickEnd, cx, cy - tickStart, paint)
                canvas.drawLine(cx, cy + tickStart, cx, cy + tickEnd, paint)
            }
            CrosshairStyle.CIRCLE_CROSS_TICKS -> {

                canvas.drawCircle(cx, cy, r * 0.7f, paint)
                canvas.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, paint)
                canvas.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, paint)
                val tickStart = r * 0.7f + thicknessPx * 0.4f
                val tickEnd = r
                canvas.drawLine(cx - tickEnd, cy, cx - tickStart, cy, paint)
                canvas.drawLine(cx + tickStart, cy, cx + tickEnd, cy, paint)
                canvas.drawLine(cx, cy - tickEnd, cx, cy - tickStart, paint)
                canvas.drawLine(cx, cy + tickStart, cx, cy + tickEnd, paint)
            }
        }

        canvas.restoreToCount(saveCount)
    }
}
