package com.aether.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.R

/**
 * Dialog color picker HSV bebas untuk warna crosshair — FITUR BARU (lihat
 * perintah rework: "opsi warna untuk sesuai selera sendiri pakai picker"),
 * melengkapi 6 swatch preset yang sudah ada di [CrosshairSettingsSection]
 * (tetap dipertahankan sebagai pintasan cepat). Terdiri dari:
 * 1. Area saturation/value persegi (geser untuk memilih kecerahan & saturasi
 *    pada hue yang sedang aktif).
 * 2. Slider hue horizontal (gradasi penuh 0-360°).
 * 3. Preview warna hasil pilihan + kode HEX-nya.
 *
 * Warna akhir dikembalikan sebagai ARGB [Long] lewat [onColorConfirmed]
 * supaya konsisten dengan tipe `colorArgb` yang sudah dipakai di seluruh
 * alur crosshair yang ada (DataStore, [com.aether.x.core.overlay.CrosshairView],
 * dst.) — TIDAK memperkenalkan tipe warna baru yang butuh konversi tambahan
 * di tempat lain.
 */
@Composable
fun CrosshairColorPickerDialog(
    initialColorArgb: Long,
    onDismiss: () -> Unit,
    onColorConfirmed: (Long) -> Unit,
) {
    val initialHsv = remember(initialColorArgb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColorArgb.toInt(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crosshair_custom_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v -> saturation = s; value = v },
                )
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        text = "#%06X".format(currentColor.toArgb() and 0xFFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Simpan sebagai ARGB penuh opacity (0xFF______) — opacity
                // crosshair sendiri diatur terpisah lewat slider Opacity yang
                // sudah ada, supaya picker ini murni untuk memilih warna dasar.
                val argb = 0xFF000000L or (currentColor.toArgb().toLong() and 0xFFFFFFL)
                onColorConfirmed(argb)
            }) {
                Text(stringResource(R.string.crosshair_custom_color_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.crosshair_custom_color_cancel))
            }
        },
    )
}

/**
 * Area persegi saturation (kiri→kanan: putih→hue murni) x value (atas→bawah:
 * terang→gelap) untuk hue yang sedang dipilih di [HueSlider]. Ketuk atau
 * seret di mana pun pada area untuk memilih titik saturation/value.
 *
 * BUG FIX (lihat perintah rework — "warna custom yang penanda warna nya
 * bug"): sebelumnya indikator posisi dihitung dengan
 * `offsetX = panelSizeDp * saturation`, memakai `panelSizeDp` (220.dp, nilai
 * TINGGI panel) untuk KEDUA sumbu X dan Y. Padahal panel ini
 * `.fillMaxWidth()` — lebar sebenarnya HAMPIR PASTI BUKAN 220dp (biasanya
 * jauh lebih lebar dari layar ponsel), jadi posisi horizontal indikator
 * selalu meleset dari titik yang benar-benar diketuk/digeser pengguna.
 * Sekarang dibungkus [BoxWithConstraints] supaya lebar & tinggi AKTUAL
 * panel diukur langsung dari `maxWidth`/`maxHeight`, dipakai konsisten baik
 * untuk kalkulasi drag (`pointerInput`) maupun posisi indikator — kedua
 * arah dijamin sinkron dengan ukuran box yang sesungguhnya dirender.
 */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (saturation: Float, value: Float) -> Unit,
) {
    val panelHeight = 220.dp
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(hue) {
                fun updateFromOffset(offset: Offset) {
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }
                // detectDragGestures juga menangani tap tunggal (drag dengan
                // delta nol memicu onDragStart) — jadi SATU detector ini
                // cukup untuk tap maupun geser, tidak perlu detectTapGestures
                // terpisah yang berisiko berebut event pointer dengan
                // detector drag di Compose.
                detectDragGestures(
                    onDragStart = { offset -> updateFromOffset(offset) },
                    onDrag = { change, _ -> updateFromOffset(change.position) },
                )
            },
    ) {
        // Indikator posisi saturation/value saat ini — lingkaran kecil
        // dengan border putih supaya terlihat di atas warna apa pun.
        // offsetX memakai maxWidth (lebar AKTUAL box, dari
        // BoxWithConstraints), offsetY memakai maxHeight — masing-masing
        // sumbu memakai ukuran sungguhan miliknya sendiri, bukan satu nilai
        // konstan yang dipakai untuk keduanya seperti sebelumnya.
        val indicatorColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        val offsetX = maxWidth * saturation
        val offsetY = maxHeight * (1f - value)
        Box(
            modifier = Modifier
                .offset(x = offsetX - 10.dp, y = offsetY - 10.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(indicatorColor)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

/**
 * Slider horizontal gradasi hue penuh (0°-360°) — geser untuk mengganti hue
 * dasar yang lalu memengaruhi warna di [SaturationValuePanel].
 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    val hueColors = remember {
        (0..360 step 30).map { deg ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(deg.toFloat(), 1f, 1f)))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(hueColors))
            .pointerInput(Unit) {
                fun updateFromOffset(x: Float) {
                    val h = (x / size.width).coerceIn(0f, 1f) * 360f
                    onHueChange(h)
                }
                detectDragGestures(
                    onDragStart = { offset -> updateFromOffset(offset.x) },
                    onDrag = { change, _ -> updateFromOffset(change.position.x) },
                )
            },
    )
}
