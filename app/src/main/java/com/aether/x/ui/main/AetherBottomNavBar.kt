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
    
    // State baru untuk melacak sinkronisasi pergerakan drag jari
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val isActive = isPressed || isDragging

    val settleSpec = remember {
        spring<Float>(
            dampingRatio = 0.60f,
            stiffness = 220f,
        )
    }

    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.coerceIn(0, items.lastIndex).toFloat()
        previewIndex = target.roundToInt()
        if (!isDragging) {
            pillPosition.animateTo(target, settleSpec)
        }
    }

    val pillBulge by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 300f,
        ),
        label = "pillBulge",
    )
    val barBulge by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 250f,
        ),
        label = "barBulge",
    )

    val currentVelocity = if (isDragging) dragVelocity else pillPosition.velocity
    val speed = abs(currentVelocity).coerceIn(0f, 7f)
    
    val targetStretchX = 1f + (speed * 0.08f).coerceAtMost(0.35f)
    val targetSquashY = 1f - (speed * 0.04f).coerceAtMost(0.15f)

    val stretchX by animateFloatAsState(
        targetValue = targetStretchX,
        animationSpec = spring(
            dampingRatio = 0.45f, 
            stiffness = 350f
        ),
        label = "stretchX"
    )
    val squashY by animateFloatAsState(
        targetValue = targetSquashY,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = 350f
        ),
        label = "squashY"
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
                                animationSpec = settleSpec
                            )
                            dragVelocity = 0f
                        }
                        if (finalIndex != selectedIndex) {
                            onSelect(finalIndex)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressed = false
                        previewIndex = selectedIndex.coerceIn(0, items.lastIndex)
                        scope.launch {
                            pillPosition.animateTo(
                                targetValue = previewIndex.toFloat(),
                                initialVelocity = dragVelocity,
                                animationSpec = settleSpec
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
                        
                        dragPosition = (dragPosition + deltaIndex).coerceIn(0f, items.lastIndex.toFloat())
                        
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
                                Color.White.copy(alpha = 0.04f), 
                                Color.White.copy(alpha = 0.01f),
                                Color.Black.copy(alpha = 0.05f),
                            )
                        )
                    )
                    .drawWithCache {
                        val h = size.height
                        val topGlow = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.06f), 
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
                                Color.White.copy(alpha = 0.15f), 
                                outline.copy(alpha = 0.20f),
                            )
                        ),
                        shape = barShape,
                    )
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

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { offsetXDp.toPx() }
                            translationY = with(density) { offsetYDp.toPx() }
                        }
                        .size(pillWidthDp, pillHeightDp)
                        .shadow(
                            elevation = if (isActive) 8.dp else 3.dp,
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = primary.copy(alpha = if (isActive) 0.18f else 0.10f),
                            spotColor = primary.copy(alpha = if (isActive) 0.25f else 0.14f),
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.08f), 
                                    Color.White.copy(alpha = 0.03f),
                                    primary.copy(alpha = 0.075f),
                                    Color.Black.copy(alpha = 0.05f),
                                )
                            )
                        )
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            
                            val topReflection = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f), 
                                    Color.White.copy(alpha = 0.02f),
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

                            val leftReflection = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f), 
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                                center = Offset(w * 0.08f, h * 0.5f),
                                radius = w * 0.35f
                            )
                            val rightReflection = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f), 
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                                center = Offset(w * 0.92f, h * 0.5f),
                                radius = w * 0.35f
                            )

                            onDrawWithContent {
                                drawContent()
                                drawRect(topReflection)
                                drawRect(lowerTint)
                                drawRect(leftReflection)
                                drawRect(rightReflection)
                            }
                        }
                        .border(
                            width = if (isActive) 1.35.dp else 1.05.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.25f), 
                                    Color.White.copy(alpha = 0.10f),
                                    outline.copy(alpha = 0.30f),
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
            dampingRatio = 0.65f,
            stiffness = 200f,
        ),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (emphasized) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 250f,
        ),
        label = "navIconScale",
    )
    val labelScale by animateFloatAsState(
        targetValue = if (emphasized) 1.10f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 250f,
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
