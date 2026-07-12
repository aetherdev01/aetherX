package com.aether.x.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Skema gelap kustom AetherX — dasar hitam kecoklatan + aksen terracotta
// pucat, dipakai sebagai default (bukan lagi Material You bawaan Android).
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

// Skema terang tetap disediakan (fallback), tapi referensi desain fokus ke dark.
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

/**
 * Tema utama AetherX.
 *
 * UI direwrite total mengikuti referensi desain: dasar gelap pekat + aksen
 * biru pucat, tipografi tebal, kartu besar dengan sudut membulat. Dynamic
 * color (Material You) telah dihapus sepenuhnya supaya tampilan konsisten
 * dengan identitas AetherX dan tidak mengikuti wallpaper sistem.
 */
@Composable
fun AetherXTheme(
    // Tema bawaan aplikasi SELALU gelap, apa pun setelan sistem (light/dark)
    // di HP pengguna — sebelumnya memakai isSystemInDarkTheme(), jadi kalau
    // HP disetel mode terang, aplikasi ikut terbuka terang meski seluruh
    // desain (warna, kontras, ikon) dibuat untuk skema gelap. Parameter
    // darkTheme tetap ada (dipertahankan default true) supaya masih bisa
    // dioverride manual dari pemanggil kalau suatu saat mau ditambah toggle
    // tema di halaman Pengaturan.
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
