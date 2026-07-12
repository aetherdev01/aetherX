package com.aether.x.ui.theme

import androidx.compose.ui.graphics.Color

// === AetherX Dark UI ===
// Palet gelap "hangat/terracotta" di atas hitam kecoklatan — REWORK (lihat
// perintah "ganti warna aksen theme seluruh app biru -> krem/terracotta",
// referensi screenshot AXGC): seluruh identitas biru sebelumnya diganti
// terracotta + krem pucat. Nama variabel token (AccentBlue dkk) SENGAJA
// dipertahankan apa adanya (bukan di-rename jadi AccentTerracotta) supaya
// seluruh pemanggil di app (MaterialTheme.colorScheme turunannya) tidak
// perlu disentuh sama sekali — hanya nilai hex di file ini yang berubah.

// Background & surface
val BgVoid = Color(0xFF15110F)            // background paling belakang (hampir hitam, hangat)
val BgBase = Color(0xFF1C1817)            // background dasar layar
val SurfaceCard = Color(0xFF272322)       // kartu section utama
val SurfaceCardAlt = Color(0xFF2E2927)    // kartu bertingkat / hero card
val SurfaceRaised = Color(0xFF34302E)     // elemen di atas kartu (track switch off, dsb)
val StrokeSubtle = Color(0xFF3D3735)      // border/divider halus

// Aksen terracotta (primary) — sebelumnya AccentBlue/biru pucat
val AccentBlue = Color(0xFFC97B45)        // terracotta khas referensi (judul, ikon aktif)
val AccentBlueSoft = Color(0xFFECDAD0)    // krem pucat untuk judul besar/subtitle/link
val AccentBlueDim = Color(0xFF4A342A)     // terracotta redup untuk track OFF berwarna
val OnAccentBlue = Color(0xFF1F0F08)

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
val TextPrimary = Color(0xFFECDAD0)       // krem pucat untuk judul besar (mengikuti referensi AXGC)
val TextSecondary = Color(0xFFC7BDB6)     // krem redup untuk body text
val TextMuted = Color(0xFF8A8078)         // coklat-abu redup untuk caption/disabled
val TextOnCard = Color(0xFFE9DFD8)

// CATATAN HISTORIS (token CrosshairAccent dkk sempat dihapus & digantikan
// AccentBlue/AccentBlueDim/SurfaceRaised sebelum rework warna ini — lihat
// komentar di atas). Sekarang AccentBlue dkk SUDAH terracotta (bukan biru
// lagi), jadi section Crosshair otomatis ikut terracotta juga tanpa
// perubahan kode tambahan.

// Hero card Dashboard: sebelumnya token gradient coklat terpisah
// (DashboardHeroStart/End/AccentOrange/PillBrown) sudah dihapus dan
// digantikan AccentBlue/MaterialTheme.colorScheme — sekarang otomatis
// tampil terracotta lagi lewat token yang sama, tanpa perlu token baru.
