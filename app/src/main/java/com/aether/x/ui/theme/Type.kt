package com.aether.x.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aether.x.R
import com.aether.x.data.AppFont

/** SF Pro Text is used for body copy, controls, labels, and compact UI. */
val SFProTextFamily = FontFamily(
    Font(R.font.sf_pro_text_light, FontWeight.Light),
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_bold, FontWeight.Bold),
    Font(R.font.sf_pro_text_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_text_heavy, FontWeight.Black),
)

/** SF Pro Display is used for larger headings and title hierarchy. */
val SFProDisplayFamily = FontFamily(
    Font(R.font.sf_pro_display_ultralight, FontWeight.ExtraLight),
    Font(R.font.sf_pro_display_thin, FontWeight.Thin),
    Font(R.font.sf_pro_display_light, FontWeight.Light),
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_display_black, FontWeight.Black),
)

/**
 * Kept as a compatibility alias for existing technical-value call sites.
 * All app text now uses the supplied SF Pro family for consistent typography.
 */
val AetherMonoFamily = SFProTextFamily
val PoppinsFamily = SFProTextFamily

/** All font choices resolve to SF Pro so typography stays consistent throughout the app. */
fun AppFont.toFontFamily(): FontFamily = SFProTextFamily

/** Global Material typography: SF Pro Display for headings, SF Pro Text for other UI. */
fun aetherTypography(): Typography = Typography(
    displayLarge = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = SFProDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = SFProTextFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.1.sp),
)
