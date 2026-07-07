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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.components.CrosshairPreview
import com.aether.x.ui.theme.CrosshairAccent
import com.aether.x.ui.theme.CrosshairAccentDim
import com.aether.x.ui.theme.CrosshairCardBg
import com.aether.x.ui.theme.CrosshairCardBgAlt
import kotlin.math.atan2
import kotlin.math.roundToInt

private val crosshairColorPalette = listOf(
    0xFFFFFFFFL, // putih
    0xFFFF3B30L, // merah
    0xFFE8804AL, // oranye terracotta (warna aksen section ini)
    0xFFF6C560L, // kuning keemasan
    0xFFFFD60AL, // kuning
    0xFF2F6FEDL, // biru
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
)

/**
 * REWORK TOTAL (lihat perintah rework terbaru — "Samakan Section Crosshair
 * Persis seperti foto ke dua dari UI, Slider buat Size, Alpha, dan Gaya
 * Lainnya semirip mungkin"): tampilan diganti total mengikuti referensi
 * visual yang diberikan pengguna, BUKAN lagi mengikuti gaya SectionCard
 * generik app ini:
 * - Kartu besar berlatar [CrosshairCardBg] (coklat sangat gelap, BUKAN
 *   `MaterialTheme.colorScheme.surface` netral) dengan watermark logo
 *   AetherX transparan di pojok kiri-atas, dan Switch besar oranye
 *   ([CrosshairAccent]) di pojok kanan-atas untuk enable/disable.
 * - Preview mini crosshair kecil di tengah-atas (sama seperti sebelumnya,
 *   tapi posisi & ukurannya disesuaikan meniru referensi).
 * - List style DI KIRI sekarang berupa tombol persegi ICON-ONLY (bukan
 *   chip lebar berlabel teks) — [StyleIconButton] menggambar bentuk
 *   crosshair-nya sendiri lewat Canvas kecil, scrollable vertikal untuk
 *   menampung 8 style yang tersedia.
 * - Kontrol posisi X/Y SEKARANG berupa "joystick" radial ([PositionJoystick])
 *   di tengah — lingkaran besar dengan handle bisa digeser ke segala arah,
 *   menggantikan tombol "Mulai Geser Posisi"/"Reset ke Tengah" bergaya teks
 *   sebelumnya. Menyeret handle langsung mengubah crosshairOffsetX/Y.
 * - Slider Size SEKARANG VERTIKAL ([VerticalAccentSlider]) di kanan,
 *   Alpha tetap horizontal di bawah joystick — keduanya pakai gaya
 *   custom (thumb kecil oranye persegi-rounded di atas track tipis)
 *   alih-alih `TweakSlider`/M3 Slider default, supaya visualnya ramping
 *   seperti referensi (bukan Slider Material lebar dengan track tebal).
 * - Warna dipakai sebagai swatch besar rounded-square berjajar horizontal
 *   di baris paling bawah, BUKAN lingkaran kecil seperti sebelumnya.
 *
 * Section ini SEKARANG MENGGAMBAR KARTUNYA SENDIRI (dipanggil langsung dari
 * SettingsScreen, TIDAK lagi dibungkus SectionCard generik) — lihat
 * pemanggilannya di SettingsScreen.kt.
 */
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
    // dragModeActive/onToggleDragMode DIPERTAHANKAN di signature (dialirkan
    // apa adanya dari SettingsScreen) untuk kompatibilitas pemanggil, TAPI
    // TIDAK DIPAKAI lagi di badan Composable ini — posisi X/Y sekarang
    // diatur LANGSUNG lewat [PositionJoystick] di kartu ini sendiri
    // (menyeret handle langsung memicu [onOffsetChange]), menggantikan alur
    // lama "aktifkan drag-mode lalu geser crosshair di overlay layar lain".
    dragModeActive: Boolean,
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CrosshairCardBg),
    ) {
        // Watermark logo transparan di pojok kiri-atas, meniru referensi —
        // ikon vector AetherX yang sudah ada, di-tint semi-transparan dan
        // diperbesar melebihi batas kartu (clipToBounds bawaan Box induk
        // yang membungkus ini menyembunyikan kelebihannya secara otomatis).
        Icon(
            painter = painterResource(id = com.aether.x.R.drawable.ic_aetherx_mark),
            contentDescription = null,
            tint = CrosshairAccent.copy(alpha = 0.10f),
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp)
                .size(96.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // Header: judul + subjudul di kiri, Switch besar oranye di kanan.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.crosshair_card_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.crosshair_card_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
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
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrosshairAccent,
                        uncheckedThumbColor = CrosshairAccentDim,
                        uncheckedTrackColor = CrosshairCardBgAlt,
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
                // Preview mini crosshair, tengah-atas.
                Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                    CrosshairPreview(
                        style = style,
                        color = Color(colorArgb.toInt()),
                        sizeDp = 18f,
                        thicknessDp = thicknessDp.toFloat().coerceAtMost(4f),
                        opacityPercent = opacityPercent,
                        modifier = Modifier.size(48.dp),
                    )
                }

                // Baris utama: list style (kiri) — joystick posisi (tengah)
                // — slider Size vertikal (kanan). Tinggi tetap 260dp supaya
                // joystick, list style, dan slider vertikal semuanya
                // punya ruang yang cukup dan sejajar.
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "X: $offsetX",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = "Y: $offsetY",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                            IconButton(onClick = onResetPosition) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.crosshair_position_reset_center),
                                    tint = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                        PositionJoystick(
                            offsetX = offsetX,
                            offsetY = offsetY,
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

                // Alpha (opacity) — slider horizontal ramping custom.
                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_opacity_label),
                    valueText = "$opacityPercent%",
                    value = opacityPercent.toFloat(),
                    range = 20f..100f,
                    onValueChange = { onOpacityChange(it.toInt()) },
                    modifier = Modifier.padding(top = 20.dp),
                )

                // Ketebalan tetap ada (tidak ada di referensi tapi tetap
                // fungsi penting) — dipertahankan sebagai slider horizontal
                // ramping yang sama, di bawah Alpha.
                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_thickness_label),
                    valueText = "${thicknessDp}dp",
                    value = thicknessDp.toFloat(),
                    range = 1f..12f,
                    onValueChange = { onThicknessChange(it.toInt()) },
                    modifier = Modifier.padding(top = 16.dp),
                )

                // Swatch warna besar rounded-square berjajar horizontal.
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
                }
            }
        }
    }
}

