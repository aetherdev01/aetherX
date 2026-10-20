package com.aether.x.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.components.CrosshairPreview
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.referenceSliderVisual
import com.aether.x.ui.components.glassPanel
import com.aether.x.ui.components.DialogWindowDim
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.TextSecondary
import com.aether.x.ui.theme.AccentBlueDim
import com.aether.x.ui.theme.SurfaceRaised
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val crosshairColorPalette = listOf(
    0xFF00FF66L, // Neon green (default)
    0xFF00E5FFL, // Cyan
    0xFF0A84FFL, // Blue
    0xFFFFD60AL, // Yellow
    0xFFFF9F0AL, // Orange
    0xFFFF3B30L, // Red
    0xFFFF2D55L, // Pink
    0xFFBF5AF2L, // Purple
    0xFFFFFFFFL, // White
    0xFF111111L, // Black
)

private data class StyleOption(val style: CrosshairStyle, val labelRes: Int)

private val styleOptions = listOf(
    StyleOption(CrosshairStyle.DOT, R.string.crosshair_style_dot),
    StyleOption(CrosshairStyle.PLUS, R.string.crosshair_style_plus),
    StyleOption(CrosshairStyle.CIRCLE_PLUS, R.string.crosshair_style_circle_plus),
    StyleOption(CrosshairStyle.TICK_CROSS, R.string.crosshair_style_tick_cross),
    StyleOption(CrosshairStyle.CIRCLE_DOT_TICKS, R.string.crosshair_style_circle_dot_ticks),
    StyleOption(CrosshairStyle.CIRCLE_CROSS_TICKS, R.string.crosshair_style_circle_cross_ticks),
    StyleOption(CrosshairStyle.BULLET, R.string.crosshair_style_bullet),
    StyleOption(CrosshairStyle.CROSS, R.string.crosshair_style_cross),
    StyleOption(CrosshairStyle.PLUS_GAP, R.string.crosshair_style_plus_gap),
    StyleOption(CrosshairStyle.X_SHAPE, R.string.crosshair_style_x),
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
    rotationDegrees: Int,
    offsetX: Int,
    offsetY: Int,
    overlayPermissionGranted: Boolean,

    onEnabledChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStyleChange: (CrosshairStyle) -> Unit,
    onColorChange: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onRotationChange: (Int) -> Unit,
    onNudgePosition: (dx: Int, dy: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aetherCardSurface(MaterialTheme.shapes.medium),
    ) {
        // Latar Ikon Samark
        Icon(
            imageVector = Icons.Outlined.CenterFocusStrong,
            contentDescription = null,
            tint = AccentBlue.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp)
                .size(96.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // Header: Judul & Switch
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
                    }
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
                Spacer(modifier = Modifier.height(18.dp))

                // Pratinjau selalu mengikuti bentuk, warna, ukuran, dan rotasi aktif.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CrosshairPreview(
                        style = style,
                        color = Color(colorArgb.toInt()),
                        sizeDp = sizeDp.toFloat(),
                        rotationDegrees = rotationDegrees,
                        backgroundColor = Color(0xFF101218),
                        modifier = Modifier.size(104.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = "PRATINJAU LANGSUNG",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(styleOptions.firstOrNull { it.style == style }?.labelRes ?: R.string.crosshair_style_cross),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorArgb.toInt()))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                            )
                            Text(
                                text = "#%06X".format(colorArgb.toInt() and 0xFFFFFF),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Ukuran ${sizeDp} dp · ${rotationDegrees}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pemilih gaya dibuat berupa bar yang jelas, bukan hanya ikon kecil.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { showStylePicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(30.dp)) {
                            StyleIconButtonInner(style = style, color = Color(colorArgb.toInt()))
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Gaya Crosshair", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(styleOptions.firstOrNull { it.style == style }?.labelRes ?: R.string.crosshair_style_cross),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text("PILIH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Posisi & ukuran", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PositionDPadOnly(
                            offsetX = offsetX,
                            offsetY = offsetY,
                            onNudgePosition = onNudgePosition,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                            .padding(vertical = 10.dp),
                    ) {
                        VerticalAccentSlider(
                            label = stringResource(R.string.crosshair_size_label),
                            valueText = "${(sizeDp / 40f).let { "%.1f".format(it) }}x",
                            value = sizeDp.toFloat(),
                            range = 12f..80f,
                            onValueChange = { onSizeChange(it.toInt()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Rotasi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${rotationDegrees}°", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    val rotationInteractionSource = remember { MutableInteractionSource() }
                    val rotationIsPressed by rotationInteractionSource.collectIsPressedAsState()
                    val rotationSliderScale by animateFloatAsState(
                        targetValue = if (rotationIsPressed) 1.035f else 1f,
                        animationSpec = spring(
                            dampingRatio = if (rotationIsPressed) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
                            stiffness = if (rotationIsPressed) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
                        ),
                        label = "crosshairRotationPop",
                    )
                    Slider(
                        value = rotationDegrees.toFloat(),
                        onValueChange = { onRotationChange(it.roundToInt()) },
                        valueRange = 0f..360f,
                        interactionSource = rotationInteractionSource,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(rotationSliderScale)
                            .referenceSliderVisual(
                                value = rotationDegrees.toFloat(),
                                valueRange = 0f..360f,
                                activeColor = AccentBlue,
                                inactiveColor = AccentBlue.copy(alpha = 0.14f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Warna Crosshair", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Pilih warna preset atau buat warna sendiri.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    crosshairColorPalette.forEach { swatch ->
                        ColorSwatchLarge(
                            color = swatch,
                            selected = swatch == colorArgb,
                            onClick = { onColorChange(swatch) },
                        )
                    }
                    CustomColorSwatch(
                        currentColorArgb = colorArgb,
                        isCustomActive = crosshairColorPalette.none { it == colorArgb },
                        onClick = { showColorPicker = true },
                    )
                }
            }
        }
    }

    // Dialog Pop-up Gaya Crosshair
    if (showStylePicker) {
        CrosshairStylePickerDialog(
            currentStyle = style,
            onDismiss = { showStylePicker = false },
            onStyleSelected = { 
                onStyleChange(it)
                showStylePicker = false
            }
        )
    }

    // Dialog Color Picker (Asli)
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

// Dialog Pop-up Mengambang untuk Pilihan Style
@Composable
fun CrosshairStylePickerDialog(
    currentStyle: CrosshairStyle,
    onDismiss: () -> Unit,
    onStyleSelected: (CrosshairStyle) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogWindowDim()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Pilih Gaya Crosshair",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 78.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                ) {
                    items(styleOptions, key = { it.style.name }) { option ->
                        StyleIconButton(
                            style = option.style,
                            label = stringResource(option.labelRes),
                            isSelected = option.style == currentStyle,
                            onClick = { onStyleSelected(option.style) },
                        )
                    }
                }
            }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) AccentBlueDim else SurfaceRaised.copy(alpha = 0.68f))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.size(28.dp)) {
            StyleIconButtonInner(
                style = style,
                color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Logika Canvas dipisah agar bisa digunakan ulang untuk preview kecil di bar utama
@Composable
private fun StyleIconButtonInner(style: CrosshairStyle, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2.6f
        val thickness = 2.2f

        when (style) {
            CrosshairStyle.CROSS -> {
                drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.PLUS -> {
                drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.BULLET -> {
                drawCircle(color, radius = r * 0.5f, center = Offset(cx, cy))
            }
            CrosshairStyle.PLUS_GAP -> {
                val gap = r * 0.35f
                drawLine(color, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.X_SHAPE -> {
                val d = r * 0.7071f
                drawLine(color, Offset(cx - d, cy - d), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - d, cy + d), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
            }
            CrosshairStyle.DOT -> drawCircle(color, radius = thickness * 1.6f, center = Offset(cx, cy))
            CrosshairStyle.CIRCLE -> drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
            CrosshairStyle.CIRCLE_DOT -> {
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                drawCircle(color, radius = thickness * 1.6f, center = Offset(cx, cy))
            }
            CrosshairStyle.CROSS_DOT -> {
                val gap = r * 0.35f
                drawLine(color, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                drawCircle(color, radius = thickness * 1.4f, center = Offset(cx, cy))
            }
            CrosshairStyle.T_SHAPE -> {
                drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.DIAMOND -> {
                val gap = r * 0.3f
                val d = r * 0.7071f
                val gapD = gap * 0.7071f
                drawLine(color, Offset(cx - gapD, cy - gapD), Offset(cx - d, cy - d), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - d, cy - d), Offset(cx, cy - r), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + d, cy - d), Offset(cx + gapD, cy - gapD), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + gapD, cy + gapD), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + d, cy + d), Offset(cx, cy + r), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + r), Offset(cx - d, cy + d), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - d, cy + d), Offset(cx - gapD, cy + gapD), thickness, StrokeCap.Round)
            }
            CrosshairStyle.SQUARE -> {
                val s = r * 0.85f
                val corner = s * 0.5f
                drawLine(color, Offset(cx - s, cy - s), Offset(cx - s + corner, cy - s), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - s, cy - s), Offset(cx - s, cy - s + corner), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + s, cy - s), Offset(cx + s - corner, cy - s), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + s, cy - s), Offset(cx + s, cy - s + corner), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - s, cy + s), Offset(cx - s + corner, cy + s), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - s, cy + s), Offset(cx - s, cy + s - corner), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + s, cy + s), Offset(cx + s - corner, cy + s), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + s, cy + s), Offset(cx + s, cy + s - corner), thickness, StrokeCap.Round)
            }
            CrosshairStyle.CHEVRON -> {
                val gap = r * 0.35f
                val arm = r * 0.45f
                val tip = r * 0.9f
                drawLine(color, Offset(cx - arm, cy - tip), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - gap), Offset(cx + arm, cy - tip), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - arm, cy + tip), Offset(cx, cy + gap), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx + arm, cy + tip), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - tip, cy - arm), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx - gap, cy), Offset(cx - tip, cy + arm), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + tip, cy - arm), Offset(cx + gap, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + tip, cy + arm), thickness, StrokeCap.Round)
            }
            CrosshairStyle.DOUBLE_RING -> {
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                drawCircle(color, radius = r * 0.55f, center = Offset(cx, cy), style = Stroke(thickness))
            }
            CrosshairStyle.CIRCLE_PLUS -> {
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                val armInner = r * 0.15f
                drawLine(color, Offset(cx - r * 0.75f, cy), Offset(cx - armInner, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + armInner, cy), Offset(cx + r * 0.75f, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r * 0.75f), Offset(cx, cy - armInner), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + armInner), Offset(cx, cy + r * 0.75f), thickness, StrokeCap.Round)
            }
            CrosshairStyle.TICK_CROSS -> {
                val tickInner = r * 0.45f
                drawLine(color, Offset(cx - r, cy), Offset(cx - tickInner, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + tickInner, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - tickInner), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + tickInner), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.CIRCLE_DOT_TICKS -> {
                drawCircle(color, radius = r * 0.7f, center = Offset(cx, cy), style = Stroke(thickness))
                drawCircle(color, radius = thickness * 1.2f, center = Offset(cx, cy))
                val tickStart = r * 0.7f + thickness * 0.4f
                drawLine(color, Offset(cx - r, cy), Offset(cx - tickStart, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + tickStart, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - tickStart), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + tickStart), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
            CrosshairStyle.CIRCLE_CROSS_TICKS -> {
                drawCircle(color, radius = r * 0.7f, center = Offset(cx, cy), style = Stroke(thickness))
                drawLine(color, Offset(cx - r * 0.7f, cy), Offset(cx + r * 0.7f, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f), thickness, StrokeCap.Round)
                val tickStart2 = r * 0.7f + thickness * 0.4f
                drawLine(color, Offset(cx - r, cy), Offset(cx - tickStart2, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx + tickStart2, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - tickStart2), thickness, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + tickStart2), Offset(cx, cy + r), thickness, StrokeCap.Round)
            }
        }
    }
}

