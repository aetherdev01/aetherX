package com.aether.x.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import com.aether.x.data.AppFontStyle
import com.aether.x.data.CardStylePref
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.LocalHazeStyle

// v3.5 — skema warna dibangun dari token "Aether" baru (lihat Color.kt untuk
// filosofi lengkapnya). primary/secondary/tertiary dipetakan dengan makna
// yang KONSISTEN di seluruh app: primary=cyan (performa/aktif),
// secondary=violet (premium/membership), tertiary=amber (peringatan).
private val AetherXDarkScheme = darkColorScheme(
    primary = AetherCyan,
    onPrimary = OnAetherCyan,
    primaryContainer = AetherCyanContainer,
    onPrimaryContainer = AetherCyanSoft,
    secondary = AetherViolet,
    onSecondary = OnAetherCyan,
    secondaryContainer = AetherVioletContainer,
    onSecondaryContainer = OnAetherVioletContainer,
    tertiary = AetherAmber,
    tertiaryContainer = AetherAmberContainer,
    onTertiaryContainer = OnAetherAmberContainer,
    background = VoidBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = StrokeSubtle,
    outlineVariant = StrokeSubtle,
    error = AetherRed,
    onError = OnAetherRed,
)

private val AetherXLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = OnLightPrimaryContainer,
    secondary = LightSecondary,
    secondaryContainer = LightSecondaryContainer,
    background = LightBg,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
)

// v3.5 — hierarki shape FUNGSIONAL (sebelumnya: satu radius besar (24dp)
// dipakai rata di semua kartu/dialog/tombol tanpa pembeda — ciri khas
// "SaaS-card kit" generik). Sekarang radius berbeda MENURUT PERAN elemen:
// panel data (SectionCard) sengaja LEBIH KOTAK — kesan "panel instrumen",
// sementara elemen interaktif (tombol) sengaja LEBIH BULAT — kesan
// "tactile, jelas bisa disentuh". Hierarki ini yang menciptakan makna,
// bukan sekadar variasi angka.
private val AetherXShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),  // badge/chip kecil
    small = RoundedCornerShape(16.dp),       // dropdown/menu item
    medium = RoundedCornerShape(22.dp),      // compact panels
    large = RoundedCornerShape(28.dp),       // buttons and interactive surfaces
    extraLarge = RoundedCornerShape(32.dp),  // dialog/bottom sheet
)

val LocalCardStyle = staticCompositionLocalOf { CardStylePref.GLASS }
val LocalGlassIntensity = staticCompositionLocalOf { 0.45f }

@Composable
fun AetherXTheme(
    darkTheme: Boolean = false,
    fontStyle: AppFontStyle = AppFontStyle.DEFAULT,
    cardStyle: CardStylePref = CardStylePref.GLASS,
    glassIntensity: Float = 0.45f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AetherXDarkScheme else AetherXLightScheme

    val selectedFamily = when (fontStyle) {
        AppFontStyle.DEFAULT -> PoppinsFamily
        AppFontStyle.SERIF -> FontFamily.Serif
        AppFontStyle.MONO -> FontFamily.Monospace
    }
    val baseTypography = AetherXTypography
    val selectedTypography = baseTypography.copy(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = selectedFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = selectedFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = selectedFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = selectedFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = selectedFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = selectedFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = selectedFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = selectedFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = selectedFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = selectedFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = selectedFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = selectedFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = selectedFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = selectedFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = selectedFamily),
    )
    val normalizedGlassIntensity = glassIntensity.coerceIn(0f, 1f)
    CompositionLocalProvider(
        LocalCardStyle provides cardStyle,
        LocalGlassIntensity provides normalizedGlassIntensity,
        LocalHazeStyle provides HazeStyle(
            backgroundColor = colorScheme.surface.copy(alpha = 0.62f - normalizedGlassIntensity * 0.24f),
            blurRadius = (normalizedGlassIntensity * 30f).dp,
            noiseFactor = 0.04f,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = selectedTypography,
            shapes = AetherXShapes,
            content = content,
        )
    }
}
