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

/**
 * iOS-style liquid capsule bottom navigation.
 *
 * Interaction model:
 * - Tap: capsule softly springs to the tapped tab.
 * - Drag: capsule follows the finger directly (no coroutine pile-up).
 * - Release: capsule settles to the nearest tab with a relaxed spring.
 * - While dragging/pressing: capsule stays lifted and grows continuously,
 *   with velocity-driven jelly stretch/squash and a tiny directional tilt.
 * - No sparkle/sweep animation. The glass treatment is static/subtle.
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

    val pillPosition = remember { Animatable(selectedIndex.toFloat()) }
    var previewIndex by remember { mutableStateOf(selectedIndex.coerceIn(0, items.lastIndex)) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    // Relaxed, controlled spring. The capsule should feel weighty rather than
    // snapping aggressively after the finger is released.
    val settleSpec = remember {
        spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 155f,
        )
    }

    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
        previewIndex = target.roundToInt()
        if (!isDragging) {
            pillPosition.animateTo(target, settleSpec)
        }
    }

    // Jelly is driven by the gesture itself, not only by "pressed".
    // During a drag the capsule stretches continuously and remains lifted
    // until the finger is released. This makes the motion feel elastic.
    val interactionEnergy by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "interactionEnergy",
    )

    val pillBulge by animateFloatAsState(
        targetValue = 1f + 0.125f * interactionEnergy,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 310f,
        ),
        label = "pillBulge",
    )

    val barBulge by animateFloatAsState(
        targetValue = 1f + 0.014f * interactionEnergy,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 250f,
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
            // Tap detector is deliberately independent from the drag detector,
            // while both use the same stable items.size key. Bar width is read
            // live so a re-layout cannot invalidate the gesture state.
            .pointerInput(items.size) {
                detectTapGestures(
                    onPress = { offset ->
                        val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                        isPressed = true
                        if (slot > 0f) {
                            val target = ((offset.x / slot) - 0.5f)
                                .coerceIn(0f, (items.lastIndex).toFloat())
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
                            previewIndex = start.roundToInt()
                            scope.launch { pillPosition.stop() }
                            scope.launch { pillPosition.snapTo(start) }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        // Release the jelly only after the finger leaves.
                        isPressed = false
                        val finalIndex = previewIndex.coerceIn(0, items.lastIndex)
                        dragVelocity = 0f
                        scope.launch {
                            // Always settle to an exact tab slot. This keeps
                            // the capsule centered on the selected item.
                            pillPosition.animateTo(finalIndex.toFloat(), settleSpec)
                        }
                        if (finalIndex != selectedIndex) {
                            onSelect(finalIndex)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressed = false
                        dragVelocity = 0f
                        previewIndex = selectedIndex.coerceIn(0, items.lastIndex)
                        scope.launch {
                            pillPosition.animateTo(previewIndex.toFloat(), settleSpec)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val slot = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                    if (slot > 0f) {
                        // Direct tracking during drag is intentional. It removes
                        // the old per-event animateTo() race and makes the pill
                        // stay underneath the finger even during rapid swipes.
                        val deltaIndex = dragAmount / slot
                        val next = (pillPosition.value + deltaIndex)
                            .coerceIn(0f, items.lastIndex.toFloat())
                        dragVelocity = deltaIndex
                        scope.launch { pillPosition.snapTo(next) }
                        previewIndex = next.roundToInt().coerceIn(0, items.lastIndex)
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
            // Base glass bar. No shimmer/sparkle animation; only static soft
            // glass shading and rim, keeping the visual calm and premium.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 18.dp,
                        shape = barShape,
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.38f),
                    )
                    .clip(barShape)
                    .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.045f),
                                Color.Black.copy(alpha = 0.08f),
                            )
                        )
                    )
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val topGlow = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h * 0.52f,
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
                                Color.White.copy(alpha = 0.38f),
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

                val speed = abs(dragVelocity).coerceIn(0f, 0.90f)
                val velocityEnergy = (speed / 0.90f).coerceIn(0f, 1f)

                // Real jelly deformation while sliding:
                // horizontal stretch grows with movement, vertical axis squashes,
                // and the whole capsule gets a small elastic lift.
                val stretchX = 1f + 0.24f * velocityEnergy
                val squashY = 1f - 0.105f * velocityEnergy
                val elasticLift = -with(density) {
                    (1.8f * velocityEnergy * interactionEnergy).dp.toPx()
                }

                val pillWidthPx =
                    baseWidth * (0.95f + 0.05f * pillBulge) * stretchX
                val pillHeightPx =
                    baseHeight * pillBulge * squashY

                val centerX = (pillPosition.value + 0.5f) * slotWidthPx
                val offsetX = centerX - pillWidthPx / 2f
                val offsetY = (barHeightPx - pillHeightPx) / 2f

                val pillWidthDp = with(density) { pillWidthPx.toDp() }
                val pillHeightDp = with(density) { pillHeightPx.toDp() }
                val offsetXDp = with(density) { offsetX.toDp() }
                val offsetYDp = with(density) { offsetY.toDp() }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { offsetXDp.toPx() }
                            translationY = with(density) { offsetYDp.toPx() } + elasticLift
                            // A tiny directional rotation reinforces the liquid/spring feel.
                            rotationZ = (dragVelocity * 3.8f).coerceIn(-4.0f, 4.0f)
                            transformOrigin = TransformOrigin.Center
                        }
                        .size(pillWidthDp, pillHeightDp)
                        .shadow(
                            elevation = if (isPressed) 8.dp else 3.dp,
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = primary.copy(alpha = if (isPressed) 0.18f else 0.10f),
                            spotColor = primary.copy(alpha = if (isPressed) 0.25f else 0.14f),
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.13f),
                                    Color.White.copy(alpha = 0.07f),
                                    primary.copy(alpha = 0.075f),
                                    Color.Black.copy(alpha = 0.10f),
                                )
                            )
                        )
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val topReflection = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.045f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = h * 0.62f,
                            )
                            val lowerTint = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primary.copy(alpha = 0.10f),
                                ),
                                startY = h * 0.62f,
                                endY = h,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(topReflection)
                                drawRect(lowerTint)
                            }
                        }
                        // Stronger, cleaner rim around the capsule. It stays
                        // visible during drag and does not sparkle or animate.
                        .border(
                            width = if (isPressed) 1.35.dp else 1.05.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.66f),
                                    Color.White.copy(alpha = 0.30f),
                                    outline.copy(alpha = 0.44f),
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
            dampingRatio = 0.88f,
            stiffness = 190f,
        ),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (emphasized) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 180f,
        ),
        label = "navIconScale",
    )
    val labelScale by animateFloatAsState(
        targetValue = if (emphasized) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 180f,
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
