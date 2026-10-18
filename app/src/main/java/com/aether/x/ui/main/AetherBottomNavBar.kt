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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.blurRadiusFor
import com.aether.x.ui.theme.LocalAetherPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
    /** Warna aksen tab ini — dipakai untuk pill dan ikon aktif. */
    val accent: Color,
)

/**
 * Tab bar capsule v3.6 — kaca.
 *
 *  - v3.8: latar capsule mem-BLUR konten tab yang lewat di belakangnya
 *    (Haze, radius dari slider blur) + tint tipis + garis tepi berkilau.
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
    /** Sumber blur = konten tab di belakang navbar (SIBLING navbar, bukan induknya). */
    hazeState: HazeState? = null,
) {
    if (items.isEmpty()) return

    val count = items.size
    val lastIndex = count - 1
    val barShape = RoundedCornerShape(percent = 50)
    val palette = LocalAetherPalette.current
    val blur = LocalAetherStyle.current.blur
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

    val blurOn = hazeState != null && blur > 0.01f
    val barBlurRadius = blurRadiusFor(blur).dp
    // Blur nyata: tint cukup tipis supaya blur-nya kelihatan tapi ikon tetap terbaca.
    val barAlpha = if (blurOn) (0.80f - 0.22f * blur).coerceIn(0.55f, 0.9f) else 0.96f

    // Lapisan luar tetap selebar layar agar navbar terletak di tengah pada tablet.
    // Kapsulnya sendiri dibatasi supaya tidak meregang memenuhi seluruh lebar tablet.
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
            .padding(horizontal = Spacing.xxl + Spacing.sm)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .height(60.dp)
            // Tetap izinkan tab terluar menerima sentuhan di dekat tepi layar.
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
                    isDragging = true
                    dragVelocity = 0f
                    var lastX = down.position.x
                    dragPosition = slotAt(down.position.x)
                    previewIndex = dragPosition.roundToInt().coerceIn(0, lastIndex)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val x = change.position.x
                        val dx = x - lastX

                        // Perbarui posisi bahkan sebelum jari diangkat; tidak menunggu touch-slop.
                        dragPosition = slotAt(x)
                        previewIndex = dragPosition.roundToInt().coerceIn(0, lastIndex)
                        dragVelocity = (dx / slot) * 60f
                        lastX = x

                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        if (abs(dx) > 0.5f) change.consume()
                    }

                    val target = dragPosition.roundToInt().coerceIn(0, lastIndex)
                    previewIndex = target
                    val startFrom = dragPosition
                    pressed = false
                    // Tandai sebelum onSelect agar efek perubahan selectedIndex tidak
                    // memulai animasi kedua yang membuat capsule terasa terlambat.
                    settledByTouch = target != selectedIndex
                    scope.launch {
                        pillPosition.snapTo(startFrom)
                        isDragging = false
                        dragVelocity = 0f
                        pillPosition.animateTo(target.toFloat(), settleSpec)
                    }
                    onSelect(target)
                }
            },
      ) {
        // ── Latar kaca capsule (di-clip) ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 10.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                )
                .clip(barShape)
                // Tint is behind the blur effect; a later background modifier
                // would hide the captured image and look merely transparent.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(surface, Color.White, if (isDark) 0.06f else 0.0f).copy(alpha = barAlpha),
                            surface.copy(alpha = (barAlpha - 0.06f).coerceAtLeast(0.55f)),
                        ),
                    ),
                )
                .then(
                    if (blurOn) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurRadius = barBlurRadius
                            noiseFactor = 0.03f
                            backgroundColor = surface.copy(alpha = 0.18f)
                        }
                    } else {
                        Modifier
                    },
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

        // ── Pill (digambar di draw phase, TIDAK di-clip agar bisa menyembul) ─
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
                    val w = baseW * stretch * (1f + 0.14f * p)
                    val h = baseH * squash + 14.dp.toPx() * p
                    val cx = (pos + 0.5f) * slot
                    val left = cx - w / 2f
                    val top = (size.height - h) / 2f - 2.dp.toPx() * p

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
