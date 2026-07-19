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
    primaryContainer = SurfaceCardAlt,
    onPrimaryContainer = AccentBlue,
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
    primary = Color(0xFFA85A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DCC9),
    onPrimaryContainer = Color(0xFF3D1F0A),
    secondary = Color(0xFFA85A2E),
    secondaryContainer = Color(0xFFF3DCC9),
    background = Color(0xFFFAF6F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EAE4),
    onBackground = Color(0xFF1C1817),
    onSurface = Color(0xFF1C1817),
    onSurfaceVariant = Color(0xFF54463C),
    outline = Color(0xFFDDD2C8),
)

private val AetherXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
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
