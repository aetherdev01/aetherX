package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class AetherNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

/*
 * AetherBottomNavBar
 *
 * iOS 26-inspired floating Liquid Glass navigation:
 * - active capsule previews the destination while dragging
 * - capsule follows the finger 1:1
 * - short, velocity-aware settling instead of a slow/bouncy spring
 * - smaller glass hierarchy: bar = subtle, capsule = interactive
 * - restrained tint and specular highlights for a cleaner iOS 26 look
 *
 * This is an Android/Compose recreation of the visual/interaction language,
 * not Apple's private/native implementation.
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
    val capsuleShape = RoundedCornerShape(percent = 50)
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }

    val slotWidthPx =
        if (barWidthPx > 0f && items.isNotEmpty()) {
            barWidthPx / items.size
        } else {
            0f
        }

    val pillPosition = remember { Animatable(selectedIndex.coerceIn(0, items.lastIndex).toFloat()) }
    var previewIndex by remember {
        mutableStateOf(selectedIndex.coerceIn(0, items.lastIndex))
    }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val isActive = isPressed || isDragging
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var dragPosition by remember {
        mutableFloatStateOf(selectedIndex.coerceIn(0, items.lastIndex).toFloat())
    }
    var lastDragTimeNs by remember { mutableFloatStateOf(0f) }

    var settleJob by remember { mutableStateOf<Job?>(null) }

    // iOS-like: quick settle, critically damped, almost no visible rebound.
    val settleSpec = remember {
        spring<Float>(
            dampingRatio = 0.92f,
            stiffness = 760f,
            visibilityThreshold = 0.001f,
        )
    }

    // Very short press preview: the capsule starts moving almost immediately.
    val previewSpec: TweenSpec<Float> = remember {
        tween(
            durationMillis = 135,
            easing = FastOutSlowInEasing,
        )
    }

    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
        previewIndex = target.roundToInt()
        dragPosition = target

        if (!isDragging) {
            settleJob?.cancel()
            pillPosition.animateTo(target, settleSpec)
        }
    }

    // The capsule "breathes" on touch, but does not distort the whole bar.
    val capsuleInteractionScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.025f
            isPressed -> 1.018f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 700f,
        ),
        label = "capsuleInteractionScale",
    )

    // Small velocity deformation, capped to stay refined rather than bouncy.
    val normalizedSpeed = abs(dragVelocity).coerceIn(0f, 7f)
    val stretchTarget =
        1f + (normalizedSpeed / 7f) * if (isDragging) 0.045f else 0.020f
    val squashTarget =
        1f - (normalizedSpeed / 7f) * if (isDragging) 0.020f else 0.010f

    val stretchX by animateFloatAsState(
        targetValue = stretchTarget,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 850f,
        ),
        label = "capsuleStretchX",
    )

    val squashY by animateFloatAsState(
        targetValue = squashTarget,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 850f,
        ),
        label = "capsuleSquashY",
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            // Slightly wider floating margins, closer to the compact iOS 26 bar.
            .padding(horizontal = 28.dp, vertical = 10.dp)
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
                        val slot = slotWidthPx
                        if (slot <= 0f) return@detectTapGestures

                        val target = (
                            (offset.x / slot) - 0.5f
                        ).coerceIn(0f, items.lastIndex.toFloat())

                        previewIndex = target.roundToInt().coerceIn(0, items.lastIndex)
                        isPressed = true

                        settleJob?.cancel()
                        settleJob = scope.launch {
                            // Press feedback should feel immediate, not like a page transition.
                            pillPosition.animateTo(target, previewSpec)
                        }

                        val released = tryAwaitRelease()

                        isPressed = false

                        if (!released && !isDragging) {
                            val fallback = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
                            previewIndex = fallback.roundToInt()

                            settleJob?.cancel()
                            settleJob = scope.launch {
                                pillPosition.animateTo(fallback, settleSpec)
                            }
                        }
                    },
                    onTap = { offset ->
                        val slot = slotWidthPx
                        if (slot <= 0f) return@detectTapGestures

                        val tapped = (
                            ((offset.x / slot) - 0.5f)
                                .coerceIn(0f, items.lastIndex.toFloat())
                        ).roundToInt().coerceIn(0, items.lastIndex)

                        previewIndex = tapped
                        onSelect(tapped)

                        settleJob?.cancel()
                        settleJob = scope.launch {
                            pillPosition.animateTo(
                                targetValue = tapped.toFloat(),
                                animationSpec = settleSpec,
                            )
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

                        val slot = slotWidthPx
                        if (slot <= 0f) return@detectHorizontalDragGestures

                        val start = (
                            (offset.x / slot) - 0.5f
                        ).coerceIn(0f, items.lastIndex.toFloat())

                        dragPosition = start
                        previewIndex = start.roundToInt().coerceIn(0, items.lastIndex)
                        lastDragTimeNs = System.nanoTime().toFloat()

                        settleJob?.cancel()
                        scope.launch {
                            pillPosition.stop()
                            pillPosition.snapTo(start)
                        }
                    },
                    onDragEnd = {
                        val finalIndex = previewIndex.coerceIn(0, items.lastIndex)
                        val releaseVelocity = dragVelocity

                        isDragging = false
                        isPressed = false

                        settleJob?.cancel()
                        settleJob = scope.launch {
                            pillPosition.animateTo(
                                targetValue = finalIndex.toFloat(),
                                initialVelocity = releaseVelocity,
                                animationSpec = settleSpec,
                            )
                            dragVelocity = 0f
                        }

                        onSelect(finalIndex)
                    },
                    onDragCancel = {
                        val fallback = selectedIndex.coerceIn(0, items.lastIndex)
                        isDragging = false
                        isPressed = false
                        previewIndex = fallback
                        dragVelocity = 0f

                        settleJob?.cancel()
                        settleJob = scope.launch {
                            pillPosition.animateTo(
                                targetValue = fallback.toFloat(),
                                animationSpec = settleSpec,
                            )
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()

                    val slot = slotWidthPx
                    if (slot <= 0f) return@detectHorizontalDragGestures

                    val deltaIndex = dragAmount / slot
                    val nowNs = System.nanoTime().toFloat()
                    val previousNs = lastDragTimeNs
                    val deltaSeconds = ((nowNs - previousNs) / 1_000_000_000f)
                        .coerceIn(1f / 240f, 0.1f)

                    dragVelocity = (deltaIndex / deltaSeconds)
                        .coerceIn(-6.5f, 6.5f)
                    lastDragTimeNs = nowNs

                    dragPosition = (
                        dragPosition + deltaIndex
                    ).coerceIn(0f, items.lastIndex.toFloat())

                    previewIndex = dragPosition
                        .roundToInt()
                        .coerceIn(0, items.lastIndex)

                    // During drag, the capsule reads dragPosition directly (1:1 with the finger).
                    // No suspend call is needed here, so there is no coroutine backlog.
                    // pillPosition remains reserved for the release/settle animation.
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // ── Floating glass bar ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 12.dp,
                        shape = barShape,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .clip(barShape)
                    .hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.thin(),
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.070f),
                                Color.White.copy(alpha = 0.035f),
                                Color.Black.copy(alpha = 0.018f),
                            ),
                        ),
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.34f),
                                Color.White.copy(alpha = 0.13f),
                                outline.copy(alpha = 0.10f),
                            ),
                        ),
                        shape = barShape,
                    ),
            )

            if (slotWidthPx > 0f && barHeightPx > 0f) {
                // Keep a visible breathing space around the active glass capsule.
                val horizontalInsetPx = with(density) { 4.dp.toPx() }
                val verticalInsetPx = with(density) { 4.dp.toPx() }

                val baseWidth =
                    (slotWidthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
                val baseHeight =
                    (barHeightPx - verticalInsetPx * 2f).coerceAtLeast(1f)

                val pillWidthPx = baseWidth * capsuleInteractionScale * stretchX
                val pillHeightPx = baseHeight * capsuleInteractionScale * squashY

                val visualPosition = if (isDragging) dragPosition else pillPosition.value
                val centerX =
                    (visualPosition + 0.5f) * slotWidthPx

                val offsetX = centerX - pillWidthPx / 2f
                val offsetY = (barHeightPx - pillHeightPx) / 2f

                val pillWidthDp = with(density) { pillWidthPx.toDp() }
                val pillHeightDp = with(density) { pillHeightPx.toDp() }
                val offsetXDp = with(density) { offsetX.toDp() }
                val offsetYDp = with(density) { offsetY.toDp() }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = offsetXDp.toPx()
                            translationY = offsetYDp.toPx()
                        }
                        .size(
                            width = pillWidthDp,
                            height = pillHeightDp,
                        )
                        .shadow(
                            elevation = if (isPressed) 4.dp else 2.dp,
                            shape = capsuleShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(capsuleShape)
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(),
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.075f),
                                    primary.copy(alpha = if (isPressed) 0.070f else 0.045f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .drawWithCache {
                            val w = size.width
                            val h = size.height

                            // A soft top "lens" highlight: brighter on contact,
                            // but intentionally much subtler than a generic glossy pill.
                            val topSpecular = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(
                                        alpha = if (isPressed) 0.38f else 0.28f,
                                    ),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = h * 0.42f,
                            )

                            val lowerDepth = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.055f),
                                ),
                                startY = h * 0.72f,
                                endY = h,
                            )

                            // Edge sheen keeps the capsule readable over bright media.
                            val sideSheen = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.06f),
                                ),
                                startX = 0f,
                                endX = w,
                            )

                            onDrawWithContent {
                                drawContent()
                                drawRect(topSpecular)
                                drawRect(lowerDepth)
                                drawRect(sideSheen)
                            }
                        }
                        .border(
                            width = if (isPressed) 1.15.dp else 0.8.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(
                                        alpha = if (isPressed) 0.62f else 0.46f,
                                    ),
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.08f),
                                    outline.copy(alpha = 0.12f),
                                ),
                            ),
                            shape = capsuleShape,
                        ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 6.dp,
                        vertical = 5.dp,
                    ),
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
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        },
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 700f,
        ),
        label = "navItemColor",
    )

    val iconScale by animateFloatAsState(
        targetValue = when {
            emphasized -> 1.075f
            selected -> 1.025f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 720f,
        ),
        label = "navIconScale",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing,
        ),
        label = "navLabelAlpha",
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
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )

        Text(
            text = item.label,
            color = contentColor.copy(alpha = labelAlpha),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(
                    top = 3.dp,
                    start = 2.dp,
                    end = 2.dp,
                ),
        )
    }
}
