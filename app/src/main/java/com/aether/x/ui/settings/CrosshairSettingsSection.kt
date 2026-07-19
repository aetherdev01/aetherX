package com.aether.x.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim
import com.aether.x.ui.theme.SurfaceRaised
import kotlin.math.roundToInt

private val crosshairColorPalette = listOf(
    0xFFFFFFFFL,
    0xFFFF3B30L,
    0xFFE8804AL,
    0xFFF6C560L,
    0xFFFFD60AL,
    0xFF2F6FEDL,
)

private data class StyleOption(val style: CrosshairStyle, val labelRes: Int)

private val styleOptions = listOf(
    StyleOption(CrosshairStyle.CROSS, R.string.crosshair_style_cross),
    StyleOption(CrosshairStyle.PLUS_GAP, R.string.crosshair_style_plus_gap),
    StyleOption(CrosshairStyle.X_SHAPE, R.string.crosshair_style_x),
    StyleOption(CrosshairStyle.DOT, R.string.crosshair_style_dot),
    StyleOption(CrosshairStyle.CIRCLE, R.string.crosshair_style_circle),
    StyleOption(CrosshairStyle.CIRCLE_DOT, R.string.crosshair_style_circle_dot),
    StyleOption(CrosshairStyle.CROSS_DOT, R.string.crosshair_style_cross_dot),
    StyleOption(CrosshairStyle.T_SHAPE, R.string.crosshair_style_t_shape),
    StyleOption(CrosshairStyle.DIAMOND, R.string.crosshair_style_diamond),
    StyleOption(CrosshairStyle.SQUARE, R.string.crosshair_style_square),

    StyleOption(CrosshairStyle.CHEVRON, R.string.crosshair_style_chevron),
    StyleOption(CrosshairStyle.DOUBLE_RING, R.string.crosshair_style_double_ring),
)

