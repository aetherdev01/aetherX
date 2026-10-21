package com.aether.x.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aether.x.data.AppFont

/**
 * AetherX UI typeface.
 *
 * Use the platform sans-serif face rather than the old bundled "gf*" face,
 * whose internal family name is GeForce (not Poppins). This keeps the UI
 * clean and familiar on Android and avoids adding a large font asset.
 * The typography scale and tracking are tuned toward the restrained,
 * readable look commonly associated with SF Pro / MiSans.
 */
val AetherUiFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Backwards-compatible alias for code that may still reference PoppinsFamily.
 * Existing saved AppFont values are also intentionally mapped to the new UI
 * family so an upgrade does not keep displaying the previous GeForce face.
 */
@Deprecated("Use AetherUiFontFamily")
val PoppinsFamily: FontFamily = AetherUiFontFamily

/** Monospaced numerals remain reserved for technical readings (CPU/GPU/FPS). */
val AetherMonoFamily: FontFamily = FontFamily.Monospace

/**
 * Older font preferences are retained for DataStore compatibility, but all
 * regular app UI text now uses the unified SF/MiSans-inspired sans-serif face.
 * Technical numeric readouts can still explicitly use [AetherMonoFamily].
 */
fun AppFont.toFontFamily(): FontFamily = AetherUiFontFamily

/** Consistent typography hierarchy tuned for a modern, clean system UI. */
fun aetherTypography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.45).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.35).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.05.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.15.sp,
    ),
)