/**
 * Komponen D-Pad murni (tanpa ring rotasi) agar UI lebih terfokus
 */
@Composable
private fun PositionDPadOnly(
    offsetX: Int,
    offsetY: Int,
    onNudgePosition: (dx: Int, dy: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nudgeStep = 6
    val ringColor = AccentBlueDim.copy(alpha = 0.5f)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Lingkaran tipis latar D-Pad
        Canvas(modifier = Modifier.size(130.dp)) {
            val strokeWidth = 1.dp.toPx()
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f,
                style = Stroke(strokeWidth)
            )
        }

        DPadTriangle(
            direction = DPadDirection.UP,
            onNudge = { onNudgePosition(0, -nudgeStep) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        )
        DPadTriangle(
            direction = DPadDirection.DOWN,
            onNudge = { onNudgePosition(0, nudgeStep) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
        DPadTriangle(
            direction = DPadDirection.LEFT,
            onNudge = { onNudgePosition(-nudgeStep, 0) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
        )
        DPadTriangle(
            direction = DPadDirection.RIGHT,
            onNudge = { onNudgePosition(nudgeStep, 0) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        )

        // Label Koordinat X Y di tengah
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "X: $offsetX", style = MaterialTheme.typography.labelSmall, color = AccentBlue)
            Text(text = "Y: $offsetY", style = MaterialTheme.typography.labelSmall, color = AccentBlue)
        }
    }
}

private enum class DPadDirection { UP, DOWN, LEFT, RIGHT }

@Composable
private fun DPadTriangle(
    direction: DPadDirection,
    onNudge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHeld by remember { mutableStateOf(false) }
    val latestOnNudge by rememberUpdatedState(onNudge)

    LaunchedEffect(isHeld) {
        if (!isHeld) return@LaunchedEffect
        latestOnNudge()
        delay(350)
        while (isHeld) {
            latestOnNudge()
            delay(80)
        }
    }

    val triangleRotation = when (direction) {
        DPadDirection.UP -> 0f
        DPadDirection.RIGHT -> 90f
        DPadDirection.DOWN -> 180f
        DPadDirection.LEFT -> 270f
    }

    val heldColor = AccentBlue
    val idleColor = TextSecondary
    Box(
        modifier = modifier
            .size(36.dp)
            .pointerInput(direction) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isHeld = true
                        waitForUpOrCancellation()
                        isHeld = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(16.dp)
                .rotate(triangleRotation),
        ) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color = if (isHeld) heldColor else idleColor)
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
    var isDragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.16f else 1f,
        animationSpec = spring(
            dampingRatio = if (isDragging) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "crosshairSizeThumbPop",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AccentBlue,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                    ) { change, _ ->
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
                    .background(Color.Black.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBlue),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (fraction * 120).dp.coerceAtMost(116.dp))
                        .size(width = 20.dp, height = 10.dp)
                        .scale(thumbScale)
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
    val swatchColor = Color(color.toInt())
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Warna dipilih",
                tint = contrastingCheckTint(swatchColor),
                modifier = Modifier.size(19.dp),
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
                Color(0xFF00E5FF), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF3B30),
            ),
        )
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(rainbowBrush)
            .border(
                width = if (isCustomActive) 2.5.dp else 1.dp,
                color = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(if (isCustomActive) Color(currentColorArgb.toInt()) else Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!isCustomActive) {
                Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Warna kustom dipilih",
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
