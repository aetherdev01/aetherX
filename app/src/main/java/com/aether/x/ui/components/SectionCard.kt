package com.aether.x.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aether.x.R

/**
 * Watermark logo AetherX transparan di pojok kiri-atas — dipakai bersama
 * oleh KEDUA overload [SectionCard] di bawah (lihat perintah rework —
 * "jadikan semua card itu ada logonya disisi kiri seperti card Crosshair,
 * dari card Tweak Dan lainnya intinya semua"). MENIRU PERSIS gaya kartu
 * Crosshair custom (lihat
 * [com.aether.x.ui.settings.CrosshairSettingsSection] — vector
 * [R.drawable.ic_aetherx_mark], tint semi-transparan, ukuran besar melebihi
 * batas kartu lalu di-clip oleh [Modifier.clipToBounds] pada [Box] induk)
 * — BUKAN logo kecil sejajar teks judul biasa, supaya identitas visual
 * SEMUA kartu di app ini konsisten satu sama lain lewat SATU titik
 * perubahan untuk ~8 layar yang memakai [SectionCard].
 *
 * Alpha di sini (0.08) sengaja sedikit lebih redup dibanding kartu
 * Crosshair (0.10) karena kartu Crosshair berlatar gelap pekat
 * ([com.aether.x.ui.theme.CrosshairCardBg]) sedangkan [SectionCard]
 * berlatar `MaterialTheme.colorScheme.surface` yang notabene sudah lebih
 * terang — alpha yang sama akan terlihat lebih mencolok/mengganggu
 * keterbacaan teks di atasnya pada latar terang.
 */
@Composable
private fun SectionCardWatermark() {
    Icon(
        painter = painterResource(id = R.drawable.ic_aetherx_mark),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier
            .padding(top = 4.dp, start = 4.dp)
            .size(84.dp),
    )
}

/**
 * Kartu section gaya AetherX: latar gelap pekat, sudut besar membulat, tanpa
 * bayangan/elevasi — mengikuti referensi (kartu flat di atas background hitam).
 * Judul ditampilkan sebagai label kecil huruf besar di atas isi kartu.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark()
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
            }
        }
    }
}

/**
 * Varian [SectionCard] TANPA judul (title = null) — dipakai untuk kartu yang
 * kontennya sudah punya identitas visual sendiri di baris pertama (mis.
 * kartu identitas aplikasi di AboutScreen yang barisnya sudah berisi
 * logo+nama+versi, sehingga label judul kartu tambahan jadi redundan).
 */
@Composable
fun SectionCard(
    title: Nothing? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark()
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}
