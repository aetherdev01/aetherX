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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.aether.x.data.AccentPreset
import com.aether.x.data.DarkModePref
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.glassPanel
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
            // Render nilai lokal seketika. Nilai disimpan dengan debounce pendek
            // saat jari masih menggeser, jadi Liquid Glass/blur langsung berubah
            // sebelum slider dilepas tanpa menulis DataStore untuk setiap pixel.
            var sliderValue by remember { mutableFloatStateOf(blurStrength.toFloat()) }
            var dragging by remember { mutableStateOf(false) }
            var lastAppliedBlur by remember { mutableIntStateOf(blurStrength) }
            val latestSliderValue by rememberUpdatedState(sliderValue)
            val latestBlurCallback by rememberUpdatedState(onBlurChange)
            LaunchedEffect(blurStrength) {
                if (!dragging) {
                    sliderValue = blurStrength.toFloat()
                    lastAppliedBlur = blurStrength
                }
            }
            // Debounce saat slider digeser. Jangan menulis ke DataStore setiap 75 ms:
            // setiap penulisan membangun ulang tema dan seluruh efek Haze, yang bisa
            // membuat perangkat tertentu crash ketika radius blur terus berubah.
            // Jika pengguna berhenti sebentar, preview diterapkan; nilai akhir tetap
            // disimpan langsung ketika slider dilepas.
            LaunchedEffect(dragging, sliderValue) {
                if (!dragging) return@LaunchedEffect
                delay(250)
                val target = latestSliderValue.roundToInt().coerceIn(0, 100)
                if (target != lastAppliedBlur) {
                    latestBlurCallback(target)
                    lastAppliedBlur = target
                }
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
                    dragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    val finalValue = sliderValue.roundToInt()
                    if (finalValue != lastAppliedBlur) onBlurChange(finalValue)
                    lastAppliedBlur = finalValue
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
            .aetherCardSurface(shape = shape)
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
    var red by remember(initialColor) { mutableIntStateOf((initialColor.red * 255f).roundToInt().coerceIn(0, 255)) }
    var green by remember(initialColor) { mutableIntStateOf((initialColor.green * 255f).roundToInt().coerceIn(0, 255)) }
    var blue by remember(initialColor) { mutableIntStateOf((initialColor.blue * 255f).roundToInt().coerceIn(0, 255)) }
    var hexText by remember(initialColor) {
        mutableStateOf(String.format(Locale.ROOT, "%06X", 0xFFFFFF and initialColor.toArgb()))
    }
    val previewArgb = AndroidColor.rgb(red, green, blue)
    val previewColor = Color(previewArgb.toLong() and 0xFFFFFFFFL)
    val previewHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and previewArgb)

    fun updateHexFromRgb() {
        hexText = String.format(Locale.ROOT, "%06X", 0xFFFFFF and AndroidColor.rgb(red, green, blue))
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassPanel(RoundedCornerShape(28.dp)),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text("Warna aksen bebas") },
        text = {
            Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
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
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        val clean = raw.removePrefix("#").filter { it.digitToIntOrNull(16) != null }.take(6).uppercase(Locale.ROOT)
                        hexText = clean
                        if (clean.length == 6) {
                            runCatching { AndroidColor.parseColor("#$clean") }.getOrNull()?.let { parsed ->
                                red = AndroidColor.red(parsed)
                                green = AndroidColor.green(parsed)
                                blue = AndroidColor.blue(parsed)
                            }
                        }
                    },
                    label = { Text("Kode warna HEX") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Atur komponen RGB secara bebas atau masukkan kode HEX.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TweakSlider(
                    label = "Merah (R)",
                    description = "Komponen merah dari 0 sampai 255.",
                    valueText = red.toString(),
                    value = red.toFloat(),
                    range = 0f..255f,
                    steps = 254,
                    onValueChange = { red = it.roundToInt().coerceIn(0, 255); updateHexFromRgb() },
                )
                TweakSlider(
                    label = "Hijau (G)",
                    description = "Komponen hijau dari 0 sampai 255.",
                    valueText = green.toString(),
                    value = green.toFloat(),
                    range = 0f..255f,
                    steps = 254,
                    onValueChange = { green = it.roundToInt().coerceIn(0, 255); updateHexFromRgb() },
                )
                TweakSlider(
                    label = "Biru (B)",
                    description = "Komponen biru dari 0 sampai 255.",
                    valueText = blue.toString(),
                    value = blue.toFloat(),
                    range = 0f..255f,
                    steps = 254,
                    onValueChange = { blue = it.roundToInt().coerceIn(0, 255); updateHexFromRgb() },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = hexText.length == 6,
                onClick = {
                    onApply(AndroidColor.rgb(red, green, blue).toLong() and 0xFFFFFFFFL)
                },
            ) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
