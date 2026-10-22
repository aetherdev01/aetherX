package com.aether.x.ui.main

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.AuroraBlurLayer
import com.aether.x.ui.components.LocalAuroraBackdrop
import com.aether.x.ui.components.NativeBlurWindow
import com.aether.x.ui.components.NativeBlurWindowState
import com.aether.x.ui.components.blurRadiusFor
import com.aether.x.ui.components.rememberCrossWindowBlurEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk ikon + label saat dilewati/ditempati capsule. */
    val accent: Color,
)

/** true = coba window blur native OS (dialog); default menggunakan blur Haze inline. */
private const val USE_NATIVE_NAV_BLUR = false

/**
 * Tab bar capsule v3.12 — Liquid Glass (acuan iOS 26/27).
 *
 *  - Jalur utama memakai Haze untuk memburamkan konten aktual di belakang navbar.
 *    Gaya iOS 27: tanpa drop shadow, border lebih tegas, kaca sedikit lebih solid.
 *  - AuroraBlurLayer hanya menjadi fallback ketika HazeState tidak tersedia.
 *  - Opsional: USE_NATIVE_NAV_BLUR memindahkan navbar ke window dialog dengan blur
 *    native OS. Blur hanya berlaku pada latar capsule; floating pill tidak di-blur.
 *  - Pill digambar di fase DRAW dan semua nilai animasinya dibaca dari lambda
 *    draw / graphicsLayer, jadi saat meluncur TIDAK ada recomposition.
 *  - TAHAN: pill membesar menjadi "lensa kaca" dengan efek jelly. Lebar dan tinggi
 *    dianimasikan dengan dua pegas berbeda (redaman & kekakuan berbeda) sehingga
 *    keduanya bergoyang tidak serempak saat ditekan maupun dilepas. Saat bergerak,
 *    pill memanjang searah gerak dan menipis (squash & stretch) sesuai kecepatan.
 *  - LENSA: ikon + label yang sedang dilewati/ditempati capsule berubah ke warna
 *    aksennya secara halus (berdasarkan jarak capsule ke tiap ikon), lalu kembali
 *    abu-abu setelah capsule menjauh — bukan hanya tab tujuan.
 *  - Spesifikasi pegas perpindahan tab (settle/follow) tidak diubah.
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
    val surface = palette.surface
    val isDark = palette.isDark
    val scope = rememberCoroutineScope()
    val densityForMeasure = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val navLabelStyle = MaterialTheme.typography.labelSmall
    // Ukuran indikator mengikuti panjang label tab, dengan padding yang cukup
    // dan batas agar indikator tidak menabrak tab tetangga pada layar sempit.
    val indicatorWidthsPx = remember(items, navLabelStyle, densityForMeasure, textMeasurer) {
        val horizontalPaddingPx = with(densityForMeasure) { 20.dp.toPx() }
        val minimumWidthPx = with(densityForMeasure) { 48.dp.toPx() }
        items.map { item ->
            (textMeasurer.measure(AnnotatedString(item.label), style = navLabelStyle).size.width + horizontalPaddingPx)
                .toFloat()
                .coerceAtLeast(minimumWidthPx)
        }
    }

    // Posisi pill dalam satuan "indeks slot" (0f..lastIndex). Dibaca HANYA di
    // lambda draw / graphicsLayer -> tidak memicu recomposition.
    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // Kecepatan drag dalam satuan slot/detik (dihaluskan, lalu meluruh saat jari diam).
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    // Saat dilepas, capsule mendarat pada tab terdekat dengan pegas singkat.
    // Selama jari masih menyentuh layar, posisinya mengikuti pointer secara langsung.
    val settleSpec = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 520f) }
    val followSpec = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 220f) }
    // Mencegah LaunchedEffect menjalankan animasi kedua setelah gesture sudah settle.
    var settledByTouch by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, count) {
        val target = selectedIndex.coerceIn(0, lastIndex)
        if (settledByTouch) {
            settledByTouch = false
            return@LaunchedEffect
        }
        if (!isDragging && abs(pillPosition.value - target) > 0.01f) {
            pillPosition.animateTo(target.toFloat(), followSpec)
        }
    }

    // Saat drag, event pointer berhenti ketika jari diam; tanpa peluruhan,
    // kecepatan lama akan "menempel" dan capsule tetap terlihat memanjang.
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (true) {
                withFrameNanos { }
                dragVelocity *= 0.90f
            }
        }
    }

    // Material pill memudar menjadi bening ketika disentuh/digeser, lalu kembali
    // ke abu-abu netral saat jari dilepas. Nilai Animatable dibaca pada fase draw
    // agar perubahan alpha tidak memicu recomposition setiap frame.
    val pillSolidness = remember { Animatable(1f) }
    LaunchedEffect(pressed, isDragging) {
        pillSolidness.animateTo(
            targetValue = if (pressed || isDragging) 0f else 1f,
            animationSpec = tween(durationMillis = 140),
        )
    }

    // Efek JELLY saat ditahan: lebar dan tinggi memakai pegas berbeda. Redaman rendah
    // membuat nilai melampaui target lalu berayun balik; perbedaan frekuensi antara
    // lebar & tinggi membuat bentuknya bergoyang seperti jeli, bukan sekadar membesar.
    val popW = remember { Animatable(0f) }
    val popH = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        val target = if (pressed) 1f else 0f
        launch {
            popW.animateTo(
                target,
                spring(
                    dampingRatio = if (pressed) 0.52f else 0.40f,
                    stiffness = if (pressed) 420f else 320f,
                ),
            )
        }
        launch {
            popH.animateTo(
                target,
                spring(
                    dampingRatio = if (pressed) 0.44f else 0.34f,
                    stiffness = if (pressed) 330f else 250f,
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
                    var lastX = downX
                    var dragging = false
                    var dragOriginX = downX
                    var dragOriginPosition = pillPosition.value

                    // Saat tap, capsule mulai meluncur menuju tab yang disentuh.
                    // Jika jari mulai digeser, animasi ini dibatalkan dan posisi
                    // capsule dilanjutkan langsung dari posisi visual terakhir.
                    var moveJob: Job? = scope.launch {
                        pillPosition.animateTo(touchTarget.toFloat(), followSpec)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val x = change.position.x
                        val travel = x - downX
                        val dx = x - lastX

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
                        }

                        if (dragging) {
                            dragPosition = (
                                dragOriginPosition + (x - dragOriginX) / slot
                            ).coerceIn(0f, lastIndex.toFloat())
                            // Kecepatan sebenarnya (slot/detik) memakai selisih waktu event,
                            // jadi hasilnya sama di layar 60 Hz maupun 120 Hz.
                            val dtMs = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            val instant = (dx / slot) / (dtMs / 1000f)
                            dragVelocity = dragVelocity * 0.65f + instant * 0.35f
                        }
                        lastX = x

                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        if (abs(travel) > viewConfiguration.touchSlop) change.consume()
                    }

                    val target = if (dragging) {
                        dragPosition.roundToInt().coerceIn(0, lastIndex)
                    } else {
                        touchTarget
                    }
                    // Jangan memanggil suspend API langsung dari AwaitPointerEventScope.
                    // Mutasi Animatable dijalankan pada coroutine scope biasa di bawah.
                    moveJob?.cancel()
                    settledByTouch = target != selectedIndex
                    pressed = false

                    if (dragging) {
                        val releasePosition = dragPosition
                        val releaseVelocity = dragVelocity.coerceIn(-12f, 12f)
                        scope.launch {
                            // Sinkronkan posisi terakhir saat drag sebelum mulai settle,
                            // sehingga capsule tidak snap saat jari dilepas. Kecepatan
                            // lepas jari dibawa ke pegas agar gerakannya terasa menyambung.
                            pillPosition.snapTo(releasePosition)
                            isDragging = false
                            dragVelocity = 0f
                            pillPosition.animateTo(
                                targetValue = target.toFloat(),
                                animationSpec = settleSpec,
                                initialVelocity = releaseVelocity,
                            )
                        }
                    } else {
                        isDragging = false
                        dragVelocity = 0f
                        scope.launch {
                            pillPosition.animateTo(target.toFloat(), settleSpec)
                        }
                    }
                    onSelect(target)
                }
            }

    // Lapisan kapsul: latar kaca, pill, dan item. [nat] = dirender di window native.
    val layers: @Composable (Boolean) -> Unit = { nat ->
        // Di window native, window selebar kapsul meng-clip pill -> sembulan dikecilkan
        // supaya tidak terpotong. Mode inline bebas menyembul melewati tepi capsule.
        val growWFraction = if (nat) 0.06f else 0.20f
        val growHeight = if (nat) 4.dp else 14.dp
        val stretchMax = 0.14f
        val squashMax = 0.09f

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
                                    backgroundColor = surface.copy(alpha = 0.06f + 0.20f * glassLevel)
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
                                // Tint PUTIH (bukan warna surface): di tema gelap surface nyaris
                                // sama dengan latar sehingga capsule terlihat bening.
                                if (blurOn) {
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.10f else 0.30f),
                                        Color.White.copy(alpha = if (isDark) 0.05f else 0.16f),
                                    )
                                } else {
                                    listOf(
                                        lerp(surface, Color.White, if (isDark) 0.04f else 0.0f).copy(alpha = 0.96f),
                                        surface.copy(alpha = 0.30f + 0.60f * glassLevel),
                                    )
                                },
                            ),
                        )
                        .border(width = 1.dp, brush = rimBrush, shape = barShape),
                )
            }
        }

        // ── Pill / lensa (draw phase, TIDAK di-blur) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val slot = size.width / count
                    val inset = 4.dp.toPx()
                    val pos = if (isDragging) dragPosition else pillPosition.value
                    val velocity = if (isDragging) dragVelocity else pillPosition.velocity
                    // popW/popH bisa >1 atau <0 sesaat karena overshoot pegas -> inilah jelly-nya.
                    val pw = popW.value
                    val ph = popH.value

                    // Lebar capsule berubah mengikuti label yang sedang dilewati.
                    // Interpolasi kontinu menjaga sliding halus tanpa resize mendadak.
                    val lowerIndex = pos.toInt().coerceIn(0, lastIndex)
                    val upperIndex = (lowerIndex + 1).coerceAtMost(lastIndex)
                    val fraction = (pos - lowerIndex).coerceIn(0f, 1f)
                    val lowerWidth = indicatorWidthsPx[lowerIndex]
                    val upperWidth = indicatorWidthsPx[upperIndex]
                    val labelWidth = lowerWidth + (upperWidth - lowerWidth) * fraction
                    val minIndicatorWidth = slot * 0.60f
                    val maxIndicatorWidth = slot * 1.06f
                    val baseW = labelWidth.coerceIn(minIndicatorWidth, maxIndicatorWidth)
                    val baseH = size.height - inset * 2f

                    // Squash & stretch: saat meluncur capsule memanjang dan menipis;
                    // volume visual dijaga dengan mengecilkan tinggi sedikit.
                    val speed = (abs(velocity) / 8f).coerceIn(0f, 1f)
                    val h = (baseH * (1f - squashMax * speed) + growHeight.toPx() * ph)
                        .coerceAtLeast(baseH * 0.7f)
                    val w = (baseW * (1f + growWFraction * pw) * (1f + stretchMax * speed))
                        .coerceAtLeast(h)
                    val cx = (pos + 0.5f) * slot
                    val left = cx - w / 2f
                    val top = (size.height - h) / 2f

                    // Neutral iOS-like gray; only icon tint uses the selected accent.
                    val solidness = pillSolidness.value.coerceIn(0f, 1f)
                    // lens = 1 saat ditahan/digeser (kaca bening), 0 saat diam (pill solid).
                    val lens = 1f - solidness
                    val pillGrayTop = if (isDark) Color(0xFF3F3F41) else Color(0xFFE8E8ED)
                    val pillGrayBottom = if (isDark) Color(0xFF303033) else Color(0xFFE0E0E5)
                    val corner = CornerRadius(h / 2f, h / 2f)

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
                    // Rim terang pada lensa: tepi atas bersinar, tepi bawah meredup.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = (if (isDark) 0.34f else 0.90f) * lens),
                                (if (isDark) Color.White else Color(0xFF8E8E93)).copy(
                                    alpha = (if (isDark) 0.12f else 0.30f) * lens,
                                ),
                            ),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
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

        // ── Item ─────────────────────────────────────────────────────────
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
                    pop = { popW.value },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
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
                    tint = surface.copy(alpha = 0.06f + 0.20f * glassLevel),
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
                val c = coverage().coerceIn(0f, 1f)
                // Ikon di bawah lensa sedikit "diperbesar", dan makin besar saat ditahan.
                val s = 1f + 0.12f * c + 0.06f * c * pop().coerceIn(0f, 1.3f)
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
