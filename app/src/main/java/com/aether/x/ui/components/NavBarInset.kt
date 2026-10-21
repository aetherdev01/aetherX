package com.aether.x.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Padding Scaffold TANPA sisi bawah. Layar memakai ini untuk container scroll-nya
 * supaya area scroll membentang sampai ke tepi bawah layar (konten tembus ke
 * belakang navbar, tidak terpotong lurus di atas navbar). Tinggi navbar
 * ([PaddingValues.calculateBottomPadding]) ditambahkan sebagai padding bawah di
 * DALAM konten scroll supaya item terakhir tetap bisa digeser ke atas navbar.
 */
@Composable
fun PaddingValues.withoutBottom(): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir),
        top = calculateTopPadding(),
        end = calculateEndPadding(dir),
        bottom = 0.dp,
    )
}
