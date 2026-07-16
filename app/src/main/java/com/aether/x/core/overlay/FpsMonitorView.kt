package com.aether.x.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.aether.x.data.FpsMonitorStyle
import com.aether.x.data.TemperatureUnit

/**
 * View kustom yang menggambar panel Monitor FPS, dengan dua gaya visual:
 *
 * - [FpsMonitorStyle.ROG]: kartu gelap translusen ala ROG Phone Game Genie —
 *   menampilkan FPS + baris kecil CPU/GPU/Suhu, dan BISA digeser bebas
 *   oleh pengguna (posisi disimpan).
 * - [FpsMonitorStyle.CLASSIC]: pil kompak satu baris "FPS · CPU · Suhu" ala
 *   game booster jadul, SELALU terkunci di pojok kiri bawah layar (tidak bisa
 *   digeser, tidak ada logika drag untuk gaya ini).
 *
 * Kedua gaya sengaja dibuat dalam skala ukuran yang mirip (selisih kecil,
 * bukan beberapa kali lipat) supaya keduanya terasa satu keluarga desain,
 * hanya beda tata letak & aksen warna.
 */
class FpsMonitorView(context: Context) : View(context) {

    var style: FpsMonitorStyle = FpsMonitorStyle.CLASSIC
        set(value) { field = value; requestLayout(); invalidate() }

    var fps: Int = 0
        set(value) { field = value; invalidate() }

    var cpuPercent: Int? = null
        set(value) { field = value; invalidate() }

    var gpuPercent: Int? = null
        set(value) { field = value; invalidate() }

    var temperatureCelsius: Float? = null
        set(value) { field = value; invalidate() }

    /** Satuan tampilan suhu. Nilai sumber selalu disimpan dalam Celsius
     *  ([temperatureCelsius]); konversi hanya terjadi saat digambar. */
    var temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS
        set(value) { field = value; invalidate() }

    private fun formattedTemperature(): String {
        val celsius = temperatureCelsius ?: return when (temperatureUnit) {
            TemperatureUnit.CELSIUS -> "-°C"
            TemperatureUnit.FAHRENHEIT -> "-°F"
        }
        return when (temperatureUnit) {
            TemperatureUnit.CELSIUS -> "${celsius.toInt()}°C"
            TemperatureUnit.FAHRENHEIT -> "${(celsius * 9f / 5f + 32f).toInt()}°F"
        }
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.LEFT
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textAlign = Paint.Align.LEFT
    }

    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Ukuran ROG & Classic sengaja dibuat berdekatan (selisih kecil), tidak
        // lagi jauh berbeda seperti sebelumnya, dan keduanya jauh lebih ringkas
        // supaya tidak menutupi layar game.
        val (w, h) = when (style) {
            FpsMonitorStyle.ROG -> dp(116f) to dp(46f)
            FpsMonitorStyle.CLASSIC -> dp(128f) to dp(28f)
        }
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (style) {
            FpsMonitorStyle.ROG -> drawRogStyle(canvas)
            FpsMonitorStyle.CLASSIC -> drawClassicStyle(canvas)
        }
    }

    private fun drawRogStyle(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = dp(10f)

        // Kartu gelap semi-transparan dengan aksen garis di kiri, mirip Game Genie,
        // tapi jauh lebih ringkas dari versi sebelumnya.
        bgPaint.color = Color.argb(220, 18, 18, 22)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        accentPaint.color = Color.parseColor("#FF2C4BFF") // aksen biru khas ROG
        val accentWidth = dp(3f)
        val accentRect = RectF(0f, dp(6f), accentWidth, h - dp(6f))
        canvas.drawRoundRect(accentRect, accentWidth / 2, accentWidth / 2, accentPaint)

        val leftPad = accentWidth + dp(8f)

        // Baris besar: FPS
        bigTextPaint.textSize = dp(15f)
        val fpsLabel = "$fps"
        canvas.drawText(fpsLabel, leftPad, dp(19f), bigTextPaint)

        labelTextPaint.textSize = dp(9f)
        val fpsLabelWidth = bigTextPaint.measureText(fpsLabel)
        canvas.drawText("FPS", leftPad + fpsLabelWidth + dp(3f), dp(19f), labelTextPaint)

        // Baris kecil: CPU · GPU · Suhu
        smallTextPaint.textSize = dp(9f)
        val cpuText = "CPU ${cpuPercent?.let { "$it%" } ?: "-"}"
        val gpuText = "GPU ${gpuPercent?.let { "$it%" } ?: "-"}"
        val tempText = formattedTemperature()
        val lineY = dp(33f)
        var x = leftPad
        canvas.drawText(cpuText, x, lineY, smallTextPaint)
        x += smallTextPaint.measureText(cpuText) + dp(7f)
        canvas.drawText(gpuText, x, lineY, smallTextPaint)
        x += smallTextPaint.measureText(gpuText) + dp(7f)
        canvas.drawText(tempText, x, lineY, smallTextPaint)

        // Indikator kecil "bisa digeser" di baris paling bawah.
        labelTextPaint.textSize = dp(7f)
        canvas.drawText("• geser", leftPad, h - dp(5f), labelTextPaint)
    }

    private fun drawClassicStyle(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = h / 2f

        bgPaint.color = Color.argb(190, 0, 0, 0)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        bigTextPaint.textSize = dp(11f)
        smallTextPaint.textSize = dp(10f)

        val fpsText = "$fps FPS"
        val cpuText = cpuPercent?.let { "CPU $it%" } ?: "CPU -"
        val tempText = formattedTemperature()

        val padding = dp(10f)
        val baseline = h / 2f + dp(3.8f)
        var x = padding

        bigTextPaint.color = Color.parseColor("#FF7CFC00") // hijau klasik game booster
        canvas.drawText(fpsText, x, baseline, bigTextPaint)
        x += bigTextPaint.measureText(fpsText) + dp(6f)

        smallTextPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText(cpuText, x, baseline, smallTextPaint)
        x += smallTextPaint.measureText(cpuText) + dp(6f)

        canvas.drawText(tempText, x, baseline, smallTextPaint)
    }
}
