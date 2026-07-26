package com.aether.x.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AetherXDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OnAccentBlue,
    primaryContainer = AccentGreenIconContainer,
    onPrimaryContainer = AccentBlueSoft,
    secondary = AccentBlue,
    onSecondary = OnAccentBlue,
    secondaryContainer = AccentBlueDim,
    onSecondaryContainer = AccentBlue,
    tertiary = AccentBlueSoft,
    tertiaryContainer = SurfaceRaised,
    onTertiaryContainer = AccentBlueSoft,
    background = BgBase,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextOnCard,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = StrokeSubtle,
    outlineVariant = StrokeSubtle,
    error = AccentRed,
    onError = Color(0xFF2B0704),
)

private val AetherXLightScheme = lightColorScheme(
    primary = Color(0xFF4A5E2D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBF6B3),
    onPrimaryContainer = Color(0xFF1C2410),
    secondary = Color(0xFF4A5E2D),
    secondaryContainer = Color(0xFFDBF6B3),
    background = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0EC),
    onBackground = Color(0xFF17170F),
    onSurface = Color(0xFF17170F),
    onSurfaceVariant = Color(0xFF54544C),
    outline = Color(0xFFDCDCD4),
)

private val AetherXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AetherXTheme(

    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AetherXDarkScheme else AetherXLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AetherXTypography,
        shapes = AetherXShapes,
        content = content,
    )
}