@Composable
fun CrosshairSettingsSection(
    enabled: Boolean,
    style: CrosshairStyle,
    colorArgb: Long,
    sizeDp: Int,
    thicknessDp: Int,
    opacityPercent: Int,
    offsetX: Int,
    offsetY: Int,
    overlayPermissionGranted: Boolean,

    dragModeActive: Boolean,

    positionLocked: Boolean = false,
    onPositionLockedChange: (Boolean) -> Unit = {},
    onEnabledChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStyleChange: (CrosshairStyle) -> Unit,
    onColorChange: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onThicknessChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onToggleDragMode: (Boolean) -> Unit,
    onResetPosition: () -> Unit,
    onOffsetChange: (x: Int, y: Int) -> Unit,
) {

    var showColorPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))

            .background(MaterialTheme.colorScheme.surface),
    ) {

        Icon(
            imageVector = Icons.Outlined.CenterFocusStrong,
            contentDescription = null,
            tint = AccentBlue.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp)
                .size(96.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.crosshair_card_title),
                        style = MaterialTheme.typography.titleLarge,

                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.crosshair_card_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked && !overlayPermissionGranted) {
                            onRequestOverlayPermission()
                        } else {
                            onEnabledChange(checked)
                        }
                    },

                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = AccentBlue,
                    ),
                )
            }

            if (!overlayPermissionGranted) {
                Text(
                    text = stringResource(R.string.crosshair_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (enabled) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StyleIconList(
                        selected = style,
                        onSelect = onStyleChange,
                        modifier = Modifier.width(56.dp),
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {

                            IconButton(onClick = { onPositionLockedChange(!positionLocked) }) {
                                Icon(
                                    imageVector = if (positionLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = stringResource(
                                        if (positionLocked) R.string.crosshair_position_unlock else R.string.crosshair_position_lock,
                                    ),
                                    tint = if (positionLocked) AccentBlue else Color.White.copy(alpha = 0.5f),
                                )
                            }
                            IconButton(onClick = onResetPosition, enabled = !positionLocked) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.crosshair_position_reset_center),
                                    tint = Color.White.copy(alpha = if (positionLocked) 0.25f else 0.5f),
                                )
                            }
                        }

                        val configuration = LocalConfiguration.current
                        val density = LocalDensity.current
                        val screenBoundsPx = remember(configuration, density) {
                            IntSize(
                                width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                                height = with(density) { configuration.screenHeightDp.dp.roundToPx() },
                            )
                        }
                        PositionJoystick(
                            offsetX = offsetX,
                            offsetY = offsetY,
                            screenBoundsPx = screenBoundsPx,
                            locked = positionLocked,
                            onOffsetChange = onOffsetChange,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    VerticalAccentSlider(
                        label = stringResource(R.string.crosshair_size_label),
                        valueText = "${(sizeDp / 40f).let { "%.1f".format(it) }}x",
                        value = sizeDp.toFloat(),
                        range = 12f..80f,
                        onValueChange = { onSizeChange(it.toInt()) },
                        modifier = Modifier.width(56.dp),
                    )
                }

                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_opacity_label),
                    valueText = "$opacityPercent%",
                    value = opacityPercent.toFloat(),
                    range = 20f..100f,
                    onValueChange = { onOpacityChange(it.toInt()) },
                    modifier = Modifier.padding(top = 20.dp),
                )

                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_thickness_label),
                    valueText = "${thicknessDp}dp",
                    value = thicknessDp.toFloat(),
                    range = 1f..12f,
                    onValueChange = { onThicknessChange(it.toInt()) },
                    modifier = Modifier.padding(top = 16.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    crosshairColorPalette.forEach { swatch ->
                        ColorSwatchLarge(
                            color = swatch,
                            selected = swatch == colorArgb,
                            onClick = { onColorChange(swatch) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CustomColorSwatch(
                        currentColorArgb = colorArgb,
                        isCustomActive = crosshairColorPalette.none { it == colorArgb },
                        onClick = { showColorPicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        CrosshairColorPickerDialog(
            initialColorArgb = colorArgb,
            onDismiss = { showColorPicker = false },
            onColorConfirmed = { picked ->
                onColorChange(picked)
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun StyleIconList(
    selected: CrosshairStyle,
    onSelect: (CrosshairStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(styleOptions, key = { it.style.name }) { option ->
            StyleIconButton(
                style = option.style,
                label = stringResource(option.labelRes),
                isSelected = option.style == selected,
                onClick = { onSelect(option.style) },
            )
        }
    }
}

@Composable
private fun StyleIconButton(
    style: CrosshairStyle,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AccentBlueDim else SurfaceRaised)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2.6f
            val thickness = 2.2f
            val drawColor = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.6f)
            when (style) {
                CrosshairStyle.CROSS -> {
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                CrosshairStyle.PLUS_GAP -> {
                    val gap = r * 0.35f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                CrosshairStyle.X_SHAPE -> {
                    val d = r * 0.7071f
                    drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                }
                CrosshairStyle.DOT -> drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
                CrosshairStyle.CIRCLE -> drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                CrosshairStyle.CIRCLE_DOT -> {
                    drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                    drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
                }
                CrosshairStyle.CROSS_DOT -> {
                    val gap = r * 0.35f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                    drawCircle(drawColor, radius = thickness * 1.4f, center = Offset(cx, cy))
                }
                CrosshairStyle.T_SHAPE -> {
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }

                CrosshairStyle.DIAMOND -> {
                    val gap = r * 0.3f
                    val d = r * 0.7071f
                    val gapD = gap * 0.7071f
                    drawLine(drawColor, Offset(cx - gapD, cy - gapD), Offset(cx - d, cy - d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx, cy - r), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + d, cy - d), Offset(cx + gapD, cy - gapD), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gapD, cy + gapD), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + d, cy + d), Offset(cx, cy + r), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + r), Offset(cx - d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx - gapD, cy + gapD), thickness, StrokeCap.Round)
                }

                CrosshairStyle.SQUARE -> {
                    val s = r * 0.85f
                    val corner = s * 0.5f

                    drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s + corner, cy - s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s, cy - s + corner), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s - corner, cy - s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s, cy - s + corner), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s + corner, cy + s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s, cy + s - corner), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s - corner, cy + s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s, cy + s - corner), thickness, StrokeCap.Round)
                }

                CrosshairStyle.CHEVRON -> {
                    val gap = r * 0.35f
                    val arm = r * 0.45f
                    val tip = r * 0.9f

                    drawLine(drawColor, Offset(cx - arm, cy - tip), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - gap), Offset(cx + arm, cy - tip), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx - arm, cy + tip), Offset(cx, cy + gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx + arm, cy + tip), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx - tip, cy - arm), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - gap, cy), Offset(cx - tip, cy + arm), thickness, StrokeCap.Round)

                    drawLine(drawColor, Offset(cx + tip, cy - arm), Offset(cx + gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + tip, cy + arm), thickness, StrokeCap.Round)
                }

                CrosshairStyle.DOUBLE_RING -> {
                    drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                    drawCircle(drawColor, radius = r * 0.55f, center = Offset(cx, cy), style = Stroke(thickness))
                }
            }
        }
    }
}

