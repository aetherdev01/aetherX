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

// ----------------------------------------------------------------------------
// v3.8 — JAMINAN KONTRAS. Bug v3.6: teks di atas warna aksen dipilih dengan
// ambang luminance 0.45 (salah — putih baru kalah dari hitam di luminance
// ~0.18), jadi di tema gelap teks PUTIH muncul di atas aksen pastel (kontras
// 2.4-2.7) dan di tema terang aksen cerah (Coral/Amber/Mint) dipakai apa
// adanya sebagai teks di atas putih (kontras 3.1-3.6). Sekarang semua warna
// yang dipakai sebagai TEKS dipaksa >= 4.5:1 (WCAG AA) terhadap permukaan
// tempat ia berada, dan warna teks-di-atas-warna dipilih dari kontras nyata.
// ----------------------------------------------------------------------------

private val DarkInk = Color(0xFF1A1A1A)

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Teks/ikon terbaca di atas [bg]: putih atau hampir-hitam, mana yang kontrasnya lebih tinggi. */
fun onColorFor(bg: Color): Color =
    if (contrastRatio(Color.White, bg) >= contrastRatio(DarkInk, bg)) Color.White else DarkInk

/**
 * Menggeser warna ini (menggelapkan di tema terang, menerangkan di tema gelap)
 * secukupnya sampai kontrasnya >= [min] terhadap SEMUA [backgrounds].
 */
private fun Color.fit(backgrounds: List<Color>, min: Float, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    for (i in 0..40) {
        val c = lerp(this, target, i * 0.025f)
        if (backgrounds.all { contrastRatio(c, it) >= min }) return c
    }
    return target
}

fun aetherPalette(dark: Boolean, preset: AccentPreset): AetherPalette {
    val bg = if (dark) Color(0xFF0D0D11) else Color(0xFFF5F5F8)
    val surface = if (dark) Color(0xFF16161C) else Color(0xFFFFFFFF)
    val surfaceAlt = if (dark) Color(0xFF1C1C24) else Color(0xFFF0F0F4)
    val surfaceRaised = if (dark) Color(0xFF24242E) else Color(0xFFE9E9EF)
    val stroke = if (dark) Color(0xFF2E2E3A) else Color(0xFFDEDEE6)
    val textBackdrops = listOf(surface, bg, surfaceAlt, surfaceRaised)

    /** Warna + container tonalnya; warna dijamin terbaca sebagai teks di atas semua permukaan DAN containernya. */
    fun tone(base: Color, mix: Float): Pair<Color, Color> {
        var c = base.fit(textBackdrops, 4.5f, dark)
        var container = lerp(surface, c, mix)
        c = c.fit(textBackdrops + container, 4.5f, dark)
        container = lerp(surface, c, mix)
        return c to container
    }

    val (accent, accentContainer) = tone(Color(if (dark) preset.darkArgb else preset.lightArgb), if (dark) 0.22f else 0.12f)
    val (premium, premiumContainer) = tone(if (dark) Color(0xFFF2B84E) else Color(0xFFB7791F), if (dark) 0.22f else 0.14f)
    val (success, successContainer) = tone(if (dark) Color(0xFF5BE38A) else Color(0xFF2B8A3E), if (dark) 0.20f else 0.12f)
    val (warning, warningContainer) = tone(if (dark) Color(0xFFF2B84E) else Color(0xFFD9730D), if (dark) 0.20f else 0.14f)
    val (bonus, _) = tone(if (dark) Color(0xFFFF9A5C) else Color(0xFFE8590C), 0.14f)
    val (danger, _) = tone(if (dark) Color(0xFFFF6459) else Color(0xFFD62F2F), 0.14f)

    val textPrimary = if (dark) Color(0xFFF2F2F6) else Color(0xFF15151A)
    val textSecondary = (if (dark) Color(0xFFA4A4B2) else Color(0xFF55555F)).fit(textBackdrops, 5.5f, dark)
    val textMuted = (if (dark) Color(0xFF8A8A98) else Color(0xFF6B6B77)).fit(textBackdrops, 4.5f, dark)

    return AetherPalette(
        isDark = dark,
        bg = bg,
        surface = surface,
        surfaceAlt = surfaceAlt,
        surfaceRaised = surfaceRaised,
        stroke = stroke,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        accent = accent,
        accentSoft = lerp(accent, if (dark) Color.White else surface, 0.35f),
        accentContainer = accentContainer,
        onAccent = onColorFor(accent),
        premium = premium,
        premiumContainer = premiumContainer,
        success = success,
        successContainer = successContainer,
        warning = warning,
        warningContainer = warningContainer,
        danger = danger,
        onDanger = onColorFor(danger),
        bonus = bonus,
        onBonus = onColorFor(bonus),
    )
}

val LocalAetherPalette = staticCompositionLocalOf { aetherPalette(dark = false, preset = AccentPreset.GRAPHITE) }

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
