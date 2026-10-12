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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.components.LocalAetherHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.aether.x.ui.theme.LocalGlassIntensity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk pill, glow, dan ikon aktif. */
    val accent: Color,
)

/**
 * Tab bar capsule v3.6.
 *
 * Kenapa ditulis ulang (lebih ringan dari versi sebelumnya):
 *  - Backdrop blur menggunakan Haze dengan sampling terkontrol agar konten
 *    di bawah kapsul benar-benar ditangkap dan diburamkan, bukan diblur dekorasinya.
 *  - Pill digambar di fase DRAW (`drawBehind`) dan posisinya dibaca dari
 *    `Animatable` di dalam lambda draw, jadi saat pill meluncur TIDAK ada
 *    recomposition sama sekali (versi lama mengubah ukuran/offset Box di
 *    komposisi tiap frame -> layout ulang tiap frame).
 *  - Satu pointerInput saja (tap + drag digabung), tanpa coroutine per
 *    event drag.
 *
 * Animasi: pill meluncur dengan spring yang tenang (nyaris tanpa
 * overshoot), memanjang sedikit searah kecepatan ("liquid stretch") lalu
 * kembali ke bentuk kapsul, warnanya meleburkan aksen tab asal -> tujuan.
 */
@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val count = items.size
    val lastIndex = count - 1
    val barShape = RoundedCornerShape(percent = 50)
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val glassIntensity = LocalGlassIntensity.current
    val scope = rememberCoroutineScope()

    // Posisi pill dalam satuan "indeks slot" (0f..lastIndex). Dibaca HANYA di
    // lambda draw -> tidak memicu recomposition.
    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, lastIndex).toFloat()) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, lastIndex)) }
    var pressed by remember { mutableStateOf(false) }

    val settleSpec = remember { spring<Float>(dampingRatio = 0.82f, stiffness = 420f) }

    LaunchedEffect(selectedIndex, count) {
        val target = selectedIndex.coerceIn(0, lastIndex)
        previewIndex = target
        if (!isDragging) pillPosition.animateTo(target.toFloat(), settleSpec)
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.085f else 1f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 300f),
        label = "pillPress",
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.md)
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 14.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(barShape)
            // zIndex 1 lets the capsule sample the tab content source at zIndex 0.
            .hazeSource(LocalAetherHazeState.current, zIndex = 1f)
            .hazeEffect(state = LocalAetherHazeState.current) {
                // Slider Appearance controls this actual captured-background blur.
                blurRadius = (glassIntensity * 30f).dp
                backgroundColor = surface.copy(alpha = 0.24f + (1f - glassIntensity) * 0.20f)
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(surface, Color.White, 0.04f).copy(alpha = 0.12f),
                        surface.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), outline.copy(alpha = 0.35f)),
                ),
                shape = barShape,
            )
            // Tanpa ini, zona gestur back/predictive-back di tepi kiri layar
            // menelan tap pada tab paling kiri (Dashboard).
            .systemGestureExclusion()
            .pointerInput(count) {
                awaitEachGesture {
                    val slot = size.width.toFloat() / count
                    if (slot <= 0f) return@awaitEachGesture

                    fun slotAt(x: Float) = ((x / slot) - 0.5f).coerceIn(0f, lastIndex.toFloat())

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true

                    var moved = false
                    var lastX = down.position.x
                    var finalX = down.position.x

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        finalX = change.position.x
                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        val dx = change.position.x - lastX
                        if (!moved && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            moved = true
                            isDragging = true
                            dragPosition = pillPosition.value
                        }
                        if (moved) {
                            change.consume()
                            dragVelocity = (dx / slot) * 60f
                            dragPosition = slotAt(change.position.x)
                            previewIndex = dragPosition.roundToInt().coerceIn(0, lastIndex)
                        }
                        lastX = change.position.x
                    }

                    pressed = false
                    val target = (if (moved) dragPosition else slotAt(finalX))
                        .roundToInt().coerceIn(0, lastIndex)
                    previewIndex = target
                    val startFrom = dragPosition
                    val wasDragging = moved
                    scope.launch {
                        if (wasDragging) pillPosition.snapTo(startFrom)
                        isDragging = false
                        dragVelocity = 0f
                        pillPosition.animateTo(target.toFloat(), settleSpec)
                    }
                    onSelect(target)
                }
            },
    ) {
        // ── Pill (digambar di draw phase) ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val slot = size.width / count
                    val inset = 4.dp.toPx()
                    val pos = if (isDragging) dragPosition else pillPosition.value
                    val velocity = if (isDragging) dragVelocity else pillPosition.velocity

                    // Stretch searah gerak, dibatasi supaya tetap kapsul rapi.
                    val stretch = 1f + (abs(velocity) * 0.07f).coerceAtMost(0.22f)
                    val squash = 1f - (abs(velocity) * 0.025f).coerceAtMost(0.07f)

                    val baseW = slot - inset * 2f
                    val baseH = size.height - inset * 2f
                    val w = baseW * stretch * pressScale
                    val h = baseH * squash * pressScale
                    val cx = (pos + 0.5f) * slot
                    val left = cx - w / 2f
                    val top = (size.height - h) / 2f

                    // Warna melebur antara aksen dua tab terdekat.
                    val lo = floor(pos).toInt().coerceIn(0, lastIndex)
                    val hi = (lo + 1).coerceAtMost(lastIndex)
                    val t = (pos - lo).coerceIn(0f, 1f)
                    val accent = lerp(items[lo].accent, items[hi].accent, t)

                    val corner = CornerRadius(h / 2f, h / 2f)

                    // Glow lembut di belakang pill.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.08f + glassIntensity * 0.20f),
                        topLeft = Offset(left - 4.dp.toPx(), top - 3.dp.toPx()),
                        size = Size(w + 8.dp.toPx(), h + 6.dp.toPx()),
                        cornerRadius = CornerRadius((h + 6.dp.toPx()) / 2f),
                    )
                    // Badan pill: gradient aksen transparan.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accent.copy(alpha = 0.18f + glassIntensity * 0.28f), accent.copy(alpha = 0.08f + glassIntensity * 0.16f)),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                    )
                    // Highlight tipis di tepi atas + garis tepi aksen.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.55f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            startY = top,
                            endY = top + h * 0.55f,
                        ),
                        topLeft = Offset(left + 2.dp.toPx(), top + 1.dp.toPx()),
                        size = Size(w - 4.dp.toPx(), h * 0.55f),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                },
        )

        // ── Item (recompose hanya saat indeks aktif berubah) ─────────────
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
