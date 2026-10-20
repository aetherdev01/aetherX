package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.data.AccentPreset
import com.aether.x.data.DarkModePref
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.theme.LocalAetherPalette
import android.graphics.Color as AndroidColor
import java.util.Locale
import com.aether.x.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Pengaturan "Tampilan": mode terang/gelap, warna aksen, dan slider blur.
 * (v3.9: opsi font dan gaya card dihapus — tetap Poppins + kaca.) Semua perubahan tersimpan di DataStore
 * dan langsung diterapkan lewat AetherXTheme di MainActivity.
 */
@Composable
fun AppearanceSettingsSection(
    darkModePref: DarkModePref,
    accentPreset: AccentPreset,
    customAccentArgb: Long?,
    blurStrength: Int,
    onDarkModeChange: (DarkModePref) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onCustomAccentChange: (Long) -> Unit,
    onBlurChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Tampilan",
        watermarkIcon = Icons.Outlined.Palette,
        modifier = modifier,
    ) {
        SettingGroup(label = "Mode") {
            ChoiceRow {
                ChoicePill("Terang", darkModePref == DarkModePref.LIGHT) { onDarkModeChange(DarkModePref.LIGHT) }
                ChoicePill("Gelap", darkModePref == DarkModePref.DARK) { onDarkModeChange(DarkModePref.DARK) }
                ChoicePill("Ikuti Sistem", darkModePref == DarkModePref.SYSTEM) { onDarkModeChange(DarkModePref.SYSTEM) }
            }
        }

        SettingGroup(label = "Warna aksen") {
            val dark = LocalAetherPalette.current.isDark
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentPreset.entries.forEach { preset ->
                    AccentSwatch(
                        color = Color(if (dark) preset.darkArgb else preset.lightArgb),
                        selected = customAccentArgb == null && preset == accentPreset,
                        onClick = { onAccentChange(preset) },
                    )
                }
                var showCustomColor by remember { mutableStateOf(false) }
                val customColor = customAccentArgb?.let { Color(it and 0xFFFFFFFFL) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(customColor ?: MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (customAccentArgb != null) 2.5.dp else 1.dp,
                            color = if (customAccentArgb != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { showCustomColor = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (customAccentArgb != null) {
                        Icon(Icons.Outlined.Check, contentDescription = "Warna aksen kustom aktif", tint = com.aether.x.ui.theme.onColorFor(customColor!!), modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = "Pilih warna aksen bebas", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                    }
                }
                if (showCustomColor) {
                    CustomAccentPickerDialog(
                        initialColor = customColor ?: Color(if (dark) accentPreset.darkArgb else accentPreset.lightArgb),
                        onDismiss = { showCustomColor = false },
                        onApply = { argb ->
                            onCustomAccentChange(argb)
                            showCustomColor = false
                        },
                    )
                }
            }
            Text(
                text = if (customAccentArgb != null) "Warna kustom aktif · #${customAccentArgb.toString(16).takeLast(6).uppercase(Locale.ROOT)}" else "Pilih preset atau tekan + untuk menentukan warna sendiri.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingGroup(label = "Level Liquid Glass") {
            // Pertahankan nilai lokal saat drag supaya DataStore tidak membuat slider
            // tersendat; level deskriptif di bawahnya berubah secara langsung.
            var sliderValue by remember { mutableFloatStateOf(blurStrength.toFloat()) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(blurStrength) {
                if (!dragging) sliderValue = blurStrength.toFloat()
            }
            val liquidGlassLevel = when (sliderValue.roundToInt()) {
                in 0..12 -> "Mati"
                in 13..37 -> "Lembut"
                in 38..62 -> "Seimbang"
                in 63..87 -> "Kuat"
                else -> "Ultra"
            }
            TweakSlider(
                label = "Intensitas",
                description = "Geser untuk mengatur efek kaca dan blur pada card, dialog, drawer, serta navbar.",
                valueText = "${sliderValue.roundToInt()}%",
                value = sliderValue,
                range = 0f..100f,
                steps = 0,
                onValueChange = {
                    // Render nilai lokal selama drag agar animasi tetap mulus;
                    // simpan hanya sekali saat jari dilepas, bukan setiap pixel.
                    dragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onBlurChange(sliderValue.roundToInt())
                },
            )
            Text(
                text = "Level Liquid Glass → $liquidGlassLevel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SettingGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) primary else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = fontFamily,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 0.dp,
                color = if (selected) ring else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = com.aether.x.ui.theme.onColorFor(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


@Composable
private fun CustomAccentPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val previewColor = Color(
        (AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong()) and 0xFFFFFFFFL,
    )
    val previewHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and previewColor.toArgb())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warna aksen bebas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = previewColor,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("Pratinjau aksen", color = com.aether.x.ui.theme.onColorFor(previewColor), fontWeight = FontWeight.Bold)
                        Text(previewHex, color = com.aether.x.ui.theme.onColorFor(previewColor), style = MaterialTheme.typography.labelMedium)
                    }
                }
                TweakSlider(
                    label = "Hue / warna",
                    description = "Pilih rona warna.",
                    valueText = "${hue.roundToInt()}°",
                    value = hue,
                    range = 0f..360f,
                    steps = 0,
                    onValueChange = { hue = it },
                )
                TweakSlider(
                    label = "Saturasi",
                    description = "Geser dari abu-abu menuju warna penuh.",
                    valueText = "${(saturation * 100f).roundToInt()}%",
                    value = saturation * 100f,
                    range = 0f..100f,
                    steps = 0,
                    onValueChange = { saturation = (it / 100f).coerceIn(0f, 1f) },
                )
                TweakSlider(
                    label = "Kecerahan",
                    description = "Atur seberapa terang warna aksen.",
                    valueText = "${(brightness * 100f).roundToInt()}%",
                    value = brightness * 100f,
                    range = 0f..100f,
                    steps = 0,
                    onValueChange = { brightness = (it / 100f).coerceIn(0f, 1f) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFFL)
            }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
