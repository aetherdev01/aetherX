package com.aether.x.ui.main

import androidx.compose.animation.core.Animatable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.AuroraBlurLayer
import com.aether.x.ui.components.LocalAuroraBackdrop
import com.aether.x.ui.components.NativeBlurWindow
import com.aether.x.ui.components.NativeBlurWindowState
import com.aether.x.ui.components.blurRadiusFor
import com.aether.x.ui.components.liquidGlassTintAlpha
import com.aether.x.ui.components.rememberCrossWindowBlurEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shader AGSL lensa (Android 13+). Dijalankan sebagai RenderEffect pada lapisan salinan
 * ikon: memperbesar isi lensa, membengkokkan isi di dekat tepi (kompresi seperti kaca
 * tebal), dan memisahkan kanal R/G/B di tepi (chromatic aberration). Sekaligus menjadi
 * mask bentuk lensa (stadium) dengan tepi anti-alias, jadi tidak perlu clipPath.
 *
 * Uniform (semua dalam px, ruang lokal lapisan):
 *  - lens = (pusatX, pusatY, setengahLebar, setengahTinggi)
 *  - mag  = pembesaran pusat lensa (>= 1)
 *  - bend = kekuatan pembengkokan tepi 0..1
 *  - disp = kekuatan dispersi warna (fraksi skala)
 *  - edge = lebar zona tepi yang membengkok
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float4 lens;
uniform float mag;
uniform float bend;
uniform float disp;
uniform float edge;

