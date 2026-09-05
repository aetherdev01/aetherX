package com.aether.x.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aether.x.R

val PoppinsFamily = FontFamily(
    Font(R.font.gfthin, FontWeight.Thin),
    Font(R.font.gfextralight, FontWeight.ExtraLight),
    Font(R.font.gflight, FontWeight.Light),
    Font(R.font.gfregular, FontWeight.Normal),
    Font(R.font.gfmedium, FontWeight.Medium),
    Font(R.font.gfsemibold, FontWeight.SemiBold),
    Font(R.font.gfbold, FontWeight.Bold),
    Font(R.font.gfextrabold, FontWeight.ExtraBold),
    Font(R.font.gfblack, FontWeight.Black),
)

/**
 * v3.5 — dipakai KHUSUS untuk pembacaan angka teknis yang sering berubah:
 * CPU/GPU %, frekuensi MHz, suhu, FPS, RAM. Ini bukan hiasan — app ini
 * secara harfiah adalah instrumen pembacaan nilai kernel/sysfs, dan angka
 * di font tabular (lebar digit konsisten) terasa jauh lebih "stabil" saat
 * update cepat dibanding proportional font yang lebar tiap digitnya beda-
 * beda (angka terlihat "bergeser" tiap refresh). Pakai
 * `FontFamily.Monospace` bawaan sistem — TIDAK butuh file font tambahan
 * (aman dari batasan aset font kustom), tersedia di semua perangkat
 * Android. JANGAN dipakai untuk body text/label biasa — hanya untuk nilai
 * numerik instrumen.
 */
val AetherMonoFamily = FontFamily.Monospace

val AetherXTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    ),
)
