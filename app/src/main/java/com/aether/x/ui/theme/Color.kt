package com.aether.x.ui.theme

import androidx.compose.ui.graphics.Color

// === AetherX Dark UI ===
// Palet gelap "hacker/tactical" — biru-abu dingin di atas hitam pekat,
// bukan lagi M3 default. Referensi: dashboard gelap dengan aksen biru pucat.

// Background & surface
val BgVoid = Color(0xFF0A0A0C)            // background paling belakang (hampir hitam)
val BgBase = Color(0xFF0D0D10)            // background dasar layar
val SurfaceCard = Color(0xFF17171C)       // kartu section utama
val SurfaceCardAlt = Color(0xFF1C1C22)    // kartu bertingkat / hero card
val SurfaceRaised = Color(0xFF222229)     // elemen di atas kartu (track switch off, dsb)
val StrokeSubtle = Color(0xFF2A2A32)      // border/divider halus

// Aksen biru (primary)
val AccentBlue = Color(0xFF7FA8FF)        // biru pucat khas referensi (judul, ikon aktif)
val AccentBlueSoft = Color(0xFFAFC6FF)    // biru lebih muda untuk subtitle/link
val AccentBlueDim = Color(0xFF3D4A6B)     // biru redup untuk track OFF berwarna
val OnAccentBlue = Color(0xFF0A0F1F)

// Aksen merah (disconnected/error)
val AccentRed = Color(0xFFFF6B5E)

// Aksen hijau (membership aktif) — dipisah jadi token resmi di palet supaya
// warna badge/status "Aktif" konsisten dan tidak lagi ditulis sebagai warna
// mentah (raw Color(...)) langsung di dalam composable seperti sebelumnya.
val AccentGreen = Color(0xFF6EE7A8)
val AccentGreenContainer = Color(0xFF1E3A2A)

// Aksen kuning (membership akan berakhir / peringatan ringan)
val AccentAmber = Color(0xFFF6C560)
val AccentAmberContainer = Color(0xFF3A311A)

// Teks
val TextPrimary = Color(0xFFF2F3F7)       // putih pudar untuk judul besar
val TextSecondary = Color(0xFFB9BAC6)     // abu terang untuk body text
val TextMuted = Color(0xFF7A7B87)         // abu redup untuk caption/disabled
val TextOnCard = Color(0xFFE7E8EE)

// Aksen terracotta/oranye (FITUR BARU — lihat perintah rework: "Samakan
// Section Crosshair Persis seperti foto ke dua dari UI"): dulu dipakai
// KHUSUS section Crosshair di Settings, terpisah dari AccentBlue.
//
// DIHAPUS (RILIS v2.0 — lihat perintah rework: "fix warna Accent pada
// fitur crosshair itu harusnya mengikuti warna default sistem bukan
// coklat"): CrosshairAccent/CrosshairAccentDim/CrosshairCardBg/
// CrosshairCardBgAlt (token warna terracotta/coklat khusus Crosshair)
// dihapus total, seluruh pemakaiannya di CrosshairSettingsSection.kt
// diganti AccentBlue/AccentBlueDim/SurfaceRaised — MENGIKUTI PRESEDEN
// YANG SAMA seperti penghapusan DashboardHeroStart/DashboardHeroEnd/
// DashboardAccentOrange/DashboardPillBrown di bawah (satu identitas
// warna AccentBlue/MaterialTheme.colorScheme untuk SELURUH app, tanpa
// kecuali lagi).

// Hero card Dashboard (FITUR BARU — lihat perintah rework: "Samakan UI
// Dashboard Seperti Foto ke 1 dari Gaya"): gradient coklat gelap ke hitam
// dipakai khusus kartu hero AetherXInfoCard, warna terpisah dari
// CrosshairAccent supaya kedua kartu (Dashboard vs Crosshair) punya
// identitas warna sedikit berbeda meski sama-sama keluarga coklat/oranye
// hangat, mengikuti dua referensi yang diberikan.
// REWORK (lihat perintah rework — "warna card ... default mengikuti warna
// tema bawaan"): DashboardHeroStart/DashboardHeroEnd/DashboardAccentOrange/
// DashboardPillBrown (gradient & aksen coklat-oranye custom, sebelumnya
// dipakai kartu hero Dashboard & badge ID) DIHAPUS — seluruh app sekarang
// konsisten memakai AccentBlue/MaterialTheme.colorScheme sebagai satu
// identitas warna, bukan token warna terpisah khusus satu-dua tempat.
