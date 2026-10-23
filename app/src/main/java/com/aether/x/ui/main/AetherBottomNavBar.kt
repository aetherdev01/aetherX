package com.aether.x.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.exp
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Fase bentuk capsule: lihat penjelasan di blok popW/popH. */
private enum class LensPhase { Rest, Hold, Travel }

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk ikon + label saat dilewati/ditempati capsule. */
    val accent: Color,
)

/** true = coba window blur native OS (dialog); default menggunakan blur Haze inline. */
private const val USE_NATIVE_NAV_BLUR = false

/**
 * Tab bar capsule v3.13 — Liquid Glass dengan motion iOS-inspired.
 *
 *  - Jalur utama memakai Haze untuk memburamkan konten aktual di belakang navbar.
 *    Gaya iOS 27: tanpa drop shadow, border lebih tegas, kaca sedikit lebih solid.
 *  - AuroraBlurLayer hanya menjadi fallback ketika HazeState tidak tersedia.
 *  - Opsional: USE_NATIVE_NAV_BLUR memindahkan navbar ke window dialog dengan blur
 *    native OS. Blur hanya berlaku pada latar capsule; floating pill tidak di-blur.
 *  - Pill digambar di fase DRAW dan semua nilai animasinya dibaca dari lambda
 *    draw / graphicsLayer, jadi saat meluncur TIDAK ada recomposition.
 *  - UKURAN: lebar pill tetap (satu slot), tidak mengikuti panjang label tab.
 *  - TAHAN: saat jari diam di tab (termasuk berhenti di tab tujuan sebelum dilepas),
 *    pill membesar menjadi "lensa kaca" dengan efek jelly. Lebar dan tinggi
 *    dianimasikan dengan dua pegas berbeda sehingga bergoyang tidak serempak.
 *  - GESER: saat digeser pill mengecil dan memanjang searah gerak (sisi depan lebih
 *    maju, tinggi menipis) sesuai kecepatan; berhenti -> membesar lagi; dilepas ->
 *    mengecil dengan jelly seperti sebelumnya.
 *  - LENSA: ikon + label yang sedang dilewati/ditempati capsule berubah ke warna
 *    aksennya secara halus (berdasarkan jarak capsule ke tiap ikon), lalu kembali
 *    abu-abu setelah capsule menjauh — bukan hanya tab tujuan.
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
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // Kecepatan drag dalam satuan slot/detik (dihaluskan, lalu meluruh saat jari diam).
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    // Saat dilepas, capsule mendarat pada tab terdekat dengan pegas bergaya iOS — lebih
    // santai, tidak buru-buru, dengan overshoot lembut.
    // travelSpec dipakai untuk SATU perjalanan utuh (tekan -> meluncur -> mendarat) agar
    // tidak ada "sambungan"/mengekor yang terlihat saat jari dilepas di tengah jalan.
    // releaseSpec khusus untuk mendaratkan capsule setelah geser cepat (fling), membawa
    // kecepatan lepas jari supaya gerakannya terasa menyambung.
    val travelSpec = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 300f) }
    val releaseSpec = remember { spring<Float>(dampingRatio = 0.82f, stiffness = 420f) }
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

    // Saat drag, event pointer berhenti ketika jari diam; tanpa peluruhan,
    // kecepatan lama akan "menempel" dan capsule tetap terlihat memanjang.
    // Peluruhan berbasis waktu (bukan per frame) agar sama di 60 Hz dan 120 Hz.
    LaunchedEffect(isDragging) {
        if (isDragging) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                dragVelocity *= exp(-dt / 0.06f)
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
    //
    // Tiga fase (gaya iOS):
    //  - Rest   : jari tidak menyentuh -> capsule kecil (versi saat dilepas, jelly mengecil).
    //  - Hold   : jari menyentuh dan diam (termasuk berhenti di tab tujuan sebelum
    //             dilepas) -> capsule membesar menjadi lensa.
    //  - Travel : jari sedang bergeser cukup cepat -> capsule mengecil dan memanjang
    //             searah geser (stretch digambar di fase draw berdasarkan kecepatan).
    val popW = remember { Animatable(0f) }
    val popH = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var travelling = false
        snapshotFlow {
            val isPressed = pressed
            val dragging = isDragging
            // dragVelocity sendiri State yang meluruh tiap frame selagi drag (lihat efek
            // peluruhan di atas), jadi cukup dibaca di sini agar flow ini otomatis
            // terpicu ulang tiap frame selama jari digeser.
            val speed = if (dragging) abs(dragVelocity) else 0f
            Triple(isPressed, dragging, speed)
        }
            .map { (isPressed, dragging, speed) ->
                if (!isPressed) {
                    travelling = false
                    LensPhase.Rest
                } else if (!dragging) {
                    // Tap-and-hold (termasuk saat capsule masih meluncur menuju tab yang
                    // disentuh): lensa langsung membesar begitu jari ditekan, bukan
                    // menunggu capsule sampai dulu baru mulai animasi pegasnya.
                    travelling = false
                    LensPhase.Hold
                } else {
                    // Histeresis: mulai "bergeser" di atas 1.2 slot/dtk, kembali "diam"
                    // di bawah 0.45 slot/dtk, supaya tidak berkedip saat jari bergetar.
                    travelling = if (travelling) speed > 0.45f else speed > 1.2f
                    if (travelling) LensPhase.Travel else LensPhase.Hold
                }
            }
            .distinctUntilChanged()
            .collectLatest { phase ->
                val target: Float
                val specW: SpringSpec<Float>
                val specH: SpringSpec<Float>
                when (phase) {
                    LensPhase.Hold -> {
                        target = 1f
                        specW = spring(dampingRatio = 0.62f, stiffness = 420f)
                        specH = spring(dampingRatio = 0.56f, stiffness = 350f)
                    }
                    LensPhase.Travel -> {
                        target = 0.15f
                        specW = spring(dampingRatio = 0.78f, stiffness = 480f)
                        specH = spring(dampingRatio = 0.70f, stiffness = 400f)
                    }
                    LensPhase.Rest -> {
                        target = 0f
                        specW = spring(dampingRatio = 0.60f, stiffness = 340f)
                        specH = spring(dampingRatio = 0.52f, stiffness = 280f)
                    }
                }
                coroutineScope {
                    launch { popW.animateTo(target, specW) }
                    launch { popH.animateTo(target, specH) }
                }
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
                        }

                        if (dragging) {
                            val previousPosition = dragPosition
                            dragPosition = (
                                dragOriginPosition + (x - dragOriginX) / slot
                            ).coerceIn(0f, lastIndex.toFloat())
                            // Kecepatan sebenarnya (slot/detik) dari pergeseran capsule dan
                            // selisih waktu event -> sama di 60/120 Hz, dan nol saat capsule
                            // tertahan di tepi (jari masih bergerak tapi capsule tidak).
                            val dtMs = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            val instant = (dragPosition - previousPosition) / (dtMs / 1000f)
                            dragVelocity = dragVelocity * 0.65f + instant * 0.35f
                        }

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
        val stretchMax = 0.22f
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

                    // Lebar dasar capsule TETAP (satu slot dikurangi sedikit celah), tidak
                    // lagi mengikuti panjang label tab.
                    val baseW = slot - 8.dp.toPx()
                    val baseH = size.height - inset * 2f

                    // Squash & stretch searah geser: saat meluncur capsule memanjang ke arah
                    // gerak (sisi depan lebih maju dari sisi belakang) dan menipis; volume
                    // visual dijaga dengan mengecilkan tinggi sedikit.
                    val speed = (abs(velocity) / 8f).coerceIn(0f, 1f)
                    val direction = if (velocity >= 0f) 1f else -1f
                    val h = (baseH * (1f - squashMax * speed) + growBy.toPx() * ph)
                        .coerceAtLeast(baseH * 0.7f)
                    val stretchPx = baseW * stretchMax * speed
                    val w = (baseW + growBy.toPx() * pw + stretchPx)
                        .coerceAtLeast(h)
                    // Geser pusat sedikit ke arah gerak: sisi depan memanjang ~70%, belakang ~30%.
                    val cx = (pos + 0.5f) * slot + direction * stretchPx * 0.2f
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
