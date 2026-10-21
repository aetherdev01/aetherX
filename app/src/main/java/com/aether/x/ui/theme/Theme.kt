package com.aether.x.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.aether.x.data.AccentPreset
import com.aether.x.data.AppFont
import com.aether.x.data.CardStyle
import com.aether.x.ui.components.LocalHapticEnabled
import com.aether.x.ui.components.LocalReduceMotionEnabled

/**
 * v3.6 — gaya visual yang bisa diatur user (Pengaturan -> Tampilan).
 * [blur] dinormalisasi 0f..1f dari slider 0..100.
 */
@Immutable
data class AetherStyle(
    val cardStyle: CardStyle = CardStyle.GLASS,
    val blur: Float = 0.6f,
)

val LocalAetherStyle = staticCompositionLocalOf { AetherStyle() }

/** Skema Material dibangun dari [AetherPalette] supaya komponen M3 bawaan ikut tema. */
private fun AetherPalette.toColorScheme(): ColorScheme {
    val base: ColorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = textPrimary,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = textPrimary,
        tertiary = premium,
        onTertiary = onAccent,
        tertiaryContainer = premiumContainer,
        onTertiaryContainer = textPrimary,
        background = bg,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        outline = stroke,
        outlineVariant = stroke,
        error = danger,
        onError = onDanger,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceRaised,
    )
}

// v3.5 — hierarki shape FUNGSIONAL: panel data lebih kotak, elemen interaktif
// (tombol) lebih bulat.
private val AetherXShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),  // badge/chip kecil
    small = RoundedCornerShape(16.dp),       // dropdown/menu item
    medium = RoundedCornerShape(22.dp),      // compact panels
    large = RoundedCornerShape(28.dp),       // buttons and interactive surfaces
    extraLarge = RoundedCornerShape(32.dp),  // dialog/bottom sheet
)

@Composable
fun AetherXTheme(
    darkTheme: Boolean = false,
    accent: AccentPreset = AccentPreset.SAGE,
    customAccentArgb: Long? = null,
    font: AppFont = AppFont.POPPINS,
    cardStyle: CardStyle = CardStyle.GLASS,
    blurStrength: Int = 25,
    hapticsEnabled: Boolean = true,
    reduceMotionEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = remember(darkTheme, accent, customAccentArgb) { aetherPalette(darkTheme, accent, customAccentArgb) }
    val colorScheme = remember(palette) { palette.toColorScheme() }
    val typography = remember(font) { aetherTypography(font.toFontFamily()) }
    val style = remember(cardStyle, blurStrength) {
        AetherStyle(cardStyle = cardStyle, blur = (blurStrength / 100f).coerceIn(0f, 1f))
    }

    CompositionLocalProvider(
        LocalAetherPalette provides palette,
        LocalAetherStyle provides style,
        LocalHapticEnabled provides hapticsEnabled,
        LocalReduceMotionEnabled provides reduceMotionEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AetherXShapes,
        ) {
            // Teks tanpa warna eksplisit wajib mengikuti tema aplikasi, bukan warna default hitam.
            // Ini mencegah teks gelap di tema gelap dan teks terang di tema terang.
            CompositionLocalProvider(
                LocalContentColor provides palette.textPrimary,
                content = content,
            )
        }
    }
}
