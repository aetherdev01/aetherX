package com.aether.x.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Efek lensa "chromatic aberration": kanal merah & biru digeser berlawanan arah
 * secara radial dari tengah layer, makin kuat ke tepi — seperti pinggiran lensa kaca.
 *
 * - API 33+  : AGSL RuntimeShader lewat RenderEffect (sekali lintasan GPU, murah).
 * - API 31-32: fallback tiga salinan konten (R/G/B) digeser lalu dijumlahkan (BlendMode.Plus).
 *
 * [strengthPx] dibaca di dalam lambda graphicsLayer/draw, jadi mengubah nilainya
 * (animasi/drag) TIDAK memicu recomposition — hanya relayer/redraw.
 *
 * Lens chromatic aberration: R/B channels are split radially, stronger toward the edges.
 */
@Composable
fun rememberChromaticAberration(strengthPx: () -> Float): Modifier {
    val agsl = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ChromaticShader() else null
    }
    val paints = remember { Array(3) { channelPaint(it) } }

    return if (agsl != null) {
        Modifier.graphicsLayer {
            val s = strengthPx()
            renderEffect = if (s > 0.05f) agsl.effect(size.width, size.height, s) else null
        }
    } else {
        Modifier.drawWithContent {
            val s = strengthPx()
            if (s <= 0.05f) {
                drawContent()
                return@drawWithContent
            }
            val canvas = drawContext.canvas
            val bounds = Rect(Offset.Zero, size)
            val shifts = floatArrayOf(s, 0f, -s)
            // Layer grup: BlendMode.Plus hanya menjumlah antar-kanal, bukan ke latar.
            canvas.saveLayer(bounds, Paint())
            for (i in 0..2) {
                canvas.saveLayer(bounds, paints[i])
                translate(left = shifts[i], top = shifts[i] * 0.35f) {
                    this@drawWithContent.drawContent()
                }
                canvas.restore()
            }
            canvas.restore()
        }
    }
}

/** Paint yang hanya meloloskan satu kanal warna (0=R, 1=G, 2=B) dan menjumlahkannya. */
private fun channelPaint(channel: Int): Paint = Paint().apply {
    val m = FloatArray(20)
    m[channel * 5 + channel] = 1f // kanal warna terpilih
    m[18] = 1f                    // alpha tetap
    colorFilter = ColorFilter.colorMatrix(ColorMatrix(m))
    blendMode = BlendMode.Plus
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ChromaticShader {
    private val shader = RuntimeShader(AGSL)

    fun effect(widthPx: Float, heightPx: Float, strengthPx: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", widthPx, heightPx)
        shader.setFloatUniform("strength", strengthPx)
        return android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }

    private companion object {
        const val AGSL = """
            uniform shader content;
            uniform float2 size;
            uniform float strength;

            half4 main(float2 fc) {
                float2 center = size * 0.5;
                float2 d = fc - center;
                float len = length(d);
                float rn = clamp(len / length(center), 0.0, 1.0);
                float2 dir = d / max(len, 0.001);

                // Aberrasi lensa: tipis di tengah, membesar mulus ke tepi (kurva halus, tanpa patahan).
                float edge = rn * rn * (0.55 + 0.45 * rn);
                float amt = strength * (0.22 + 1.45 * edge);
                // Distorsi barrel sangat halus supaya terasa seperti kaca tebal.
                float2 p = center + d * (1.0 - 0.0009 * strength * rn * rn);

                // 7 sampel spektrum (biru -> merah) sepanjang arah radial. Jauh lebih halus dari
                // 3 salinan R/G/B: fringe menjadi gradasi pelangi, bukan bayangan ganda.
                float sr = 0.0; float sg = 0.0; float sb = 0.0; float sa = 0.0;
                float wR = 0.0; float wG = 0.0; float wB = 0.0;
                for (int i = 0; i < 7; i++) {
                    float t = float(i) / 3.0 - 1.0;            // -1 (biru) .. +1 (merah)
                    half4 s = content.eval(p + dir * amt * t);
                    float wr = smoothstep(-0.6, 1.0, t);
                    float wb = smoothstep(0.6, -1.0, t);
                    float wg = 1.0 - 0.6 * abs(t);
                    sr += s.r * wr; wR += wr;
                    sg += s.g * wg; wG += wg;
                    sb += s.b * wb; wB += wb;
                    sa += s.a;
                }
                float a = sa / 7.0;
                return half4(half(min(sr / wR, a)), half(min(sg / wG, a)), half(min(sb / wB, a)), half(a));
            }
        """
    }
}
