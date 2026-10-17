package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.data.AccentPreset
import com.aether.x.data.AppFont
import com.aether.x.data.CardStyle
import com.aether.x.data.DarkModePref
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.TweakSlider
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.theme.toFontFamily
import kotlin.math.roundToInt

/**
 * v3.6 — Pengaturan "Tampilan": mode terang/gelap, warna aksen, font,
 * gaya card, dan slider blur/kaca. Semua perubahan tersimpan di DataStore
 * dan langsung diterapkan lewat AetherXTheme di MainActivity.
 */
@Composable
fun AppearanceSettingsSection(
    darkModePref: DarkModePref,
    accentPreset: AccentPreset,
    appFont: AppFont,
    cardStyle: CardStyle,
    blurStrength: Int,
    onDarkModeChange: (DarkModePref) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onFontChange: (AppFont) -> Unit,
    onCardStyleChange: (CardStyle) -> Unit,
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
            ) {
                AccentPreset.entries.forEach { preset ->
                    AccentSwatch(
                        color = Color(if (dark) preset.darkArgb else preset.lightArgb),
                        selected = preset == accentPreset,
                        onClick = { onAccentChange(preset) },
                    )
                }
            }
        }

        SettingGroup(label = "Font") {
            ChoiceRow {
                AppFont.entries.forEach { font ->
                    ChoicePill(
                        label = font.label,
                        selected = font == appFont,
                        fontFamily = font.toFontFamily(),
                    ) { onFontChange(font) }
                }
            }
        }

        SettingGroup(label = "Gaya card") {
            ChoiceRow {
                CardStyle.entries.forEach { style ->
                    ChoicePill(style.label, style == cardStyle) { onCardStyleChange(style) }
                }
            }
        }

        var sliderValue by remember(blurStrength) { mutableFloatStateOf(blurStrength.toFloat()) }
        TweakSlider(
            label = "Blur",
            description = "Kekuatan blur sungguhan di dialog, drawer, dan navbar. 0 = blur mati.",
            valueText = "${sliderValue.roundToInt()}%",
            value = sliderValue,
            range = 0f..100f,
            steps = 0,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onBlurChange(sliderValue.roundToInt()) },
        )
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