@Composable
private fun PositionJoystick(
    offsetX: Int,
    offsetY: Int,
    screenBoundsPx: IntSize,

    locked: Boolean = false,
    onOffsetChange: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackpadSize = 220.dp

    val maxOffsetX = (screenBoundsPx.width / 2).coerceAtLeast(1)
    val maxOffsetY = (screenBoundsPx.height / 2).coerceAtLeast(1)

    val latestOffsetX by rememberUpdatedState(offsetX)
    val latestOffsetY by rememberUpdatedState(offsetY)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(trackpadSize)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceRaised.copy(alpha = 0.4f))
                .border(1.dp, if (locked) AccentBlue.copy(alpha = 0.3f) else AccentBlueDim, RoundedCornerShape(20.dp))
                .pointerInput(maxOffsetX, maxOffsetY, locked) {

                    if (locked) return@pointerInput

                    val scaleX = maxOffsetX.toFloat() * 2f / size.width.toFloat()
                    val scaleY = maxOffsetY.toFloat() * 2f / size.height.toFloat()

                    var runningX = 0f
                    var runningY = 0f
                    detectDragGestures(
                        onDragStart = {
                            runningX = latestOffsetX.toFloat()
                            runningY = latestOffsetY.toFloat()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        runningX = (runningX + dragAmount.x * scaleX)
                            .coerceIn(-maxOffsetX.toFloat(), maxOffsetX.toFloat())
                        runningY = (runningY + dragAmount.y * scaleY)
                            .coerceIn(-maxOffsetY.toFloat(), maxOffsetY.toFloat())
                        onOffsetChange(runningX.roundToInt(), runningY.roundToInt())
                    }
                },
        ) {

            DirectionArrow(Alignment.TopCenter, rotationDeg = 0f)
            DirectionArrow(Alignment.BottomCenter, rotationDeg = 180f)
            DirectionArrow(Alignment.CenterStart, rotationDeg = 270f)
            DirectionArrow(Alignment.CenterEnd, rotationDeg = 90f)

            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                drawLine(
                    color = AccentBlueDim.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, size.height / 2f - 6.dp.toPx()),
                    end = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawLine(
                    color = AccentBlueDim.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f - 6.dp.toPx(), size.height / 2f),
                    end = Offset(size.width / 2f + 6.dp.toPx(), size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            val handleFractionX = 0.5f + (offsetX.toFloat() / maxOffsetX.toFloat()) * 0.5f
            val handleFractionY = 0.5f + (offsetY.toFloat() / maxOffsetY.toFloat()) * 0.5f
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (handleFractionX.coerceIn(0f, 1f) * trackpadSize.toPx() - HANDLE_SIZE_DP.dp.toPx() / 2f).roundToInt(),
                            y = (handleFractionY.coerceIn(0f, 1f) * trackpadSize.toPx() - HANDLE_SIZE_DP.dp.toPx() / 2f).roundToInt(),
                        )
                    }
                    .size(HANDLE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(AccentBlue)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            )
        }

        Text(
            text = stringResource(R.string.crosshair_position_offset_format, offsetX, offsetY),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private const val HANDLE_SIZE_DP = 22

@Composable
private fun DirectionArrow(alignment: Alignment, rotationDeg: Float) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp)
                .rotate(rotationDeg),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = AccentBlueDim)
            }
        }
    }
}

@Composable
private fun VerticalAccentSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                        onValueChange(range.start + newFraction * (range.endInclusive - range.start))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceRaised),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((fraction * 200).dp.coerceAtMost(200.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBlue),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (fraction * 200).dp.coerceAtMost(196.dp))
                        .size(width = 20.dp, height = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlue),
                )
            }
        }
    }
}

@Composable
private fun HorizontalAccentSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentBlue,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + newFraction * (range.endInclusive - range.start))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceRaised),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBlue),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (fraction * 280).dp.coerceAtMost(276.dp))
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlue),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchLarge(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(color.toInt()))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = contrastingCheckTint(Color(color.toInt())),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CustomColorSwatch(
    currentColorArgb: Long,
    isCustomActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF3B30), Color(0xFFFFD60A), Color(0xFF34C759),
                Color(0xFF2F6FED), Color(0xFFAF52DE), Color(0xFFFF3B30),
            ),
        )
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rainbowBrush)
            .border(
                width = if (isCustomActive) 2.dp else 0.dp,
                color = if (isCustomActive) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (isCustomActive) Color(currentColorArgb.toInt()) else Color.Black.copy(alpha = 0.45f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!isCustomActive) {
                Text(
                    text = "+",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = contrastingCheckTint(Color(currentColorArgb.toInt())),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun contrastingCheckTint(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}
