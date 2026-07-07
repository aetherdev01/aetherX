package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.components.CrosshairPreview
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.components.TweakSwitch

private val crosshairColorPalette = listOf(
    0xFF00FF66L, // hijau — klasik game booster
    0xFFFF3B30L, // merah
    0xFF00E5FFL, // cyan
    0xFFFFD60AL, // kuning
    0xFFFF2D95L, // magenta
    0xFFFFFFFFL, // putih
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
 * REWORK TAMPILAN (lihat perintah rework — "rework tampilan section
 * Crosshair"): sebelumnya semua kategori (style, warna, size/thickness/
 * opacity, posisi) ditumpuk datar dalam satu Column tanpa pemisah visual
 * yang jelas selain spacing. Sekarang tiap kategori dibungkus
 * [CrosshairSubGroup] — sub-card berlatar `surfaceVariant` tipis dengan
 * judul + ikon kecil di puncaknya — supaya batas antar kategori terlihat
 * jelas sekilas pandang tanpa perlu menambah SectionCard baru bertumpuk
 * (composable ini SUDAH dibungkus satu SectionCard besar di
 * SettingsScreen, lihat pemanggilnya).
 */
@Composable
fun CrosshairSettingsSection(
    enabled: Boolean,
    style: CrosshairStyle,
    colorArgb: Long,
    sizeDp: Int,
    thicknessDp: Int,
    opacityPercent: Int,
    overlayPermissionGranted: Boolean,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TweakSwitch(
            label = stringResource(R.string.crosshair_enable),
            description = stringResource(R.string.crosshair_enable_desc),
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked && !overlayPermissionGranted) {
                    onRequestOverlayPermission()
                } else {
                    onEnabledChange(checked)
                }
            },
        )

        if (!overlayPermissionGranted) {
            Text(
                text = stringResource(R.string.crosshair_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (enabled) {
            CrosshairSubGroup(title = stringResource(R.string.crosshair_style_label)) {
                StyleGrid(selected = style, onSelect = onStyleChange)
            }

            CrosshairSubGroup(title = stringResource(R.string.crosshair_color_label)) {
                var showCustomColorPicker by remember { mutableStateOf(false) }
                // isCustomColor: true kalau warna aktif TIDAK ada di 6 preset
                // — dipakai untuk menandai swatch "Custom" sebagai terpilih
                // (ring accent) alih-alih tidak ada satu pun swatch yang
                // ter-highlight, yang akan terlihat seperti bug/warna hilang.
                val isCustomColor = crosshairColorPalette.none { it == colorArgb }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    crosshairColorPalette.forEach { swatch ->
                        ColorSwatch(
                            color = swatch,
                            selected = swatch == colorArgb,
                            onClick = { onColorChange(swatch) },
                        )
                    }
                    // Swatch "Custom" (FITUR BARU — lihat perintah rework:
                    // "opsi warna untuk sesuai selera sendiri pakai picker"):
                    // lingkaran roda-warna sebagai afforadance visual "pilih
                    // warna apa saja", membuka CrosshairColorPickerDialog
                    // (HSV picker penuh) saat diketuk.
                    CustomColorSwatch(
                        selected = isCustomColor,
                        currentColor = if (isCustomColor) Color(colorArgb.toInt()) else null,
                        onClick = { showCustomColorPicker = true },
                    )
                }
                if (showCustomColorPicker) {
                    CrosshairColorPickerDialog(
                        initialColorArgb = colorArgb,
                        onDismiss = { showCustomColorPicker = false },
                        onColorConfirmed = { newColor ->
                            onColorChange(newColor)
                            showCustomColorPicker = false
                        },
                    )
                }
            }

            CrosshairSubGroup(title = stringResource(R.string.crosshair_dimension_group_label)) {
                TweakSlider(
                    label = stringResource(R.string.crosshair_size_label),
                    description = stringResource(R.string.crosshair_size_desc),
                    valueText = "${sizeDp}dp",
                    value = sizeDp.toFloat(),
                    range = 12f..80f,
                    steps = 16,
                    onValueChange = { onSizeChange(it.toInt()) },
                )
                TweakSlider(
                    label = stringResource(R.string.crosshair_thickness_label),
                    description = stringResource(R.string.crosshair_thickness_desc),
                    valueText = "${thicknessDp}dp",
                    value = thicknessDp.toFloat(),
                    range = 1f..12f,
                    steps = 10,
                    onValueChange = { onThicknessChange(it.toInt()) },
                )
                TweakSlider(
                    label = stringResource(R.string.crosshair_opacity_label),
                    description = stringResource(R.string.crosshair_opacity_desc),
                    valueText = "$opacityPercent%",
                    value = opacityPercent.toFloat(),
                    range = 20f..100f,
                    steps = 7,
                    onValueChange = { onOpacityChange(it.toInt()) },
                )
            }

            CrosshairSubGroup(title = stringResource(R.string.crosshair_position_label)) {
                Text(
                    text = stringResource(R.string.crosshair_position_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onToggleDragMode(!dragModeActive) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (dragModeActive) {
                                stringResource(R.string.crosshair_position_done)
                            } else {
                                stringResource(R.string.crosshair_position_start)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onResetPosition,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.crosshair_position_reset_center),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (dragModeActive) {
                    Text(
                        text = stringResource(R.string.crosshair_position_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Sub-card kategori di dalam section Crosshair: judul kecil + latar
 * `surfaceVariant` tipis membedakan tiap kelompok (Bentuk, Warna, Ukuran &
 * Ketebalan, Posisi) secara visual — REWORK dari sebelumnya yang cuma
 * `Text` judul + `Column`/`Row` konten langsung tanpa pembungkus, membuat
 * batas antar kategori kurang terlihat pada layar penuh toggle/slider.
 */
@Composable
private fun CrosshairSubGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun StyleGrid(selected: CrosshairStyle, onSelect: (CrosshairStyle) -> Unit) {
    val rows = styleOptions.chunked(3)
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    StyleOptionChip(
                        label = stringResource(option.labelRes),
                        style = option.style,
                        isSelected = option.style == selected,
                        onClick = { onSelect(option.style) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleOptionChip(
    label: String,
    style: CrosshairStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CrosshairPreview(
            style = style,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            sizeDp = 16f,
            thicknessDp = 2.5f,
            opacityPercent = 100,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ColorSwatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = contrastingCheckTint(Color(color)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * BUG FIX (lihat perintah rework — "warna custom yang penanda warna nya
 * bug"): ikon check di swatch custom SEBELUMNYA selalu `tint = Color.White`
 * — kalau pengguna memilih warna custom yang terang (kuning, putih, cyan
 * muda, dst.), ikon check jadi nyaris tak terlihat karena kontrasnya
 * rendah. Fungsi ini menghitung luminance relatif warna latar (rumus
 * standar ITU-R BT.709) dan memilih tint hitam untuk latar terang, putih
 * untuk latar gelap — dipakai konsisten di [ColorSwatch] (6 preset) maupun
 * [CustomColorSwatch] (hasil color picker), menggantikan pengecekan
 * `color == 0xFFFFFFFFL` yang sebelumnya HANYA menangani kasus putih murni,
 * bukan warna terang lain seperti kuning (`0xFFFFD60A`) atau cyan
 * (`0xFF00E5FF`) yang sama-sama butuh check mark gelap supaya terlihat.
 */
private fun contrastingCheckTint(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

/**
 * Swatch "Custom" (FITUR BARU) di ujung baris warna preset — lingkaran
 * roda-warna (hue wheel sederhana lewat sweepGradient) sebagai affordance
 * "pilih warna apa saja", membuka [CrosshairColorPickerDialog] saat diketuk.
 * Kalau warna aktif saat ini adalah hasil custom picker (bukan salah satu
 * dari 6 preset), swatch ini menampilkan warna tersebut dan ring accent
 * terpilih — swatch preset lain otomatis tidak ada yang ter-highlight.
 */
@Composable
private fun CustomColorSwatch(
    selected: Boolean,
    currentColor: Color?,
    onClick: () -> Unit,
) {
    val wheelBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
            ),
        )
    }
    val baseModifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
    val coloredModifier = if (currentColor != null) {
        baseModifier.background(currentColor)
    } else {
        baseModifier.background(wheelBrush)
    }
    Box(
        modifier = coloredModifier
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (currentColor == null) {
            // Latar masih wheelBrush (belum pernah pilih warna custom) —
            // tint putih di sini AMAN karena hue wheel selalu campuran warna
            // gelap-terang yang cukup kontras terhadap putih di titik mana
            // pun ikon Palette diletakkan (di tengah lingkaran).
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = stringResource(R.string.crosshair_custom_color_title),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        } else if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = contrastingCheckTint(currentColor),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
