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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * Pengaturan "Tampilan": mode terang/gelap, warna aksen, intensitas blur, dan level Liquid Glass.
 * Tipografi memakai SF Pro Text/Display secara konsisten. Semua perubahan tersimpan di DataStore
 * dan langsung diterapkan lewat AetherXTheme di MainActivity.
 */
@Composable
fun AppearanceSettingsSection(
    darkModePref: DarkModePref,
    accentPreset: AccentPreset,
    customAccentArgb: Long?,
    blurStrength: Int,
    liquidGlassLevel: Int,
    onDarkModeChange: (DarkModePref) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onCustomAccentChange: (Long) -> Unit,
    onBlurChange: (Int) -> Unit,
    onLiquidGlassLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = str(R.string.settings_section_appearance),
        watermarkIcon = Icons.Outlined.Palette,
        modifier = modifier,
    ) {
        SettingGroup(label = str(R.string.appearance_settings_mode)) {
            ChoiceRow {
                ChoicePill(str(R.string.settings_appearance_light), darkModePref == DarkModePref.LIGHT) { onDarkModeChange(DarkModePref.LIGHT) }
                ChoicePill(str(R.string.settings_appearance_dark), darkModePref == DarkModePref.DARK) { onDarkModeChange(DarkModePref.DARK) }
                ChoicePill(str(R.string.settings_appearance_system), darkModePref == DarkModePref.SYSTEM) { onDarkModeChange(DarkModePref.SYSTEM) }
            }
        }

        SettingGroup(label = str(R.string.appearance_settings_warna_aksen)) {
            val dark = LocalAetherPalette.current.isDark
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(AccentPreset.SAGE, AccentPreset.SKY, AccentPreset.LAVENDER, AccentPreset.ROSE, AccentPreset.AMBER).forEach { preset ->
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
                        Icon(Icons.Outlined.Check, contentDescription = str(R.string.appearance_settings_warna_aksen_kustom_aktif), tint = com.aether.x.ui.theme.onColorFor(customColor!!), modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = str(R.string.appearance_settings_pilih_warna_aksen_bebas), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
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
                text = if (customAccentArgb != null) str(R.string.appearance_settings_warna_kustom_aktif, customAccentArgb.toString(16).takeLast(6).uppercase(Locale.ROOT)) else str(R.string.appearance_settings_pilih_preset_atau_tekan_untuk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingGroup(label = "Blur Intensity") {
            LevelSlider(
                label = str(R.string.appearance_settings_kekuatan_blur),
                description = str(R.string.appearance_settings_mengatur_seberapa_kuat_konten_di),
                value = blurStrength,
                levelNames = listOf(str(R.string.appearance_settings_mati), str(R.string.appearance_settings_lembut), str(R.string.kernel_preset_balanced), str(R.string.appearance_settings_kuat), str(R.string.appearance_settings_ultra)),
                onValueChange = onBlurChange,
            )
        }

        SettingGroup(label = "Liquid Glass Level") {
            LevelSlider(
                label = str(R.string.appearance_settings_intensitas_kaca),
                description = str(R.string.appearance_settings_mengatur_transparansi_tint_warna_dan),
                value = liquidGlassLevel,
                levelNames = listOf(str(R.string.appearance_settings_jernih), str(R.string.appearance_settings_tipis), str(R.string.kernel_preset_balanced), str(R.string.appearance_settings_pekat), "Solid"),
                onValueChange = onLiquidGlassLevelChange,
            )
        }
    }
}


@Composable
private fun LevelSlider(
    label: String,
    description: String,
    value: Int,
    levelNames: List<String>,
    onValueChange: (Int) -> Unit,
) {
    val normalized = ((value.coerceIn(0, 100) + 12) / 25).coerceIn(0, 4) * 25
    var sliderValue by remember(value) { mutableFloatStateOf(normalized.toFloat()) }
    var lastApplied by remember(value) { mutableIntStateOf(normalized) }
    LaunchedEffect(value) {
        sliderValue = ((value.coerceIn(0, 100) + 12) / 25).coerceIn(0, 4).times(25).toFloat()
        lastApplied = sliderValue.toInt()
    }
    val level = (sliderValue.toInt() / 25).coerceIn(0, 4)
    TweakSlider(
        label = label,
        description = description,
        valueText = "",
        value = sliderValue,
        range = 0f..100f,
        steps = 3,
        onValueChange = { raw ->
            val snapped = (raw / 25f).roundToInt().coerceIn(0, 4) * 25
            sliderValue = snapped.toFloat()
            if (snapped != lastApplied) {
                lastApplied = snapped
                onValueChange(snapped)
            }
        },
        onValueChangeFinished = {
            val snapped = (sliderValue / 25f).roundToInt().coerceIn(0, 4) * 25
            sliderValue = snapped.toFloat()
            if (snapped != lastApplied) {
                lastApplied = snapped
                onValueChange(snapped)
            }
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        levelNames.forEachIndexed { index, name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (index == level) FontWeight.Bold else FontWeight.Normal,
                color = if (index == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingGroup(label: String, content: @Composable () -> Unit) {
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
internal fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
internal fun ChoicePill(
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
        title = { Text(str(R.string.appearance_settings_warna_aksen_bebas)) },
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
                        Text(str(R.string.appearance_settings_pratinjau_aksen), color = com.aether.x.ui.theme.onColorFor(previewColor), fontWeight = FontWeight.Bold)
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
                    label = { Text(str(R.string.appearance_settings_kode_warna_hex)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = str(R.string.appearance_settings_atur_komponen_rgb_secara_bebas),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TweakSlider(
                    label = str(R.string.appearance_settings_merah_r),
                    description = str(R.string.appearance_settings_komponen_merah_dari_0_sampai),
                    valueText = red.toString(),
                    value = red.toFloat(),
                    range = 0f..255f,
                    steps = 254,
                    onValueChange = { red = it.roundToInt().coerceIn(0, 255); updateHexFromRgb() },
                )
                TweakSlider(
                    label = str(R.string.appearance_settings_hijau_g),
                    description = str(R.string.appearance_settings_komponen_hijau_dari_0_sampai),
                    valueText = green.toString(),
                    value = green.toFloat(),
                    range = 0f..255f,
                    steps = 254,
                    onValueChange = { green = it.roundToInt().coerceIn(0, 255); updateHexFromRgb() },
                )
                TweakSlider(
                    label = str(R.string.appearance_settings_biru_b),
                    description = str(R.string.appearance_settings_komponen_biru_dari_0_sampai),
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
            ) { Text(str(R.string.crosshair_custom_color_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.membership_logout_cancel)) } },
    )
}
