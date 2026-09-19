package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class AetherNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

/*
 * AetherBottomNavBar — iOS 26/27 "Liquid Glass" style capsule tab bar.
 *
 * Perubahan utama dari versi sebelumnya:
 * 1. Kapsul kini benar-benar "glass": hampir transparan, dengan specular rim
 *    putih di tepi atas (ciri khas Liquid Glass iOS 26) dan inner shadow halus
 *    di bagian bawah.
 * 2. Gerakan dihaluskan: kapsul mengikuti jari 1:1 saat drag (snapTo) lalu
 *    settle dengan spring low-damping (0.75 / 240) — smooth, nyaris tanpa
 *    overshoot, persis behavior iOS.
 * 3. Deformasi velocity-based dibuat lebih halus (stretch maks 10%, squash 5%)
 *    supaya terasa "cair" bukan "melar".
 * 4. Warna gelap yang sebelumnya membuat kapsul terlihat "keruh" dikurangi
 *    drastis — glass iOS sangat bening.
 */
@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val barShape = RoundedCornerShape(percent = 50)
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val slotWidthPx = if (barWidthPx > 0f) barWidthPx / items.size else 0f

    val pillPosition = remember { Animatable(selectedIndex.toFloat()) }
    var previewIndex by remember { mutableStateOf(selectedIndex.coerceIn(0, items.lastIndex)) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    // Posisi drag 1:1 dengan jari — kunci kelancaran kapsul iOS.
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val isActive = isPressed || isDragging

    // Settle spring iOS-like: damping tinggi, stiffness sedang.
    // Result: kapsul "mendarat" mulus di slot tujuan tanpa mantulan berlebih.
    val settleSpec = remember {
        spring<Float>(
            dampingRatio = 0.75f,
            stiffness = 240f,
        )
    }

    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
        previewIndex = target.roundToInt()
        if (!isDragging) {
            pillPosition.animateTo(target, settleSpec)
        }
    }

    // Bulge saat disentuh: halus, hanya ~4% (iOS tidak "mengembang" besar).
    val pillBulge by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 320f,
        ),
        label = "pillBulge",
    )
    val barBulge by animateFloatAsState(
        targetValue = if (isActive) 1.015f else 1f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 280f,
        ),
        label = "barBulge",
    )

    // Stretch halus berbasis kecepatan — efek "liquid" yang sangat subtle.
    val currentVelocity = if (isDragging) dragVelocity else pillPosition.velocity
    val speed = abs(currentVelocity).coerceIn(0f, 8f)

    val targetStretchX = 1f + (speed * 0.025f).coerceAtMost(0.10f)
    val targetSquashY = 1f - (speed * 0.012f).coerceAtMost(0.05f)

    val stretchX by animateFloatAsState(
        targetValue = targetStretchX,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f,
        ),
        label = "stretchX",
    )
    val squashY by animateFloatAsState(
        targetValue = targetSquashY,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f,
        ),
        label = "squashY",
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 42.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(58.dp)
            .onSizeChanged {
                barWidthPx = it.width.toFloat()
                barHeightPx = it.height.toFloat()
            }
            .systemGestureExclusion()
            .pointerInput(items.size) {
                detectTapGestures(
                    onPress = { offset ->
                        val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                        isPressed = true
                        if (slot > 0f) {
                            val target = ((offset.x / slot) - 0.5f)
                                .coerceIn(0f, items.lastIndex.toFloat())
                            previewIndex = target.roundToInt()
                            scope.launch {
                                pillPosition.animateTo(target, settleSpec)
                            }
                        }
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (!released && !isDragging) {
                            previewIndex = selectedIndex.coerceIn(0, items.lastIndex)
                            scope.launch {
                                pillPosition.animateTo(previewIndex.toFloat(), settleSpec)
                            }
                        }
                    },
                    onTap = { offset ->
                        val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                        if (slot > 0f) {
                            val tapped = (((offset.x / slot) - 0.5f)
                                .coerceIn(0f, items.lastIndex.toFloat()))
                                .roundToInt()
                                .coerceIn(0, items.lastIndex)
                            previewIndex = tapped
                            onSelect(tapped)
                            scope.launch {
                                pillPosition.animateTo(tapped.toFloat(), settleSpec)
                            }
                        }
                    },
                )
            }
            .pointerInput(items.size) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        isPressed = true
                        dragVelocity = 0f
                        val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                        if (slot > 0f) {
                            val start = ((offset.x / slot) - 0.5f)
                                .coerceIn(0f, items.lastIndex.toFloat())

                            dragPosition = start
                            previewIndex = start.roundToInt()

                            scope.launch { pillPosition.stop() }
                            scope.launch { pillPosition.snapTo(start) }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        isPressed = false
                        val finalIndex = previewIndex.coerceIn(0, items.lastIndex)
                        scope.launch {
                            pillPosition.animateTo(
                                targetValue = finalIndex.toFloat(),
                                initialVelocity = dragVelocity,
                                animationSpec = settleSpec,
                            )
                            dragVelocity = 0f
                        }
                        onSelect(finalIndex)
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressed = false
                        previewIndex = selectedIndex.coerceIn(0, items.lastIndex)
                        scope.launch {
                            pillPosition.animateTo(
                                targetValue = previewIndex.toFloat(),
                                initialVelocity = dragVelocity,
                                animationSpec = settleSpec,
                            )
                            dragVelocity = 0f
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                    if (slot > 0f) {
                        val deltaIndex = dragAmount / slot
                        dragVelocity = deltaIndex * 60f

                        // Kapsul mengikuti jari 1:1 — inti smoothness iOS.
                        dragPosition = (dragPosition + deltaIndex)
                            .coerceIn(0f, items.lastIndex.toFloat())

                        scope.launch { pillPosition.snapTo(dragPosition) }
                        previewIndex = dragPosition.roundToInt().coerceIn(0, items.lastIndex)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = barBulge
                    scaleY = barBulge
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            // ── Bar latar: glass tipis, hampir bening ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 14.dp,
                        shape = barShape,
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.22f),
                    )
                    .clip(barShape)
                    .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Black.copy(alpha = 0.02f),
                            ),
                        ),
                    )
                    .drawWithCache {
                        val h = size.height
                        // Specular tipis di tepi atas bar.
                        val topGlow = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h * 0.45f,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(topGlow)
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                outline.copy(alpha = 0.14f),
                            ),
                        ),
                        shape = barShape,
                    ),
            )

            if (slotWidthPx > 0f && barHeightPx > 0f) {
                val insetPx = with(density) { 3.dp.toPx() }
                val baseWidth = (slotWidthPx - insetPx * 2f).coerceAtLeast(1f)
                val baseHeight = (barHeightPx - insetPx * 2f).coerceAtLeast(1f)

                val pillWidthPx = baseWidth * pillBulge * stretchX
                val pillHeightPx = baseHeight * pillBulge * squashY

                val centerX = (pillPosition.value + 0.5f) * slotWidthPx
                val offsetX = centerX - pillWidthPx / 2f
                val offsetY = (barHeightPx - pillHeightPx) / 2f

                val pillWidthDp = with(density) { pillWidthPx.toDp() }
                val pillHeightDp = with(density) { pillHeightPx.toDp() }
                val offsetXDp = with(density) { offsetX.toDp() }
                val offsetYDp = with(density) { offsetY.toDp() }

                // ── Kapsul seleksi: Liquid Glass iOS 26/27 ─────────────────
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { offsetXDp.toPx() }
                            translationY = with(density) { offsetYDp.toPx() }
                        }
                        .size(pillWidthDp, pillHeightDp)
                        .shadow(
                            elevation = if (isActive) 6.dp else 2.dp,
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = Color.Black.copy(alpha = 0.10f),
                            spotColor = Color.Black.copy(alpha = 0.14f),
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        // Blur reguler → frosting terlihat jelas tapi tetap bening.
                        .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        // Isi kapsul: bening. Hanya kilau putih sangat halus
                        // + tint primer tipis di bagian bawah (seperti iOS).
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.05f),
                                    Color.White.copy(alpha = 0.03f),
                                    primary.copy(alpha = 0.04f),
                                ),
                            ),
                        )
                        .drawWithCache {
                            val w = size.width
                            val h = size.height

                            // 1) Specular rim atas — highlight putih terang
                            //    yang memudar di ~35% tinggi. Ciri Liquid Glass.
                            val topSpecular = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = h * 0.35f,
                            )
                            // 2) Inner shadow bawah — memberi kesan "cembung".
                            val innerBottom = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.06f),
                                ),
                                startY = h * 0.70f,
                                endY = h,
                            )
                            // 3) Pantulan samping sangat lemah (volume kaca).
                            val sideSheen = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.08f),
                                ),
                                startX = 0f,
                                endX = w,
                            )

                            onDrawWithContent {
                                drawContent()
                                drawRect(topSpecular)
                                drawRect(innerBottom)
                                drawRect(sideSheen)
                            }
                        }
                        // Border kapsul: specular putih di atas → transparan
                        // di tengah → tipis gelap di bawah (mengikuti kontur cahaya).
                        .border(
                            width = if (isActive) 1.15.dp else 0.9.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.10f),
                                    outline.copy(alpha = 0.22f),
                                ),
                            ),
                            shape = RoundedCornerShape(percent = 50),
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val active = if (isActive) {
                        index == previewIndex
                    } else {
                        index == selectedIndex
                    }
                    NavBarItem(
                        item = item,
                        selected = active,
                        emphasized = active && isActive,
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
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 220f,
        ),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (emphasized) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = 280f,
        ),
        label = "navIconScale",
    )
    val labelScale by animateFloatAsState(
        targetValue = if (emphasized) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = 280f,
        ),
        label = "navLabelScale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp, start = 2.dp, end = 2.dp)
                .graphicsLayer {
                    scaleX = labelScale
                    scaleY = labelScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        )
    }
}