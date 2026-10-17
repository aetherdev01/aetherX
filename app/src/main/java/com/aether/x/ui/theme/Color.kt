package com.aether.x.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.aether.x.data.AccentPreset

// ============================================================================
// v3.6 — SISTEM WARNA BERBASIS PALET (terang/gelap + preset aksen).
//
// Sebelumnya semua warna adalah konstanta gelap yang di-hardcode. Sekarang
// satu sumber kebenaran: [AetherPalette], dibangun oleh [aetherPalette]
// dari (mode terang/gelap, preset aksen) dan dibagikan lewat
// [LocalAetherPalette] oleh AetherXTheme.
//
// Nama token LAMA (AccentBlue, TextMuted, SurfaceRaised, dst.) SENGAJA
// dipertahankan sebagai properti @Composable di bagian bawah file ini,
// supaya 13+ layar yang meng-import-nya langsung otomatis ikut tema tanpa
// diubah satu-satu. Konsekuensinya: token lama HANYA boleh dibaca di
// konteks @Composable. Di dalam lambda draw/remember/onClick, ambil nilainya
// ke variabel lokal dulu (`val accent = AccentBlue`) lalu pakai variabelnya.
// ============================================================================

@Immutable
data class AetherPalette(
    val isDark: Boolean,
    // Permukaan
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceRaised: Color,
    val stroke: Color,
    // Teks
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Aksen utama (mengikuti preset pilihan user)
    val accent: Color,
    val accentSoft: Color,
    val accentContainer: Color,
    val onAccent: Color,
    // Premium/membership — emas, supaya tidak bentrok dengan aksen utama
    val premium: Color,
    val premiumContainer: Color,
    // Status
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val onDanger: Color,
    // Bonus harian
    val bonus: Color,
    val onBonus: Color,
)

fun aetherPalette(dark: Boolean, preset: AccentPreset): AetherPalette {
    val accent = Color(if (dark) preset.darkArgb else preset.lightArgb)

    val bg = if (dark) Color(0xFF0D0D11) else Color(0xFFF5F5F8)
    val surface = if (dark) Color(0xFF16161C) else Color(0xFFFFFFFF)
    val surfaceAlt = if (dark) Color(0xFF1C1C24) else Color(0xFFF0F0F4)
    val surfaceRaised = if (dark) Color(0xFF24242E) else Color(0xFFE9E9EF)
    val stroke = if (dark) Color(0xFF2E2E3A) else Color(0xFFDEDEE6)

    val premium = if (dark) Color(0xFFF2B84E) else Color(0xFFB7791F)
    val success = if (dark) Color(0xFF5BE38A) else Color(0xFF2B8A3E)
    val warning = if (dark) Color(0xFFF2B84E) else Color(0xFFD9730D)
    val bonus = if (dark) Color(0xFFFF9A5C) else Color(0xFFE8590C)

    return AetherPalette(
        isDark = dark,
        bg = bg,
        surface = surface,
        surfaceAlt = surfaceAlt,
        surfaceRaised = surfaceRaised,
        stroke = stroke,
        textPrimary = if (dark) Color(0xFFF2F2F6) else Color(0xFF15151A),
        textSecondary = if (dark) Color(0xFFA4A4B2) else Color(0xFF55555F),
        textMuted = if (dark) Color(0xFF6C6C7A) else Color(0xFF7B7B87),
        accent = accent,
        accentSoft = lerp(accent, if (dark) Color.White else surface, 0.35f),
        accentContainer = lerp(surface, accent, if (dark) 0.22f else 0.12f),
        // Aksen terang (mis. Amber/Mint gelap) butuh teks gelap, sisanya putih.
        onAccent = if (accent.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White,
        premium = premium,
        premiumContainer = lerp(surface, premium, if (dark) 0.22f else 0.14f),
        success = success,
        successContainer = lerp(surface, success, if (dark) 0.20f else 0.12f),
        warning = warning,
        warningContainer = lerp(surface, warning, if (dark) 0.20f else 0.14f),
        danger = if (dark) Color(0xFFFF6459) else Color(0xFFD62F2F),
        onDanger = if (dark) Color(0xFF2B0704) else Color.White,
        bonus = bonus,
        onBonus = if (dark) Color(0xFF2A1405) else Color.White,
    )
}

val LocalAetherPalette = staticCompositionLocalOf { aetherPalette(dark = false, preset = AccentPreset.ORCHID) }

// --- Token LAMA -> palet aktif (hanya boleh dibaca di konteks @Composable) ---

val VoidBg: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.bg
val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.surface
val SurfaceCardAlt: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.surfaceAlt
val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.surfaceRaised
val StrokeSubtle: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.stroke

val AetherCyan: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accent
val AetherCyanSoft: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentSoft
val AetherCyanContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentContainer
val OnAetherCyan: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.onAccent

val AetherViolet: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.premium
val AetherVioletContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.premiumContainer
val OnAetherVioletContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textPrimary

val AetherRed: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.danger
val OnAetherRed: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.onDanger
val AetherAmber: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.warning
val AetherAmberContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.warningContainer
val OnAetherAmberContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textPrimary

val AetherBlue: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accent
val AetherBlueContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentContainer
val AetherGreen: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.success
val AetherGreenContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.successContainer
val AetherPink: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accent
val AetherPinkContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentContainer
val AetherOrange: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.bonus
val AetherOrangeContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.warningContainer

val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textMuted

// --- Alias kompatibilitas mundur (nama lama -> token palet) -----------------

val BgVoid: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.bg
val BgBase: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.bg
val TextOnCard: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.textPrimary

val AccentBlue: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accent
val AccentBlueSoft: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentSoft
val AccentBlueDim: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentContainer
val OnAccentBlue: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.onAccent
val AccentGreenIconContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accentContainer

val AccentGreen: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.success
val AccentGreenContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.successContainer
val AccentRed: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.danger
val AccentAmber: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.warning
val AccentAmberContainer: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.warningContainer
val AccentPurple: Color @Composable @ReadOnlyComposable get() = LocalAetherPalette.current.accent
