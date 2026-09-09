package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
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

/**
 * AetherX glass / liquid bottom navigation.
 *
 * Interaction model:
 * - One unified gesture state machine handles tap, hold, drag and release.
 * - The capsule follows the finger directly while sliding.
 * - Pressed/dragging state stays active until the finger is actually released.
 * - The capsule gets larger, wider and slightly tilted while moving.
 * - On release it relaxes into the nearest tab with a soft spring.
 * - Glass blur is provided by Haze; no sparkle/shimmer animation is used.
 */
data class AetherNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

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

    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, items.lastIndex).toFloat()) }
    var previewIndex by remember { mutableStateOf(selectedIndex.coerceIn(0, items.lastIndex)) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var dragStrength by remember { mutableFloatStateOf(0f) }

    val settleSpec = remember {
        spring<Float>(
            dampingRatio = 0.84f,
            stiffness = 125f,
        )
    }

    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
        previewIndex = target.roundToInt()
        if (!isPressed && !isDragging) {
            pillPosition.animateTo(target, settleSpec)
        }
    }

    val interaction = when {
        isDragging -> 1f
        isPressed -> 0.92f
        else -> 0f
    }

    val pillBulge by animateFloatAsState(
        targetValue = 1f + 0.105f * interaction,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 105f,
        ),
        label = "pillBulge",
    )
    val barBulge by animateFloatAsState(
        targetValue = 1f + 0.010f * interaction,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 105f,
        ),
        label = "barBulge",
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .onSizeChanged {
                barWidthPx = it.width.toFloat()
                barHeightPx = it.height.toFloat()
            }
            .systemGestureExclusion()
            .pointerInput(items.size, barWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                    if (slot <= 0f) return@awaitEachGesture

                    isPressed = true
                    isDragging = false
                    dragVelocity = 0f
                    dragStrength = 0.16f
                    scope.launch { pillPosition.stop() }

                    val touchTarget = ((down.position.x / slot) - 0.5f)
                        .coerceIn(0f, items.lastIndex.toFloat())
                    previewIndex = touchTarget.roundToInt().coerceIn(0, items.lastIndex)

                    var lastX = down.position.x
                    var totalTravel = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) break

                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        totalTravel += abs(dx)

                        if (!isDragging && abs(change.position.x - down.position.x) > with(density) { 4.dp.toPx() }) {
                            isDragging = true
                        }

                        if (isDragging) {
                            change.consume()
                            val deltaIndex = dx / slot
                            val next = (pillPosition.value + deltaIndex)
                                .coerceIn(0f, items.lastIndex.toFloat())

                            dragVelocity = deltaIndex
                            dragStrength = (dragStrength * 0.76f + abs(deltaIndex) * 0.90f)
                                .coerceIn(0.18f, 1f)

                            scope.launch { pillPosition.snapTo(next) }
                            previewIndex = next.roundToInt().coerceIn(0, items.lastIndex)
                        } else if (abs(change.position.x - down.position.x) < with(density) { 4.dp.toPx() }) {
                            // Keep the elastic pressed state alive while the finger is resting.
                            dragStrength = (dragStrength * 0.92f + 0.16f)
                                .coerceIn(0.16f, 0.34f)
                        }
                    }

                    val finalIndex = if (isDragging) {
                        previewIndex.coerceIn(0, items.lastIndex)
                    } else {
                        touchTarget.roundToInt().coerceIn(0, items.lastIndex)
                    }

                    isPressed = false
                    isDragging = false
                    dragVelocity = 0f
                    dragStrength = 0f

                    previewIndex = finalIndex
                    scope.launch {
                        pillPosition.animateTo(finalIndex.toFloat(), settleSpec)
                    }
                    if (finalIndex != selectedIndex) {
                        onSelect(finalIndex)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = if (isPressed) 20.dp else 16.dp,
                        shape = barShape,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.34f),
                    )
                    .clip(barShape)
                    .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.White.copy(alpha = 0.065f),
                                Color.Black.copy(alpha = 0.085f),
                            )
                        )
                    )
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val glassHighlight = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.045f),
                                Color.Transparent,
                            ),
                            start = androidx.compose.ui.geometry.Offset(w * 0.18f, 0f),
                            end = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.72f),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(glassHighlight)
                        }
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.16f),
                                outline.copy(alpha = 0.34f),
                            )
                        ),
                        shape = barShape,
                    )
            )

            if (slotWidthPx > 0f && barHeightPx > 0f) {
                val insetPx = with(density) { 3.dp.toPx() }
                val baseWidth = (slotWidthPx - insetPx * 2f).coerceAtLeast(1f)
                val baseHeight = (barHeightPx - insetPx * 2f).coerceAtLeast(1f)

                val speed = abs(dragVelocity).coerceIn(0f, 0.56f)
                val velocityFactor = (speed / 0.56f).coerceIn(0f, 1f)
                val tension = (dragStrength * 0.72f + velocityFactor * 0.65f)
                    .coerceIn(0f, 1f)

                // Organic jelly: sliding makes it wider and slightly shorter,
                // while the pressed state keeps the whole capsule inflated.
                val stretchX = 1f + 0.20f * tension
                val squashY = 1f - 0.055f * tension
                val extraHeight = 1f + 0.020f * interaction
                val pillWidthPx = baseWidth * (0.95f + 0.05f * pillBulge) * stretchX
                val pillHeightPx = baseHeight * pillBulge * squashY * extraHeight

                val centerX = (pillPosition.value + 0.5f) * slotWidthPx
                val offsetX = centerX - pillWidthPx / 2f
                val offsetY = (barHeightPx - pillHeightPx) / 2f

                val rotation = (dragVelocity * 8.0f).coerceIn(-7f, 7f)

                val pillWidthDp = with(density) { pillWidthPx.toDp() }
                val pillHeightDp = with(density) { pillHeightPx.toDp() }
                val offsetXDp = with(density) { offsetX.toDp() }
                val offsetYDp = with(density) { offsetY.toDp() }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { offsetXDp.toPx() }
                            translationY = with(density) { offsetYDp.toPx() }
                            rotationZ = rotation
                        }
                        .size(pillWidthDp, pillHeightDp)
                        .shadow(
                            elevation = if (isPressed) 11.dp else 4.dp,
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = primary.copy(alpha = if (isPressed) 0.19f else 0.10f),
                            spotColor = primary.copy(alpha = if (isPressed) 0.28f else 0.14f),
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.075f),
                                    primary.copy(alpha = 0.085f),
                                    Color.Black.copy(alpha = 0.085f),
                                )
                            )
                        )
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val topReflection = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.055f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = h * 0.60f,
                            )
                            val innerTint = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    primary.copy(alpha = 0.055f),
                                    Color.Transparent,
                                ),
                                center = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.34f),
                                radius = maxOf(w, h) * 0.85f,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(innerTint)
                                drawRect(topReflection)
                            }
                        }
                        .border(
                            width = if (isPressed) 1.45.dp else 1.05.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.72f),
                                    Color.White.copy(alpha = 0.30f),
                                    outline.copy(alpha = 0.48f),
                                )
                            ),
                            shape = RoundedCornerShape(percent = 50),
                        )
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
                    val active = if (isDragging || isPressed) {
                        index == previewIndex
                    } else {
                        index == selectedIndex
                    }
                    NavBarItem(
                        item = item,
                        selected = active,
                        emphasized = active && (isDragging || isPressed),
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
            dampingRatio = 0.90f,
            stiffness = 145f,
        ),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (emphasized) 1.16f else 1f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 135f,
        ),
        label = "navIconScale",
    )
    val labelScale by animateFloatAsState(
        targetValue = if (emphasized) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 135f,
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
                transformOrigin = TransformOrigin.Center
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