float sdStadium(float2 p, float2 halfSize) {
    float r = halfSize.y;
    float2 q = abs(p) - float2(halfSize.x - r, 0.0);
    return length(float2(max(q.x, 0.0), q.y)) - r;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - lens.xy;
    float d = sdStadium(p, lens.zw);
    if (d > 1.0) {
        return half4(0.0);
    }
    // t: 0 di bagian dalam, 1 tepat di tepi. Kuadratik agar bagian tengah tetap bersih.
    float t = 1.0 - smoothstep(0.0, edge, -d);
    t = pow(t, 2.0);
    // Tengah: sampel lebih dekat ke pusat (memperbesar). Tepi: sampel lebih JAUH dari
    // pusat (isi tampak tertekan/menekuk ke arah tepi).
    float s = mix(1.0 / mag, 1.0 + 0.10 * bend, t);
    float2 pr = lens.xy + p * s * (1.0 + disp * t);
    float2 pg = lens.xy + p * s;
    float2 pb = lens.xy + p * s * (1.0 - disp * t);
    half4 cr = content.eval(pr);
    half4 cg = content.eval(pg);
    half4 cb = content.eval(pb);
    half a = max(cr.a, max(cg.a, cb.a));
    half4 col = half4(cr.r, cg.g, cb.b, a);
    float mask = clamp(0.5 - d, 0.0, 1.0);
    return col * half(mask);
}
"""

/** Seluruh akses RuntimeShader dikurung di sini agar perangkat < Android 13 tidak memuatnya. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LensShader {
    fun create(): Any = RuntimeShader(LENS_AGSL)

    fun effect(
        shader: Any,
        centerX: Float,
        centerY: Float,
        halfW: Float,
        halfH: Float,
        mag: Float,
        bend: Float,
        disp: Float,
        edge: Float,
    ): ComposeRenderEffect {
        val s = shader as RuntimeShader
        s.setFloatUniform("lens", centerX, centerY, halfW, halfH)
        s.setFloatUniform("mag", mag)
        s.setFloatUniform("bend", bend)
        s.setFloatUniform("disp", disp)
        s.setFloatUniform("edge", edge)
        return AndroidRenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}

/** Efek tarik-ulur di tepi (rubber band) ala iOS: makin jauh melewati ujung, makin berat. */
private fun rubberBand(overshoot: Float, limit: Float = 0.45f, c: Float = 0.55f): Float =
    (1f - 1f / (overshoot * c / limit + 1f)) * limit

/** Kotak lensa pada satu frame (px). [speed] 0f..1f = kecepatan gerak ternormalisasi. */
private class LensRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val speed: Float,
) {
    val centerX: Float get() = left + width / 2f
}

/** Path rounded-rect penuh (stadium) untuk clip bentuk lensa. */
private fun lensPath(r: LensRect): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = r.left,
            top = r.top,
            right = r.left + r.width,
            bottom = r.top + r.height,
            cornerRadius = CornerRadius(r.height / 2f, r.height / 2f),
        ),
    )
}

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk ikon + label saat dilewati/ditempati capsule. */
    val accent: Color,
)

/** true = coba window blur native OS (dialog); default menggunakan blur Haze inline. */
private const val USE_NATIVE_NAV_BLUR = false

/**
 * Tab bar capsule v3.14 — Liquid Glass dengan motion iOS-inspired.
 *
 *  - Jalur utama memakai Haze untuk memburamkan konten aktual di belakang navbar.
 *    Gaya iOS 27: tanpa drop shadow, border lebih tegas, kaca sedikit lebih solid.
 *  - AuroraBlurLayer hanya menjadi fallback ketika HazeState tidak tersedia.
 *  - Opsional: USE_NATIVE_NAV_BLUR memindahkan navbar ke window dialog dengan blur
 *    native OS. Blur hanya berlaku pada latar capsule; floating pill tidak di-blur.
 *  - Pill digambar di fase DRAW dan semua nilai animasinya dibaca dari lambda
 *    draw / graphicsLayer, jadi saat meluncur TIDAK ada recomposition.
 *  - UKURAN: lebar pill tetap (satu slot), tidak mengikuti panjang label tab.
 *  - TAHAN: begitu disentuh pill langsung membesar menjadi "lensa kaca" dengan efek
 *    jelly dan tetap besar sampai dilepas. Lebar dan tinggi dianimasikan dengan dua
 *    pegas berbeda sehingga bergoyang tidak serempak.
 *  - GESER: capsule mengejar jari dengan pegas cepat (bukan menempel kaku), jadi
 *    posisi dan kecepatannya kontinu. Kecepatan itu menentukan stretch: memanjang
 *    searah gerak (sisi depan lebih maju, tinggi menipis) dan sedikit mengecil saat
 *    cepat, membesar lagi saat melambat/berhenti. Melewati ujung -> tertahan (rubber
 *    band). Dilepas -> mendarat di tab terdekat dengan proyeksi kecepatan (flick) dan
 *    overshoot ringan, lalu mengecil dengan jelly.
 *  - LENSA: ikon + label yang sedang dilewati/ditempati capsule berubah ke warna
 *    aksennya secara halus (berdasarkan jarak capsule ke tiap ikon), lalu kembali
 *    abu-abu setelah capsule menjauh — bukan hanya tab tujuan.
 *  - CERMIN: saat ditahan/digeser, lensa menampilkan salinan baris ikon yang diperbesar
 *    (berpusat di tengah lensa -> parallax), sedangkan ikon asli di bawah lensa
 *    dipotong keluar agar tidak dobel. Di Android 13+ shader AGSL membengkokkan isi di
 *    dekat tepi dan memisah kanal warna (pembiasan sungguhan); di bawahnya memakai
 *    skala + clip biasa. Tepi lensa diberi pita refraksi atas/bawah dan dispersi warna
 *    yang makin kuat saat bergerak cepat.
 *  - Perpindahan cepat dan terkontrol; sedikit overshoot untuk kesan spring iOS.
 *  - Satu pointerInput saja (tap + drag digabung).
 */
@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * State Haze bersama dari AetherBackground. Dashboard sudah mendaftarkan
     * kontennya pada state ini; tab lain menambahkan sumber konten sendiri.
     * null = fallback ke blur latar aurora.
     */
    hazeState: HazeState? = null,
) {
    if (items.isEmpty()) return

    val count = items.size
    val lastIndex = count - 1
    val barShape = RoundedCornerShape(percent = 50)
    val palette = LocalAetherPalette.current
    val style = LocalAetherStyle.current
    val blur = style.blur
    val glassLevel = style.glassLevel
    val glassTintAlpha = liquidGlassTintAlpha(glassLevel)
    val surface = palette.surface
    val isDark = palette.isDark
    val scope = rememberCoroutineScope()

    // Posisi pill dalam satuan "indeks slot" (0f..lastIndex). Dibaca HANYA di
    // lambda draw / graphicsLayer -> tidak memicu recomposition.
    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    // Posisi yang DITAMPILKAN saat drag (dikejar pegas ke dragTarget) dan kecepatannya.
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // Posisi tujuan menurut jari (sudah melewati rubber band di ujung), satuan slot.
    var dragTarget by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // Kecepatan capsule saat drag dalam slot/detik (kontinu, hasil simulasi pegas).
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    // Saat dilepas, capsule mendarat pada tab terdekat dengan pegas bergaya iOS — lebih
    // santai, tidak buru-buru, dengan overshoot lembut.
    // travelSpec dipakai untuk SATU perjalanan utuh (tekan -> meluncur -> mendarat) agar
    // tidak ada "sambungan"/mengekor yang terlihat saat jari dilepas di tengah jalan.
    // releaseSpec khusus untuk mendaratkan capsule setelah geser cepat (fling), membawa
    // kecepatan lepas jari supaya gerakannya terasa menyambung.
    // Overshoot ~4% (damping 0.72) memberi kesan "mendarat" seperti iOS; kekakuan sedang
    // agar tidak terburu-buru.
    val travelSpec = remember { spring<Float>(dampingRatio = 0.78f, stiffness = 320f) }
    val releaseSpec = remember { spring<Float>(dampingRatio = 0.72f, stiffness = 380f) }
    // Mencegah LaunchedEffect menjalankan animasi kedua setelah gesture sudah settle.
    var settledByTouch by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, count) {
        val target = selectedIndex.coerceIn(0, lastIndex)
        if (settledByTouch) {
            settledByTouch = false
            return@LaunchedEffect
        }
        if (!isDragging && abs(pillPosition.value - target) > 0.01f) {
            pillPosition.animateTo(target.toFloat(), travelSpec)
        }
    }

    // Saat drag, capsule TIDAK menempel kaku di jari: ia mengejar posisi jari dengan pegas
    // cepat (response ~0.10 dtk, sedikit underdamped) seperti interactiveSpring iOS. Hasilnya
    // gerak mengekor yang halus, dan kecepatan (dipakai untuk stretch + saat dilepas) kontinu,
    // bukan turunan sentuhan yang bergerigi. Saat jari diam pegas menetap sendiri -> kecepatan 0.
    // Disimulasikan dengan sub-langkah tetap agar stabil dan sama di 60/120 Hz.
    LaunchedEffect(isDragging) {
        if (isDragging) {
            val omega = 2f * Math.PI.toFloat() / 0.10f
            val stiffness = omega * omega
            val damping = 2f * 0.82f * omega
            var x = dragPosition
            var v = dragVelocity
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                var remaining = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                while (remaining > 0f) {
                    val step = min(remaining, 1f / 240f)
                    v += (stiffness * (dragTarget - x) - damping * v) * step
                    x += v * step
                    remaining -= step
                }
                dragPosition = x
                dragVelocity = v
            }
        }
    }

    // Pill menjadi lebih bening saat disentuh/digeser, lalu kembali ke abu-abu
    // netral dengan transisi singkat saat dilepas.
    val pillSolidness = remember { Animatable(1f) }
    LaunchedEffect(pressed, isDragging) {
        pillSolidness.animateTo(
            targetValue = if (pressed || isDragging) 0f else 1f,
            animationSpec = tween(durationMillis = 150),
        )
    }

    // Efek JELLY: lebar dan tinggi memakai pegas berbeda. Redaman rendah membuat nilai
    // melampaui target lalu berayun balik; perbedaan frekuensi antara lebar & tinggi
    // membuat bentuknya bergoyang seperti jeli, bukan sekadar membesar.
    // Ditekan -> langsung membesar dan TETAP besar selama jari menempel (seperti lensa iOS),
    // dilepas -> mengecil dengan jelly. Mengecil/memanjang saat meluncur TIDAK lagi memakai
    // fase diskrit: itu dihitung kontinu dari kecepatan di lensaRect (lebih halus).
    val popW = remember { Animatable(0f) }
    val popH = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        val target = if (pressed) 1f else 0f
        launch {
            popW.animateTo(
                target,
                spring(
                    dampingRatio = if (pressed) 0.62f else 0.60f,
                    stiffness = if (pressed) 420f else 340f,
                ),
            )
        }
        launch {
            popH.animateTo(
                target,
                spring(
                    dampingRatio = if (pressed) 0.56f else 0.52f,
                    stiffness = if (pressed) 350f else 280f,
                ),
            )
        }
    }

    // Seberapa besar capsule "menutupi" ikon ke-[index] (0f..1f). Dibaca di fase
    // draw/graphicsLayer: ikon yang dilewati capsule berwarna, yang ditinggalkan
    // kembali abu-abu. Rentang 0.28..0.72 slot ~ setengah lebar capsule + tepi lembut.
    val coverageOf: (Int) -> Float = { index ->
        val position = if (isDragging) dragPosition else pillPosition.value
        val distance = abs(position - index)
        val t = ((distance - 0.28f) / 0.44f).coerceIn(0f, 1f)
        1f - t * t * (3f - 2f * t)
    }

    // Geometri lensa/pill pada saat ini (posisi, lebar, tinggi, kecepatan). Satu sumber
    // kebenaran untuk pill dasar, clip baris normal, clip salinan lensa, dan rim — jadi
    // semuanya selalu persis sejajar. Dipanggil dari fase draw / graphicsLayer saja.
    val lensRectOf: (Size, Density, Dp) -> LensRect = { size, d, growBy ->
        with(d) {
            val slot = size.width / count
            val inset = 4.dp.toPx()
            val pos = if (isDragging) dragPosition else pillPosition.value
            val velocity = if (isDragging) dragVelocity else pillPosition.velocity
            // popW/popH bisa >1 atau <0 sesaat karena overshoot pegas -> inilah jelly-nya.
            val pw = popW.value
            val ph = popH.value

            // Lebar dasar TETAP (satu slot dikurangi celah), tidak mengikuti panjang label.
            val baseW = slot - 8.dp.toPx()
            val baseH = size.height - inset * 2f

            // Squash & stretch searah geser. Kecepatan di sini sudah kontinu (hasil pegas),
            // lalu diberi kurva ease-out agar gerak pelan pun sudah terasa melar.
            val raw = (abs(velocity) / 7f).coerceIn(0f, 1f)
            val speed = raw * (2f - raw)
            val direction = if (velocity >= 0f) 1f else -1f
            // Meluncur cepat: bagian yang membesar (jelly tahan) sedikit mengecil, lalu
            // membesar lagi saat melambat/berhenti -> "mengecil-membesar mengikuti geser".
            val grow = 1f - 0.30f * speed
            val h = (baseH * (1f - 0.09f * speed) + growBy.toPx() * ph * grow)
                .coerceAtLeast(baseH * 0.7f)
            val stretchPx = baseW * 0.26f * speed
            val w = (baseW + growBy.toPx() * pw * grow + stretchPx).coerceAtLeast(h)
            // Sisi depan memanjang ~70%, belakang ~30%.
            val cx = (pos + 0.5f) * slot + direction * stretchPx * 0.2f
            LensRect(
                left = cx - w / 2f,
                top = (size.height - h) / 2f,
                width = w,
                height = h,
                speed = speed,
            )
        }
    }

    // Shader lensa hanya dibuat di Android 13+ (null di bawahnya -> jalur skala + clip).
    val lensShader: Any? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LensShader.create() else null
    }

    val blurOn = blur > 0.01f && (hazeState != null || LocalAuroraBackdrop.current != null)
    // Radius lebih rendah menekan biaya blur off-screen, terutama saat scroll/transisi.
    val barBlurRadius = blurRadiusFor(blur).coerceAtMost(14f).dp
    // Blur NATIVE sistem (window blur Android 12+). Bila OS mematikannya (hemat
    // baterai dll.) atau slider = 0, jatuh ke blur sendiri (AuroraBlurLayer) di bawah.
    val crossWindowBlur = rememberCrossWindowBlurEnabled()
    val useNative = USE_NATIVE_NAV_BLUR && crossWindowBlur && blur > 0.01f
    val density = LocalDensity.current
    val hostView = LocalView.current
    val nativeState = remember { NativeBlurWindowState() }
    var nativeReady by remember { mutableStateOf(false) }

    // Gestur tap + drag. Dipakai di kedua mode (window native maupun inline).
    val gestureModifier = Modifier
        .systemGestureExclusion()
            .pointerInput(count) {
                awaitEachGesture {
                    val slot = size.width.toFloat() / count
                    if (slot <= 0f) return@awaitEachGesture

                    fun slotAt(x: Float) = ((x / slot) - 0.5f).coerceIn(0f, lastIndex.toFloat())

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    settledByTouch = false
                    pressed = true
                    isDragging = false
                    dragVelocity = 0f

                    val downX = down.position.x
                    val touchTarget = slotAt(downX).roundToInt().coerceIn(0, lastIndex)
                    var dragging = false
                    var dragOriginX = downX
                    var dragOriginPosition = pillPosition.value

                    // Saat tap, capsule mulai meluncur menuju tab yang disentuh.
                    // Jika jari mulai digeser, animasi ini dibatalkan dan posisi
                    // capsule dilanjutkan langsung dari posisi visual terakhir.
                    var moveJob: Job? = scope.launch {
                        pillPosition.animateTo(touchTarget.toFloat(), travelSpec)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val x = change.position.x
                        val travel = x - downX

                        if (!dragging && abs(travel) > viewConfiguration.touchSlop) {
                            dragging = true
                            // cancel() is non-suspending and is safe inside AwaitPointerEventScope.
                            // The current Animatable value is retained as the drag starting point.
                            moveJob?.cancel()
                            moveJob = null
                            isDragging = true
                            // Sambung dari posisi capsule saat animasi berhenti,
                            // bukan lompat ke slot di bawah jari.
                            dragOriginX = x
                            dragOriginPosition = pillPosition.value
                            dragPosition = dragOriginPosition
                            dragTarget = dragOriginPosition
                            // Bawa kecepatan luncur tap (bila ada) agar stretch tidak putus.
                            dragVelocity = pillPosition.velocity
                        }

                        if (dragging) {
                            // Hanya menetapkan TUJUAN menurut jari. Capsule mengejarnya lewat
                            // pegas (lihat LaunchedEffect(isDragging)), sehingga posisi dan
                            // kecepatannya kontinu. Melewati ujung -> tertahan (rubber band).
                            val raw = dragOriginPosition + (x - dragOriginX) / slot
                            dragTarget = when {
                                raw < 0f -> -rubberBand(-raw)
                                raw > lastIndex -> lastIndex + rubberBand(raw - lastIndex)
                                else -> raw
                            }
                        }

                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        if (abs(travel) > viewConfiguration.touchSlop) change.consume()
                    }

                    val target = if (dragging) {
                        // Proyeksikan posisi sedikit ke depan mengikuti kecepatan lepas jari,
                        // jadi lemparan (flick) cepat bisa pindah ke tab berikutnya.
                        (dragPosition + dragVelocity.coerceIn(-12f, 12f) * 0.12f)
                            .roundToInt()
                            .coerceIn(0, lastIndex)
                    } else {
                        touchTarget
                    }
                    // Jangan memanggil suspend API langsung dari AwaitPointerEventScope.
                    // Mutasi Animatable dijalankan pada coroutine scope biasa di bawah.
                    settledByTouch = target != selectedIndex
                    pressed = false

                    if (dragging) {
                        // moveJob sudah dibatalkan saat drag mulai (lihat atas); di sini
                        // capsule mendarat dari posisi geser terakhir, dengan kecepatan
                        // lepas jari dibawa ke releaseSpec agar gerakannya menyambung.
                        val releasePosition = dragPosition
                        val releaseVelocity = dragVelocity.coerceIn(-12f, 12f)
                        scope.launch {
                            // Sinkronkan posisi terakhir saat drag sebelum mulai settle,
                            // sehingga capsule tidak snap saat jari dilepas.
                            pillPosition.snapTo(releasePosition)
                            isDragging = false
                            dragVelocity = 0f
                            pillPosition.animateTo(
                                targetValue = target.toFloat(),
                                animationSpec = releaseSpec,
                                initialVelocity = releaseVelocity,
                            )
                        }
                    } else {
                        // TAP: moveJob sudah meluncur sejak jari ditekan, memakai travelSpec
                        // yang sama dari awal sampai akhir. Biarkan saja berjalan sampai
                        // selesai — tidak dibatalkan lalu diganti pegas lain, supaya tidak
                        // ada "sambungan"/mengekor terlihat saat jari dilepas.
                        isDragging = false
                        dragVelocity = 0f
                        if (moveJob?.isActive != true && abs(pillPosition.value - target) > 0.01f) {
                            // Jaga-jaga: moveJob sudah selesai lebih dulu (tab dekat +
                            // tekanan lama) tapi posisi belum pas, susulkan dengan pegas sama.
                            scope.launch {
                                pillPosition.animateTo(target.toFloat(), travelSpec)
                            }
                        }
                    }
                    onSelect(target)
                }
            }

    // Lapisan kapsul: latar kaca, pill, dan item. [nat] = dirender di window native.
    val layers: @Composable (Boolean) -> Unit = { nat ->
        // Di window native, window selebar kapsul meng-clip pill -> sembulan dikecilkan
        // supaya tidak terpotong. Mode inline bebas menyembul melewati tepi capsule.
        // Pill membesar SAMA besar di semua sisi (lebar & tinggi bertambah sebesar growBy),
        // jadi jarak pill ke tepi capsule tetap seragam dan lengkungnya tetap konsentris
        // dengan lengkung navbar (radius pill = tinggi/2, navbar = tinggi/2).
        val growBy = if (nat) 4.dp else 14.dp
        // Tambahan pembesaran salinan ikon di dalam lensa (di atas skala ikon bawaan).
        val lensMagnify = 0f

        // Border gaya iOS 27: lebih tegas dan jelas, bukan lagi mengandalkan bayangan.
        val rimBrush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.28f else 0.85f),
                (if (isDark) Color.White else Color(0xFF8E8E93)).copy(
                    alpha = if (isDark) 0.14f else 0.34f,
                ),
            ),
        )

        // ── Latar kaca capsule ───────────────────────────────────────────
        if (nat) {
            // Blur + tint dikerjakan window (lihat NativeBlurWindow); di sini cukup garis tepi.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 1.dp, brush = rimBrush, shape = barShape),
            )
        } else {
            // Fallback saja: bila HazeState tidak tersedia, buramkan salinan aurora.
            // Tanpa drop shadow (iOS 27 menghilangkan bayangan pada Liquid Glass).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(barShape),
            ) {
                if (blurOn) {
                    if (hazeState != null) {
                        // Pakai satu backend blur saja. Haze memburamkan konten di
                        // belakang kapsul; jangan menumpuknya dengan RenderEffect Aurora.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeEffect(state = hazeState) {
                                    // Radius navbar dibatasi untuk menjaga beban GPU tetap wajar.
                                    blurRadius = barBlurRadius
                                    noiseFactor = 0.015f
                                    backgroundColor = surface.copy(alpha = 0.035f)
                                },
                        )
                    } else {
                        AuroraBlurLayer(
                            blurRadius = barBlurRadius,
                            shape = barShape,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.94f,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                // Pakai warna surface tema yang sama dengan kartu AetherX,
                                // bukan tint putih khusus navbar. Dengan begitu Dark/Light dan
                                // level Jernih, Tipis, Seimbang, Pekat, Solid konsisten.
                                if (blurOn) {
                                    listOf(
                                        surface.copy(alpha = (glassTintAlpha * 0.92f).coerceIn(0f, 0.88f)),
                                        surface.copy(alpha = (glassTintAlpha * 0.76f).coerceIn(0f, 0.82f)),
                                    )
                                } else {
                                    // Samakan fallback tanpa blur dengan baseSurface pada
                                    // aetherCardSurface agar tiap level tetap punya hasil serupa.
                                    listOf(
                                        surface.copy(alpha = 0.40f + 0.48f * glassLevel),
                                        surface.copy(alpha = 0.34f + 0.42f * glassLevel),
                                    )
                                },
                            ),
                        )
                        .border(width = 1.dp, brush = rimBrush, shape = barShape),
                )
            }
        }

        // ── Pill dasar (draw phase, TIDAK di-blur) ───────────────────────
        // Isi pill: solid abu-abu saat diam, kaca tipis saat ditahan/digeser. Garis tepi
        // lensa dan efek refraksi digambar di lapisan ATAS (setelah ikon), lihat bawah.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val r = lensRectOf(size, this, growBy)
                    val left = r.left
                    val top = r.top
                    val w = r.width
                    val h = r.height

                    // Neutral iOS-like gray; only icon tint uses the selected accent.
                    val solidness = pillSolidness.value.coerceIn(0f, 1f)
                    // lens = 1 saat ditahan/digeser (kaca bening), 0 saat diam (pill solid).
                    val lens = 1f - solidness
                    val pillGrayTop = if (isDark) Color(0xFF3F3F41) else Color(0xFFE8E8ED)
                    val pillGrayBottom = if (isDark) Color(0xFF303033) else Color(0xFFE0E0E5)
                    val corner = CornerRadius(h / 2f, h / 2f)

                    // Bayangan lembut tepat di bawah lensa (hanya di luar bentuk lensa).
                    if (lens > 0.01f) {
                        clipPath(lensPath(r), clipOp = ClipOp.Difference) {
                            for (i in 1..4) {
                                val g = i * 1.2.dp.toPx()
                                drawRoundRect(
                                    color = Color.Black.copy(
                                        alpha = (if (isDark) 0.10f else 0.06f) * lens * (5 - i) / 4f,
                                    ),
                                    topLeft = Offset(left - g, top - g + 2.dp.toPx()),
                                    size = Size(w + g * 2f, h + g * 2f),
                                    cornerRadius = CornerRadius(h / 2f + g, h / 2f + g),
                                )
                            }
                        }
                    }

                    // Solidness=0 saat sliding, 1 setelah dilepas.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                pillGrayTop.copy(alpha = solidness),
                                pillGrayBottom.copy(alpha = solidness),
                            ),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                    )
                    // Lapisan kaca tipis agar lensa tetap terbaca di atas konten saat bening.
                    drawRoundRect(
                        color = Color.White.copy(alpha = (if (isDark) 0.07f else 0.24f) * lens),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = if (isDark) {
                            Color.White.copy(alpha = 0.10f + 0.08f * solidness)
                        } else {
                            Color.Black.copy(alpha = 0.05f + 0.05f * solidness)
                        },
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 0.8.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(
                                    alpha = (if (isDark) 0.04f else 0.05f) +
                                        (if (isDark) 0.06f else 0.12f) * solidness,
                                ),
                                Color.Transparent,
                            ),
                            startY = top,
                            endY = top + h * 0.55f,
                        ),
                        topLeft = Offset(left + 2.dp.toPx(), top + 1.dp.toPx()),
                        size = Size((w - 4.dp.toPx()).coerceAtLeast(0f), h * 0.55f),
                        cornerRadius = corner,
                    )
                },
        )

        // ── Item (baris NORMAL) ──────────────────────────────────────────
        // Saat lensa aktif, area di bawah lensa DIPOTONG keluar supaya ikon asli tidak
        // terlihat dobel; yang tampil di area itu hanya salinan yang diperbesar (di bawah).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val lens = 1f - pillSolidness.value.coerceIn(0f, 1f)
                    if (lens > 0.01f) {
                        val r = lensRectOf(size, this, growBy)
                        clipPath(lensPath(r), clipOp = ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        drawContent()
                    }
                },
        ) {
            NavItemsRow(items = items, coverageOf = coverageOf, pop = { popW.value })
        }

        // ── Item (salinan LENSA / "cermin") ──────────────────────────────
        // Salinan baris ikon yang berpusat di tengah lensa, sehingga ikon di dalamnya tampak
        // lebih besar dan bergeser lebih cepat daripada ikon di luar (parallax) -> terasa
        // seperti lup yang melewati navbar. Pada lens ~ 0 hasilnya identik dengan baris normal
        // (tidak ada lompatan saat fade-in), dan tidak digambar sama sekali saat diam.
        //  - Android 13+ : shader AGSL (RenderEffect) memperbesar isi, membengkokkan isi di
        //    tepi, memisah kanal warna, sekaligus menjadi mask bentuk lensa.
        //  - Di bawahnya : skala graphicsLayer + clipPath (tanpa pembiasan).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .drawWithContent {
                    val lens = 1f - pillSolidness.value.coerceIn(0f, 1f)
                    if (lens > 0.01f) {
                        if (lensShader != null) {
                            // Mask bentuk lensa (anti-alias) dikerjakan shader.
                            drawContent()
                        } else {
                            val r = lensRectOf(size, this, growBy)
                            clipPath(lensPath(r)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val lens = 1f - pillSolidness.value.coerceIn(0f, 1f)
                    val r = lensRectOf(size, this, growBy)
                    // Total pembesaran ikon di dalam lensa ~1.5x (skala ikon bawaan + ini).
                    val magnify = 1f + lensMagnify * lens
                    if (lensShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        scaleX = 1f
                        scaleY = 1f
                        renderEffect = if (lens > 0.01f) {
                            LensShader.effect(
                                shader = lensShader,
                                centerX = r.centerX,
                                centerY = r.top + r.height / 2f,
                                halfW = r.width / 2f,
                                halfH = r.height / 2f,
                                mag = magnify,
                                bend = lens,
                                // Dispersi warna makin kuat saat lensa bergerak cepat.
                                disp = (0.005f + 0.015f * r.speed) * lens,
                                edge = 9.dp.toPx(),
                            )
                        } else {
                            null
                        }
                    } else {
                        renderEffect = null
                        scaleX = magnify
                        scaleY = magnify
                        transformOrigin = TransformOrigin(
                            pivotFractionX = (r.centerX / size.width).coerceIn(0f, 1f),
                            pivotFractionY = 0.5f,
                        )
                    }
                },
        ) {
            NavItemsRow(items = items, coverageOf = coverageOf, pop = { popW.value })
        }

        // ── Rim lensa: refraksi + dispersi warna (di ATAS ikon) ──────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val lens = (1f - pillSolidness.value).coerceIn(0f, 1f)
                    if (lens <= 0.01f) return@drawBehind
                    val r = lensRectOf(size, this, growBy)
                    val left = r.left
                    val top = r.top
                    val w = r.width
                    val h = r.height
                    val corner = CornerRadius(h / 2f, h / 2f)

                    // 1) Pita gelap tipis di tepi atas & bawah: isi lensa menekuk di tepi,
                    //    seperti yang tampak pada lensa iOS.
                    val bandH = 5.dp.toPx().coerceAtMost(h * 0.18f)
                    val bandAlpha = (if (isDark) 0.16f else 0.08f) * lens
                    clipPath(lensPath(r)) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = bandAlpha), Color.Transparent),
                                startY = top,
                                endY = top + bandH,
                            ),
                            topLeft = Offset(left, top),
                            size = Size(w, bandH),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = bandAlpha)),
                                startY = top + h - bandH,
                                endY = top + h,
                            ),
                            topLeft = Offset(left, top + h - bandH),
                            size = Size(w, bandH),
                        )
                    }

                    // 2) Dispersi warna (chromatic aberration): tiga garis tipis merah/hijau/biru
                    //    bertumpuk dari tepi ke dalam, paling kuat di ujung kiri & kanan dan
                    //    makin kuat saat lensa bergerak cepat.
                    val fringe = lens * (0.40f + 0.60f * r.speed)
                    val fringeColors = listOf(
                        Color(0xFFFF5A4D),
                        Color(0xFF4DFF9A),
                        Color(0xFF4D7DFF),
                    )
                    fringeColors.forEachIndexed { i, c ->
                        val inset = (0.5f + i).dp.toPx()
                        val a = (if (lensShader != null) 0.22f else 0.35f) * fringe
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0f to c.copy(alpha = a),
                                0.22f to c.copy(alpha = a * 0.35f),
                                0.78f to c.copy(alpha = a * 0.35f),
                                1f to c.copy(alpha = a),
                                startX = left,
                                endX = left + w,
                            ),
                            topLeft = Offset(left + inset, top + inset),
                            size = Size(
                                (w - inset * 2f).coerceAtLeast(0f),
                                (h - inset * 2f).coerceAtLeast(0f),
                            ),
                            cornerRadius = CornerRadius(
                                (h / 2f - inset).coerceAtLeast(0f),
                                (h / 2f - inset).coerceAtLeast(0f),
                            ),
                            style = Stroke(width = 1.1.dp.toPx()),
                        )
                    }

                    // 3) Rim spekular ala iOS: terang di kiri-atas & kanan-bawah, redup di dua
                    //    sudut lainnya (sweep gradient), ditambah glow dalam yang tipis.
                    val k = if (isDark) 0.60f else 1f
                    val low = if (isDark) Color.White else Color(0xFF8E8E93)
                    val sweepCenter = Offset(left + w / 2f, top + h / 2f)
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            0.000f to low.copy(alpha = 0.22f * lens),
                            0.125f to Color.White.copy(alpha = 0.85f * k * lens),
                            0.250f to low.copy(alpha = 0.22f * lens),
                            0.500f to low.copy(alpha = 0.16f * lens),
                            0.625f to Color.White.copy(alpha = 0.95f * k * lens),
                            0.750f to low.copy(alpha = 0.30f * lens),
                            1.000f to low.copy(alpha = 0.22f * lens),
                            center = sweepCenter,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 1.3.dp.toPx()),
                    )
                    val glowInset = 2.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.07f * k * lens),
                        topLeft = Offset(left + glowInset, top + glowInset),
                        size = Size((w - glowInset * 2f).coerceAtLeast(0f), (h - glowInset * 2f).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(
                            (h / 2f - glowInset).coerceAtLeast(0f),
                            (h / 2f - glowInset).coerceAtLeast(0f),
                        ),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                },
        )
    }

    // Lapisan luar tetap selebar layar agar navbar terletak di tengah pada tablet.
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        if (useNative) {
            // Penampung: hanya menyediakan ruang + posisi. Kapsul aslinya hidup di
            // window dialog yang ditempelkan tepat di atas penampung ini.
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.xxl + Spacing.sm)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        nativeState.updateBounds(
                            hostView,
                            AndroidRect(
                                b.left.roundToInt(),
                                b.top.roundToInt(),
                                b.right.roundToInt(),
                                b.bottom.roundToInt(),
                            ),
                        )
                        if (!nativeReady) nativeReady = true
                    },
            )
            if (nativeReady) {
                NativeBlurWindow(
                    state = nativeState,
                    blurRadiusPx = with(density) { blurRadiusFor(blur).coerceAtMost(14f).dp.roundToPx() },
                    tint = surface.copy(alpha = (glassTintAlpha * 0.84f).coerceIn(0f, 0.80f)),
                ) {
                    Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
                        layers(true)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.xxl + Spacing.sm)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .then(gestureModifier),
            ) {
                layers(false)
            }
        }
    }
}

/** Baris semua tab. Dipakai dua kali: baris normal dan salinan di dalam lensa. */
@Composable
private fun NavItemsRow(
    items: List<AetherNavItem>,
    coverageOf: (Int) -> Float,
    pop: () -> Float,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavBarItem(
                item = item,
                coverage = { coverageOf(index) },
                pop = pop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * Satu tab. Warna TIDAK diberikan lewat parameter tint, melainkan diwarnai di fase draw:
 * ikon + label digambar hitam di layer offscreen, lalu ditimpa warna hasil
 * `lerp(abu-abu, aksen, coverage)` dengan BlendMode.SrcIn. Karena [coverage] dibaca di
 * lambda draw/graphicsLayer, warna mengikuti posisi capsule tiap frame tanpa recomposition.
 */
@Composable
private fun NavBarItem(
    item: AetherNavItem,
    /** 0f..1f — seberapa besar capsule menutupi tab ini saat ini. */
    coverage: () -> Float,
    /** Progres tahan/jelly capsule (bisa >1 sesaat); dipakai untuk membesarkan ikon sedikit. */
    pop: () -> Float,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = item.accent

    Column(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    color = lerp(muted, accent, coverage().coerceIn(0f, 1f)),
                    blendMode = BlendMode.SrcIn,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = Color.Black,
            modifier = Modifier.graphicsLayer {
                // Ukuran ikon + label tetap sama di dalam maupun di luar lensa.
                val s = 1f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin.Center
            },
        )
        Text(
            text = item.label,
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, start = 2.dp, end = 2.dp),
        )
    }
}
