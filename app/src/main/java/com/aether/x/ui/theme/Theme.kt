package com.aether.x.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    extraSmall = RoundedCornerShape(8.dp),   // badge/chip kecil
    small = RoundedCornerShape(10.dp),       // dropdown/menu item
    medium = RoundedCornerShape(14.dp),      // panel data (SectionCard dkk)
    large = RoundedCornerShape(20.dp),       // tombol & elemen interaktif
    extraLarge = RoundedCornerShape(26.dp),  // dialog/bottom sheet
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
