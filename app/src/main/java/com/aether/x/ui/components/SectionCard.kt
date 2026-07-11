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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aether.x.R

/**
 * Watermark logo transparan di pojok kiri-atas — dipakai bersama oleh KEDUA
 * overload [SectionCard] di bawah (lihat perintah rework — "jadikan semua
 * card itu ada logonya disisi kiri seperti card Crosshair, dari card Tweak
 * Dan lainnya intinya semua"). MENIRU PERSIS gaya kartu Crosshair custom
 * (lihat [com.aether.x.ui.settings.CrosshairSettingsSection] — tint
 * semi-transparan, ukuran besar melebihi batas kartu lalu di-clip oleh
 * [Modifier.clipToBounds] pada [Box] induk) — BUKAN logo kecil sejajar teks
 * judul biasa, supaya identitas visual SEMUA kartu di app ini konsisten
 * satu sama lain lewat SATU titik perubahan untuk ~8 layar yang memakai
 * [SectionCard].
 *
 * BUG FIX RILIS v2.0 (lihat perintah rework — "untuk logo di card seperti
 * di foto itu harusnya berbeda' tiap fitur"): SEBELUMNYA watermark ini
 * HARDCODE [R.drawable.ic_aetherx_mark] (logo "X" AetherX) untuk SEMUA
 * ~22 pemanggilan [SectionCard] di seluruh app tanpa kecuali — akibatnya
 * kartu-kartu yang mewakili fitur BERBEDA (mis. "Crosshair" vs "Monitor
 * FPS") tampil dengan watermark yang identik persis, terlihat seperti
 * bug/tidak niat dikerjakan. Sekarang [icon] opsional: kalau diisi
 * (ImageVector Material Icon, dipilih relevan per fitur di setiap
 * pemanggil), dipakai menggantikan logo AetherX generik. Kalau TIDAK diisi
 * (null, default), watermark tetap fallback ke logo AetherX seperti semula
 * — supaya pemanggil mana pun yang lolos belum di-update tetap tampil
 * wajar (redup, bukan hilang/crash), bukan celah untuk sengaja dibiarkan
 * generik di banyak tempat.
 *
 * Alpha di sini (0.08) SAMA seperti kartu Crosshair (KOREKSI — sebelumnya
 * komentar ini menyebut kartu Crosshair masih 0.10 dengan latar gelap
 * pekat custom; itu sudah tidak akurat sejak rework kartu Crosshair pindah
 * ke `MaterialTheme.colorScheme.surface` & alpha 0.08 juga, lihat KDoc
 * CrosshairSettingsSection — sekarang kedua kartu benar-benar konsisten,
 * bukan cuma "sengaja dibedakan tipis").
 */
@Composable
private fun SectionCardWatermark(icon: ImageVector? = null) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = 4.dp, start = 4.dp)
                .size(84.dp),
        )
    } else {
        Icon(
            painter = painterResource(id = R.drawable.ic_aetherx_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = 4.dp, start = 4.dp)
                .size(84.dp),
        )
    }
}

/**
 * Kartu section gaya AetherX: latar gelap pekat, sudut besar membulat, tanpa
 * bayangan/elevasi — mengikuti referensi (kartu flat di atas background hitam).
 * Judul ditampilkan sebagai label kecil huruf besar di atas isi kartu.
 *
 * @param watermarkIcon Ikon watermark KHUSUS fitur ini (lihat KDoc
 * [SectionCardWatermark] soal kenapa) — SEBAIKNYA selalu diisi eksplisit
 * per pemanggilan, default null hanya sebagai jaring pengaman.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark(icon = watermarkIcon)
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
 *
 * @param watermarkIcon lihat KDoc parameter sama di overload [SectionCard]
 * yang menerima `title: String` di atas.
 */
@Composable
fun SectionCard(
    title: Nothing? = null,
    modifier: Modifier = Modifier,
    watermarkIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            SectionCardWatermark(icon = watermarkIcon)
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}
