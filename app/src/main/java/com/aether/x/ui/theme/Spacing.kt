package com.aether.x.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skala spacing resmi AetherX — REWORK (lihat perintah rework: "jarak,
 * ukuran font, jarak padding jadikan lebih rapi lagi ... konsisten").
 *
 * SEBELUMNYA padding/jarak antar elemen ditulis manual angka lepas
 * (4dp, 6dp, 8dp, 12dp, 14dp, 16dp, 18dp, 22dp, ...) tersebar di banyak
 * file tanpa skala yang sama — dua kartu yang secara visual seharusnya
 * identik (mis. hero card di Dashboard vs SectionCard biasa) berakhir
 * punya padding berbeda (18/16 vs 20/20) padahal tidak ada alasan
 * desain untuk itu, murni tidak konsisten.
 *
 * Sekarang SEMUA jarak (padding, spacedBy, gap antar section) mengacu ke
 * token di sini — skala 4dp based, close mengikuti Material spacing scale:
 * xs(4) - sm(8) - md(12) - lg(16) - xl(20) - xxl(24). Nilai non-standar
 * lama (6dp, 14dp, 18dp, 22dp) DIHAPUS, dibulatkan ke token terdekat yang
 * secara visual setara.
 */
object Spacing {
    val xs: Dp = 4.dp   // jarak micro: antar ikon-teks rapat, badge inline
    val sm: Dp = 8.dp    // jarak antar elemen kecil dalam satu grup (label+value, chip)
    val md: Dp = 12.dp   // jarak standar antar baris (row spacing, icon-to-text)
    val lg: Dp = 16.dp   // jarak antar section/card, vertical page padding
    val xl: Dp = 20.dp   // padding horizontal halaman & padding internal SectionCard
    val xxl: Dp = 24.dp  // jarak besar antar blok mayor (jarang dipakai)
}
