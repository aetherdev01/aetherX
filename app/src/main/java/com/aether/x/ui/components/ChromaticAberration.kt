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
                float rn = len / length(center);
                float2 dir = d / max(len, 0.001);

                // Aberrasi lensa: lemah di tengah, membesar ke tepi.
                float amt = strength * (0.35 + 1.25 * rn * rn);
                // Distorsi barrel sangat halus supaya terasa seperti kaca tebal.
                float2 p = center + d * (1.0 - 0.0009 * strength * rn * rn);

                half4 cr = content.eval(p + dir * amt);
                half4 cg = content.eval(p);
                half4 cb = content.eval(p - dir * amt);

                half a = max(cr.a, max(cg.a, cb.a));
                return half4(min(cr.r, a), min(cg.g, a), min(cb.b, a), a);
            }
        """
    }
}
