package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.aether.x.ui.components.rememberCrossWindowBlurEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk pill dan ikon aktif. */
    val accent: Color,
)

/** true = coba window blur native OS (dialog); default menggunakan blur Haze inline. */
private const val USE_NATIVE_NAV_BLUR = false

/**
 * Tab bar capsule v3.11 — Liquid Glass.
 *
 *  - Jalur utama memakai Haze untuk memburamkan konten aktual di belakang navbar.
 *    Tint tetap tipis supaya warna elemen dan aurora di bawahnya masih terlihat.
 *  - AuroraBlurLayer hanya menjadi fallback ketika HazeState tidak tersedia.
 *    Radius dibatasi agar biaya blur tetap wajar pada perangkat kelas bawah.
 *  - Opsional: USE_NATIVE_NAV_BLUR memindahkan navbar ke window dialog dengan blur
 *    native OS. Blur hanya berlaku pada latar capsule; floating pill tidak di-blur.
 *  - Pill digambar di fase DRAW dan posisinya dibaca dari `Animatable` di
 *    dalam lambda draw, jadi saat meluncur TIDAK ada recomposition.
 *  - BARU: saat capsule DITAHAN, pill "menyembul" — membesar melewati tepi
 *    capsule dengan spring yang sengaja agak mantul (pegas), lalu kembali
 *    tenang saat dilepas. Pill ada di layer yang TIDAK di-clip supaya
 *    sembulannya terlihat.
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
    val outline = palette.stroke
    val isDark = palette.isDark
    val scope = rememberCoroutineScope()

    // Posisi pill dalam satuan "indeks slot" (0f..lastIndex). Dibaca HANYA di
    // lambda draw -> tidak memicu recomposition.
    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, lastIndex)) }
    var pressed by remember { mutableStateOf(false) }

    // Saat dilepas, capsule mendarat pada tab terdekat dengan pegas singkat.
    // Selama jari masih menyentuh layar, posisinya mengikuti pointer secara langsung.
    val settleSpec = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 520f) }
    val followSpec = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 220f) }
    // Mencegah LaunchedEffect menjalankan animasi kedua setelah gesture sudah settle.
    var settledByTouch by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, count) {
        val target = selectedIndex.coerceIn(0, lastIndex)
        previewIndex = target
        if (settledByTouch) {
            settledByTouch = false
            return@LaunchedEffect
        }
        if (!isDragging && abs(pillPosition.value - target) > 0.01f) {
            pillPosition.animateTo(target.toFloat(), followSpec)
        }
    }

    // 0f = diam, 1f = menyembul penuh. Spring mantul (damping rendah) saat
    // ditekan -> efek pegas; saat dilepas kembali dengan damping lebih tinggi.
    val pop = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            pop.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 360f))
        } else {
            pop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 300f))
        }
    }

    val blurOn = blur > 0.01f && (hazeState != null || LocalAuroraBackdrop.current != null)
    val barBlurRadius = blurRadiusFor(blur).coerceAtMost(32f).dp
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
                    previewIndex = touchTarget

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
                            previewIndex = dragPosition.roundToInt().coerceIn(0, lastIndex)
                            dragVelocity = (dx / slot) * 60f
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
                    previewIndex = target
                    pressed = false

                    if (dragging) {
                        val releasePosition = dragPosition
                        scope.launch {
                            // Sinkronkan posisi terakhir saat drag sebelum mulai settle,
                            // sehingga capsule tidak snap saat jari dilepas.
                            pillPosition.snapTo(releasePosition)
                            isDragging = false
                            dragVelocity = 0f
                            pillPosition.animateTo(target.toFloat(), settleSpec)
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
        // supaya tidak terpotong.
        val growH = if (nat) 5.dp else 14.dp
        val growW = if (nat) 0.06f else 0.14f
        val lift = if (nat) 0.dp else 2.dp

        // ── Latar kaca capsule ───────────────────────────────────────────
        if (nat) {
            // Blur + tint dikerjakan window (lihat NativeBlurWindow); di sini cukup garis tepi.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.16f else 0.95f),
                                outline.copy(alpha = 0.55f),
                            ),
                        ),
                        shape = barShape,
                    ),
            )
        } else {
            // Fallback saja: bila HazeState tidak tersedia, buramkan salinan aurora.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 10.dp,
                        shape = barShape,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                    )
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
                                    noiseFactor = 0.03f
                                    backgroundColor = surface.copy(alpha = 0.04f + 0.18f * glassLevel)
                                },
                        )
                    } else {
                        AuroraBlurLayer(
                            blurRadius = (barBlurRadius.value * 1.15f).dp,
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
                                        Color.White.copy(alpha = if (isDark) 0.08f else 0.24f),
                                        Color.White.copy(alpha = if (isDark) 0.03f else 0.10f),
                                    )
                                } else {
                                    listOf(
                                        lerp(surface, Color.White, if (isDark) 0.04f else 0.0f).copy(alpha = 0.96f),
                                        surface.copy(alpha = 0.30f + 0.60f * glassLevel),
                                    )
                                },
                            ),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.16f else 0.95f),
                                    outline.copy(alpha = 0.55f),
                                ),
                            ),
                            shape = barShape,
                        ),
                )
            }
        }

        // ── Pill (draw phase, TIDAK di-blur) ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val slot = size.width / count
                    val inset = 4.dp.toPx()
                    val pos = if (isDragging) dragPosition else pillPosition.value
                    val velocity = if (isDragging) dragVelocity else pillPosition.velocity
                    val p = pop.value // bisa >1 / <0 sesaat karena overshoot spring

                    // Stretch searah gerak, dibatasi supaya tetap kapsul rapi.
                    val stretch = 1f + (abs(velocity) * 0.07f).coerceAtMost(0.22f)
                    val squash = 1f - (abs(velocity) * 0.025f).coerceAtMost(0.07f)

                    val baseW = slot - inset * 2f
                    val baseH = size.height - inset * 2f
                    // Menyembul: melebar ~14% dan memanjang ~14dp (melewati tepi capsule).
                    val w = baseW * stretch * (1f + growW * p)
                    val h = baseH * squash + growH.toPx() * p
                    val cx = (pos + 0.5f) * slot
                    val left = cx - w / 2f
                    val top = (size.height - h) / 2f - lift.toPx() * p

                    // Warna melebur antara aksen dua tab terdekat.
                    val lo = floor(pos).toInt().coerceIn(0, lastIndex)
                    val hi = (lo + 1).coerceAtMost(lastIndex)
                    val t = (pos - lo).coerceIn(0f, 1f)
                    val accent = lerp(items[lo].accent, items[hi].accent, t)
                    val glowBoost = p.coerceIn(0f, 1.3f)

                    val corner = CornerRadius(h / 2f, h / 2f)

                    // Glow lembut di belakang pill (menguat saat menyembul).
                    drawRoundRect(
                        color = accent.copy(alpha = 0.14f + 0.12f * glowBoost),
                        topLeft = Offset(left - 4.dp.toPx(), top - 3.dp.toPx()),
                        size = Size(w + 8.dp.toPx(), h + 6.dp.toPx()),
                        cornerRadius = CornerRadius((h + 6.dp.toPx()) / 2f),
                    )
                    // Badan pill: gradient aksen transparan.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.30f + 0.08f * glowBoost),
                                accent.copy(alpha = 0.14f + 0.06f * glowBoost),
                            ),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                    )
                    // Garis tepi aksen + highlight kaca di sisi atas.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.50f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                            startY = top,
                            endY = top + h * 0.55f,
                        ),
                        topLeft = Offset(left + 2.dp.toPx(), top + 1.dp.toPx()),
                        size = Size(w - 4.dp.toPx(), h * 0.55f),
                        cornerRadius = CornerRadius(h / 2f),
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
                val active = if (isDragging || pressed) index == previewIndex else index == selectedIndex
                NavBarItem(
                    item = item,
                    selected = active,
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
                    blurRadiusPx = with(density) { (blurRadiusFor(blur) * 1.15f).coerceAtMost(32f).dp.roundToPx() },
                    tint = surface.copy(alpha = 0.04f + 0.18f * glassLevel),
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


@Composable
private fun NavBarItem(
    item: AetherNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tint by animateColorAsState(
        targetValue = if (selected) item.accent else muted,
        animationSpec = tween(durationMillis = 180),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "navIconScale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            },
        )
        Text(
            text = item.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, start = 2.dp, end = 2.dp),
        )
    }
}
