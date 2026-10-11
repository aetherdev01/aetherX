package com.aether.x.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skala spacing TUNGGAL AetherX (kelipatan 4dp). Semua jarak antar-elemen,
 * padding kartu, dan jarak teks wajib memakai token ini — jangan pakai angka
 * `.dp` mentah untuk jarak layout, supaya ritme vertikal konsisten di semua
 * layar.
 *
 * Aturan jarak teks (dipakai lewat [Spacing.title], [Spacing.line], dst):
 *  - judul kartu -> isi kartu      : [cardGap]  (12dp)
 *  - judul -> subjudul/deskripsi   : [line]     (4dp)
 *  - antar baris item dalam list   : [item]     (12dp)
 *  - antar kartu / seksi layar     : [section]  (16dp)
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp

    /** Padding horizontal layar penuh. */
    val screen: Dp = 16.dp

    /** Jarak vertikal antar kartu/seksi. */
    val section: Dp = 16.dp

    /** Padding dalam kartu (semua sisi). */
    val card: Dp = 16.dp

    /** Jarak judul kartu -> konten kartu. */
    val cardGap: Dp = 12.dp

    /** Jarak antar baris teks yang berpasangan (judul + deskripsi). */
    val line: Dp = 4.dp

    /** Jarak antar item dalam satu list/kolom. */
    val item: Dp = 12.dp
}