/** List vertikal scrollable berisi tombol icon-only tiap style crosshair — meniru list kiri di referensi. */
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
            .background(if (isSelected) CrosshairAccentDim else CrosshairCardBgAlt)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) CrosshairAccent else Color.Transparent,
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
            val drawColor = if (isSelected) CrosshairAccent else Color.White.copy(alpha = 0.6f)
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
            }
        }
    }
}

/**
 * Joystick radial untuk atur posisi X/Y crosshair — lingkaran besar dengan
 * handle segitiga di 4 arah + lingkaran tengah menampilkan derajat (murni
 * dekoratif, mengikuti referensi — bukan rotasi crosshair sungguhan, hanya
 * representasi visual arah geser terakhir). Menyeret di dalam area
 * lingkaran memicu [onOffsetChange] dengan delta X/Y baru.
 */
@Composable
private fun PositionJoystick(
    offsetX: Int,
    offsetY: Int,
    onOffsetChange: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var angleDeg by remember { mutableStateOf(0) }
    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            drawCircle(
                color = CrosshairCardBgAlt,
                radius = size.minDimension / 2f,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        // 4 handle panah arah, murni dekoratif (menandakan arah geser bisa
        // ke 4 sisi) — sesuai referensi yang menampilkan 4 segitiga oranye.
        Box(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = (offsetX + dragAmount.x.roundToInt()).coerceIn(-200, 200)
                        val newY = (offsetY + dragAmount.y.roundToInt()).coerceIn(-200, 200)
                        angleDeg = (Math.toDegrees(atan2(dragAmount.y.toDouble(), dragAmount.x.toDouble())).roundToInt() + 360) % 360
                        onOffsetChange(newX, newY)
                    }
                },
        ) {
            DirectionArrow(Alignment.TopCenter, rotationDeg = 0f)
            DirectionArrow(Alignment.BottomCenter, rotationDeg = 180f)
            DirectionArrow(Alignment.CenterStart, rotationDeg = 270f)
            DirectionArrow(Alignment.CenterEnd, rotationDeg = 90f)

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(CrosshairCardBg)
                    .border(1.dp, CrosshairAccentDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$angleDeg°",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrosshairAccent,
                )
            }
        }
    }
}

@Composable
private fun DirectionArrow(alignment: Alignment, rotationDeg: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = alignment) {
        Box(
            modifier = Modifier
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
                drawPath(path, color = CrosshairAccent)
            }
        }
    }
}

/** Slider vertikal ramping custom (thumb kecil oranye di atas track tipis) — dipakai untuk Size, meniru referensi. */
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
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = CrosshairAccent)
        Spacer(modifier = Modifier.height(8.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CrosshairCardBgAlt)
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
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height((fraction * 200).dp.coerceAtMost(200.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(CrosshairAccent),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (fraction * 200).dp.coerceAtMost(196.dp))
                    .size(width = 20.dp, height = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CrosshairAccent),
            )
        }
    }
}

/** Slider horizontal ramping custom (thumb kecil oranye di atas track tipis) — dipakai untuk Alpha & Ketebalan. */
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
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = CrosshairAccent)
        }
        Spacer(modifier = Modifier.height(10.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CrosshairCardBgAlt)
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
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CrosshairAccent),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (fraction * 280).dp.coerceAtMost(276.dp))
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CrosshairAccent),
            )
        }
    }
}

/** Swatch warna besar rounded-square, meniru baris warna di bagian bawah referensi. */
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
                color = if (selected) CrosshairAccent else Color.Transparent,
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

/**
 * BUG FIX (dipertahankan dari rework sebelumnya — "warna custom yang
 * penanda warna nya bug"): pilih tint check mark berdasarkan luminance
 * warna latar, supaya tetap kontras di warna terang maupun gelap.
 */
private fun contrastingCheckTint(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}
