package com.aether.x.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim
import com.aether.x.ui.theme.SurfaceRaised
import kotlinx.coroutines.delay
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

    Box(
        modifier = modifier
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.crosshair_offset_format, offsetX, offsetY),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        CrosshairRotationSlider(
                            rotationDegrees = rotationDegrees,
                            onRotationChange = onRotationChange,
                            onNudgePosition = onNudgePosition,
                            modifier = Modifier.fillMaxWidth(),
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
                CrosshairStyle.PLUS -> {
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                CrosshairStyle.BULLET -> {
                    drawCircle(drawColor, radius = r * 0.5f, center = Offset(cx, cy))
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

                CrosshairStyle.CIRCLE_PLUS -> {
                    drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                    val armInner = r * 0.15f
                    drawLine(drawColor, Offset(cx - r * 0.75f, cy), Offset(cx - armInner, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + armInner, cy), Offset(cx + r * 0.75f, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r * 0.75f), Offset(cx, cy - armInner), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + armInner), Offset(cx, cy + r * 0.75f), thickness, StrokeCap.Round)
                }

                CrosshairStyle.TICK_CROSS -> {
                    val tickInner = r * 0.45f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - tickInner, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + tickInner, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - tickInner), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + tickInner), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }

                CrosshairStyle.CIRCLE_DOT_TICKS -> {
                    drawCircle(drawColor, radius = r * 0.7f, center = Offset(cx, cy), style = Stroke(thickness))
                    drawCircle(drawColor, radius = thickness * 1.2f, center = Offset(cx, cy))
                    val tickStart = r * 0.7f + thickness * 0.4f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - tickStart, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + tickStart, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - tickStart), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + tickStart), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }

                CrosshairStyle.CIRCLE_CROSS_TICKS -> {
                    drawCircle(drawColor, radius = r * 0.7f, center = Offset(cx, cy), style = Stroke(thickness))
                    drawLine(drawColor, Offset(cx - r * 0.7f, cy), Offset(cx + r * 0.7f, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f), thickness, StrokeCap.Round)
                    val tickStart2 = r * 0.7f + thickness * 0.4f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - tickStart2, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + tickStart2, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - tickStart2), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + tickStart2), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * Widget gabungan: ring rotasi (luar) + D-pad segitiga geser posisi
 * (dalam), persis satu widget bulat seperti pada gambar referensi —
 * bukan dua kontrol terpisah lagi.
 *
 * - Ring luar + handle bulat merah kecil yang bisa digeser BERPUTAR
 *   mengelilingi tepi lingkaran mengatur rotasi bentuk crosshair
 *   ([CrosshairView.rotationDegrees]), dengan angka derajat di tengah —
 *   pengganti `PositionJoystick` lama untuk rotasi, sudah dikonfirmasi
 *   benar sebelumnya dan TIDAK diubah cara kerjanya di sini.
 * - 4 segitiga di dalam ring (atas/bawah/kiri/kanan) menggeser POSISI
 *   crosshair di layar saat DITAHAN (repeat-loop selama jari menempel),
 *   terpisah secara fungsi dari rotasi meski satu widget visual — nilai
 *   X/Y hasil geser ditampilkan di label terpisah di atas widget ini
 *   (lihat `crosshair_offset_format`), bukan di tengah dial (tengah
 *   dial tetap menampilkan derajat rotasi).
 */
@Composable
private fun CrosshairRotationSlider(
    rotationDegrees: Int,
    onRotationChange: (Int) -> Unit,
    onNudgePosition: (dx: Int, dy: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedRotation = rotationDegrees.coerceIn(0, 360)
    val fraction = clampedRotation / 360f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 90),
        label = "rotationSlider",
    )
    val nudgeStep = 6

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ROTATION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.55f),
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(AccentBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.crosshair_rotation_degrees_format,
                        clampedRotation,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceRaised.copy(alpha = 0.7f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        fun updateFromX(x: Float) {
                            val fractionFromX = (x / size.width.toFloat()).coerceIn(0f, 1f)
                            onRotationChange((fractionFromX * 360f).roundToInt())
                        }

                        updateFromX(down.position.x)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            updateFromX(change.position.x)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 14.dp),
            ) {
                val trackHeight = 7.dp.toPx()
                val trackTop = center.y - trackHeight / 2f
                val left = 0f
                val right = size.width
                val animatedRight = left + (right - left) * animatedFraction

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.09f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, trackTop),
                    size = androidx.compose.ui.geometry.Size(right - left, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                )

                if (animatedRight > left) {
                    drawRoundRect(
                        color = AccentBlue,
                        topLeft = androidx.compose.ui.geometry.Offset(left, trackTop),
                        size = androidx.compose.ui.geometry.Size(animatedRight - left, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                    )
                }

                val thumbWidth = 34.dp.toPx()
                val thumbHeight = 22.dp.toPx()
                val thumbCenterX = left + (right - left) * animatedFraction
                val thumbLeft = (thumbCenterX - thumbWidth / 2f)
                    .coerceIn(left, (right - thumbWidth).coerceAtLeast(left))

                drawRoundRect(
                    color = AccentBlue,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        thumbLeft,
                        center.y - thumbHeight / 2f,
                    ),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        thumbLeft + 7.dp.toPx(),
                        center.y - 2.dp.toPx(),
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        thumbWidth - 14.dp.toPx(),
                        4.dp.toPx(),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                Text("90°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.28f))
                Text("180°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.28f))
                Text("270°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.28f))
                Text("360°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DPadTriangle(
                direction = DPadDirection.LEFT,
                onNudge = { onNudgePosition(-nudgeStep, 0) },
                modifier = Modifier.weight(1f),
            )
            DPadTriangle(
                direction = DPadDirection.UP,
                onNudge = { onNudgePosition(0, -nudgeStep) },
                modifier = Modifier.weight(1f),
            )
            DPadTriangle(
                direction = DPadDirection.DOWN,
                onNudge = { onNudgePosition(0, nudgeStep) },
                modifier = Modifier.weight(1f),
            )
            DPadTriangle(
                direction = DPadDirection.RIGHT,
                onNudge = { onNudgePosition(nudgeStep, 0) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class DPadDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Satu segitiga D-pad. Menahan (press-and-hold) memicu [onNudge] berulang
 * kali lewat `LaunchedEffect` + delay loop sampai jari diangkat; ketuk
 * singkat tetap menggeser 1 langkah.
 */
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

    Box(
        modifier = modifier
            .size(32.dp)
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
                .size(14.dp)
                .rotate(triangleRotation),
        ) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color = if (isHeld) AccentBlue else Color.White.copy(alpha = 0.55f))
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
