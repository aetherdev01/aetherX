package com.aether.x.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.data.CrosshairStyle
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim
import com.aether.x.ui.theme.SurfaceRaised
import kotlin.math.roundToInt

private val crosshairColorPalette = listOf(
    0xFFFFFFFFL, // putih
    0xFFFF3B30L, // merah
    0xFFE8804AL, // oranye terracotta (warna aksen section ini)
    0xFFF6C560L, // kuning keemasan
    0xFFFFD60AL, // kuning
    0xFF2F6FEDL, // biru
)

private data class StyleOption(val style: CrosshairStyle, val labelRes: Int)

private val styleOptions = listOf(
    StyleOption(CrosshairStyle.CROSS, R.string.crosshair_style_cross),
    StyleOption(CrosshairStyle.PLUS_GAP, R.string.crosshair_style_plus_gap),
    StyleOption(CrosshairStyle.X_SHAPE, R.string.crosshair_style_x),
    StyleOption(CrosshairStyle.DOT, R.string.crosshair_style_dot),
    StyleOption(CrosshairStyle.CIRCLE, R.string.crosshair_style_circle),
    StyleOption(CrosshairStyle.CIRCLE_DOT, R.string.crosshair_style_circle_dot),
    StyleOption(CrosshairStyle.CROSS_DOT, R.string.crosshair_style_cross_dot),
    StyleOption(CrosshairStyle.T_SHAPE, R.string.crosshair_style_t_shape),
    StyleOption(CrosshairStyle.DIAMOND, R.string.crosshair_style_diamond),
    StyleOption(CrosshairStyle.SQUARE, R.string.crosshair_style_square),
    // FITUR BARU: 2 style tambahan (permintaan "tambah style Crosshair
    // baru"). Rendering-nya ada di 3 tempat yang HARUS konsisten (WYSIWYG):
    // StyleIconButton di bawah (icon kecil), CrosshairPreview.kt (preview
    // besar), dan CrosshairView.onDraw (core/overlay, overlay sungguhan).
    StyleOption(CrosshairStyle.CHEVRON, R.string.crosshair_style_chevron),
    StyleOption(CrosshairStyle.DOUBLE_RING, R.string.crosshair_style_double_ring),
)

/**
 * REWORK TOTAL (lihat perintah rework terbaru — "Samakan Section Crosshair
 * Persis seperti foto ke dua dari UI, Slider buat Size, Alpha, dan Gaya
 * Lainnya semirip mungkin"): tampilan diganti total mengikuti referensi
 * visual yang diberikan pengguna, BUKAN lagi mengikuti gaya SectionCard
 * generik app ini:
 * - Kartu besar berlatar `MaterialTheme.colorScheme.surface` (BUG FIX —
 *   lihat perintah rework "perbaiki warna card coklat harusnya default
 *   tema": sebelumnya CrosshairCardBg, coklat gelap custom yang tidak
 *   ikut tema) dengan watermark logo AetherX transparan di pojok
 *   kiri-atas, dan Switch besar biru ([AccentBlue] — BUG FIX RILIS v2.0,
 *   lihat perintah rework "fix warna Accent pada fitur crosshair itu
 *   harusnya mengikuti warna default sistem bukan coklat": SEBELUMNYA
 *   CrosshairAccent, oranye terracotta custom terpisah dari identitas
 *   warna app, lihat riwayat lengkap di Color.kt) di pojok kanan-atas
 *   untuk enable/disable.
 * - Preview mini crosshair DIHAPUS (lihat perintah rework — "hapus
 *   preview crosshair"; sebelumnya ada preview kecil di tengah-atas).
 * - List style DI KIRI sekarang berupa tombol persegi ICON-ONLY (bukan
 *   chip lebar berlabel teks) — [StyleIconButton] menggambar bentuk
 *   crosshair-nya sendiri lewat Canvas kecil, scrollable vertikal untuk
 *   menampung 8 style yang tersedia.
 * - Kontrol posisi X/Y SEKARANG berupa "joystick" radial ([PositionJoystick])
 *   di tengah — lingkaran besar dengan handle bisa digeser ke segala arah,
 *   menggantikan tombol "Mulai Geser Posisi"/"Reset ke Tengah" bergaya teks
 *   sebelumnya. Menyeret handle langsung mengubah crosshairOffsetX/Y.
 * - Slider Size SEKARANG VERTIKAL ([VerticalAccentSlider]) di kanan,
 *   Alpha tetap horizontal di bawah joystick — keduanya pakai gaya
 *   custom (thumb kecil oranye persegi-rounded di atas track tipis)
 *   alih-alih `TweakSlider`/M3 Slider default, supaya visualnya ramping
 *   seperti referensi (bukan Slider Material lebar dengan track tebal).
 * - Warna dipakai sebagai swatch besar rounded-square berjajar horizontal
 *   di baris paling bawah, BUKAN lingkaran kecil seperti sebelumnya.
 *
 * Section ini SEKARANG MENGGAMBAR KARTUNYA SENDIRI (dipanggil langsung dari
 * SettingsScreen, TIDAK lagi dibungkus SectionCard generik) — lihat
 * pemanggilannya di SettingsScreen.kt.
 */
@Composable
fun CrosshairSettingsSection(
    enabled: Boolean,
    style: CrosshairStyle,
    colorArgb: Long,
    sizeDp: Int,
    thicknessDp: Int,
    opacityPercent: Int,
    offsetX: Int,
    offsetY: Int,
    overlayPermissionGranted: Boolean,
    // dragModeActive/onToggleDragMode DIPERTAHANKAN di signature (dialirkan
    // apa adanya dari SettingsScreen) untuk kompatibilitas pemanggil, TAPI
    // TIDAK DIPAKAI lagi di badan Composable ini — posisi X/Y sekarang
    // diatur LANGSUNG lewat [PositionJoystick] di kartu ini sendiri
    // (menyeret handle langsung memicu [onOffsetChange]), menggantikan alur
    // lama "aktifkan drag-mode lalu geser crosshair di overlay layar lain".
    dragModeActive: Boolean,
    // OPSI BARU (permintaan "tambahkan juga opsi baru di pengaturan"):
    // kunci posisi crosshair supaya joystick tidak bisa digeser tanpa
    // sengaja setelah posisi pas ditemukan — berguna karena kartu ini
    // scrollable dan sentuhan tidak sengaja di area joystick sebelumnya
    // langsung memindahkan crosshair. Sumber kebenarannya (default value,
    // penyimpanan) ada di AppPreferences/AetherXPreferences milik modul
    // `data`, di luar zip ini — lihat wiring di SettingsScreen.kt &
    // SettingsViewModel.kt.
    positionLocked: Boolean = false,
    onPositionLockedChange: (Boolean) -> Unit = {},
    onEnabledChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStyleChange: (CrosshairStyle) -> Unit,
    onColorChange: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onThicknessChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onToggleDragMode: (Boolean) -> Unit,
    onResetPosition: () -> Unit,
    onOffsetChange: (x: Int, y: Int) -> Unit,
) {
    // FITUR BARU: state dialog color picker HSV kustom — lihat pemakaian di
    // baris swatch warna & CrosshairColorPickerDialog di bawah.
    var showColorPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            // BUG FIX (lihat perintah rework — "perbaiki warna card coklat
            // harusnya default tema"): sebelumnya CrosshairCardBg (coklat
            // gelap custom, lihat KDoc di atas). Diganti ke
            // MaterialTheme.colorScheme.surface supaya kartu ini mengikuti
            // token tema aktif seperti SectionCard di layar lain — otomatis
            // benar juga di light theme, bukan hanya dark theme.
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Watermark logo transparan di pojok kiri-atas, meniru referensi —
        // diperbesar melebihi batas kartu (clipToBounds bawaan Box induk
        // yang membungkus ini menyembunyikan kelebihannya secara otomatis).
        // Alpha diturunkan dari 0.10 ke 0.08 (menyamai SectionCardWatermark
        // di SectionCard.kt) karena latar sekarang MaterialTheme.colorScheme.surface
        // yang lebih terang dari CrosshairCardBg lama — alpha lama akan
        // terlihat terlalu mencolok di latar yang lebih terang ini.
        //
        // BUG FIX RILIS v2.0 (lihat perintah rework — "untuk logo di card
        // seperti di foto itu harusnya berbeda' tiap fitur"): SEBELUMNYA
        // ikon di sini R.drawable.ic_aetherx_mark (logo "X" AetherX generik)
        // — PERSIS SAMA dengan watermark kartu "Monitor FPS (Beta)" di
        // bawahnya (lihat SettingsScreen -> SectionCard, watermarkIcon
        // Icons.Outlined.Speed) meski dua fitur ini sama sekali berbeda,
        // itulah keluhan "berbeda tiap fitur" di foto referensi. Diganti
        // Icons.Outlined.CenterFocusStrong — SAMA PERSIS dengan ikon yang
        // dipakai tombol quick-toggle "Crosshair" di menu radial Game
        // Booster (GameBoosterScreen.kt), supaya SATU konsep "Crosshair" di
        // seluruh app konsisten pakai SATU ikon yang sama, sekaligus jelas
        // berbeda dari kartu-kartu fitur lain.
        Icon(
            imageVector = Icons.Outlined.CenterFocusStrong,
            contentDescription = null,
            tint = AccentBlue.copy(alpha = 0.08f),
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp)
                .size(96.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // Header: judul + subjudul di kiri, Switch besar oranye di kanan.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.crosshair_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        // BUG FIX: Color.White hardcode diganti ke
                        // MaterialTheme.colorScheme.onSurface — di light
                        // theme, teks putih di atas card putih (surface
                        // terang) akan hilang; onSurface otomatis kontras
                        // benar di kedua tema.
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.crosshair_card_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked && !overlayPermissionGranted) {
                            onRequestOverlayPermission()
                        } else {
                            onEnabledChange(checked)
                        }
                    },
                    // BUG FIX (permintaan "fix warna Accent switch crosshair
                    // saat off harusnya default abu' seperti switch
                    // lainnya"): SEBELUMNYA uncheckedThumbColor = AccentBlueDim
                    // — walau namanya "Blue", token ini sebenarnya nuansa
                    // coklat/oranye redup (turunan dari AccentBlue lama yang
                    // sudah diganti tapi dim-nya belum ikut di-update saat
                    // BUG FIX RILIS v2.0 di atas), sehingga thumb saat OFF
                    // terlihat coklat mencolok (lihat screenshot laporan) —
                    // tidak konsisten dengan Switch "Monitor FPS" di bawahnya
                    // yang polos pakai SwitchDefaults.colors() default M3
                    // (abu-abu netral saat off). uncheckedThumbColor &
                    // uncheckedTrackColor sekarang di-OMIT supaya keduanya
                    // otomatis jatuh ke default M3 SwitchDefaults — sama
                    // persis dengan switch FPS Monitor, konsisten di seluruh
                    // layar Settings.
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = AccentBlue,
                    ),
                )
            }

            if (!overlayPermissionGranted) {
                Text(
                    text = stringResource(R.string.crosshair_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (enabled) {
                // Preview mini DIHAPUS (lihat perintah rework — "hapus
                // preview crosshair") — sebelumnya ada CrosshairPreview()
                // kecil di tengah-atas kartu di sini.

                // Baris utama: list style (kiri) — joystick posisi (tengah)
                // — slider Size vertikal (kanan). Tinggi tetap 260dp supaya
                // joystick, list style, dan slider vertikal semuanya
                // punya ruang yang cukup dan sejajar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StyleIconList(
                        selected = style,
                        onSelect = onStyleChange,
                        modifier = Modifier.width(56.dp),
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            // BARU: tombol kunci posisi — mencegah joystick
                            // tergeser tidak sengaja. Ikon berubah antara
                            // Lock/LockOpen mengikuti state positionLocked.
                            IconButton(onClick = { onPositionLockedChange(!positionLocked) }) {
                                Icon(
                                    imageVector = if (positionLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = stringResource(
                                        if (positionLocked) R.string.crosshair_position_unlock else R.string.crosshair_position_lock,
                                    ),
                                    tint = if (positionLocked) AccentBlue else Color.White.copy(alpha = 0.5f),
                                )
                            }
                            IconButton(onClick = onResetPosition, enabled = !positionLocked) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.crosshair_position_reset_center),
                                    tint = Color.White.copy(alpha = if (positionLocked) 0.25f else 0.5f),
                                )
                            }
                        }
                        // screenBoundsPx: dimensi layar ASLI device (dalam
                        // px) — dipakai PositionJoystick untuk memetakan
                        // posisi drag (0..1 dari kotak trackpad) ke rentang
                        // offset penuh layar, MENGGANTIKAN batas hardcode
                        // ±200 yang lama. Lihat KDoc PositionJoystick.
                        val configuration = LocalConfiguration.current
                        val density = LocalDensity.current
                        val screenBoundsPx = remember(configuration, density) {
                            IntSize(
                                width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                                height = with(density) { configuration.screenHeightDp.dp.roundToPx() },
                            )
                        }
                        PositionJoystick(
                            offsetX = offsetX,
                            offsetY = offsetY,
                            screenBoundsPx = screenBoundsPx,
                            locked = positionLocked,
                            onOffsetChange = onOffsetChange,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    VerticalAccentSlider(
                        label = stringResource(R.string.crosshair_size_label),
                        valueText = "${(sizeDp / 40f).let { "%.1f".format(it) }}x",
                        value = sizeDp.toFloat(),
                        range = 12f..80f,
                        onValueChange = { onSizeChange(it.toInt()) },
                        modifier = Modifier.width(56.dp),
                    )
                }

                // Alpha (opacity) — slider horizontal ramping custom.
                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_opacity_label),
                    valueText = "$opacityPercent%",
                    value = opacityPercent.toFloat(),
                    range = 20f..100f,
                    onValueChange = { onOpacityChange(it.toInt()) },
                    modifier = Modifier.padding(top = 20.dp),
                )

                // Ketebalan tetap ada (tidak ada di referensi tapi tetap
                // fungsi penting) — dipertahankan sebagai slider horizontal
                // ramping yang sama, di bawah Alpha.
                HorizontalAccentSlider(
                    label = stringResource(R.string.crosshair_thickness_label),
                    valueText = "${thicknessDp}dp",
                    value = thicknessDp.toFloat(),
                    range = 1f..12f,
                    onValueChange = { onThicknessChange(it.toInt()) },
                    modifier = Modifier.padding(top = 16.dp),
                )

                // Swatch warna besar rounded-square berjajar horizontal,
                // + satu swatch "custom" di ujung kanan yang membuka
                // [CrosshairColorPickerDialog] — FITUR BARU (permintaan
                // "opsi warna untuk sesuai selera sendiri pakai picker").
                // Swatch custom ini SELALU tampak terpilih (border aktif)
                // kalau warna saat ini BUKAN salah satu dari 6 preset di
                // [crosshairColorPalette] — menandakan pengguna sedang
                // memakai warna hasil pilihan bebas, bukan preset.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    crosshairColorPalette.forEach { swatch ->
                        ColorSwatchLarge(
                            color = swatch,
                            selected = swatch == colorArgb,
                            onClick = { onColorChange(swatch) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CustomColorSwatch(
                        currentColorArgb = colorArgb,
                        isCustomActive = crosshairColorPalette.none { it == colorArgb },
                        onClick = { showColorPicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        CrosshairColorPickerDialog(
            initialColorArgb = colorArgb,
            onDismiss = { showColorPicker = false },
            onColorConfirmed = { picked ->
                onColorChange(picked)
                showColorPicker = false
            },
        )
    }
}

/** List vertikal scrollable berisi tombol icon-only tiap style crosshair — meniru list kiri di referensi. */
@Composable
private fun StyleIconList(
    selected: CrosshairStyle,
    onSelect: (CrosshairStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(styleOptions, key = { it.style.name }) { option ->
            StyleIconButton(
                style = option.style,
                label = stringResource(option.labelRes),
                isSelected = option.style == selected,
                onClick = { onSelect(option.style) },
            )
        }
    }
}

@Composable
private fun StyleIconButton(
    style: CrosshairStyle,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AccentBlueDim else SurfaceRaised)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2.6f
            val thickness = 2.2f
            val drawColor = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.6f)
            when (style) {
                CrosshairStyle.CROSS -> {
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                CrosshairStyle.PLUS_GAP -> {
                    val gap = r * 0.35f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                CrosshairStyle.X_SHAPE -> {
                    val d = r * 0.7071f
                    drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                }
                CrosshairStyle.DOT -> drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
                CrosshairStyle.CIRCLE -> drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                CrosshairStyle.CIRCLE_DOT -> {
                    drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                    drawCircle(drawColor, radius = thickness * 1.6f, center = Offset(cx, cy))
                }
                CrosshairStyle.CROSS_DOT -> {
                    val gap = r * 0.35f
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx, cy + r), thickness, StrokeCap.Round)
                    drawCircle(drawColor, radius = thickness * 1.4f, center = Offset(cx, cy))
                }
                CrosshairStyle.T_SHAPE -> {
                    drawLine(drawColor, Offset(cx - r, cy), Offset(cx + r, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy), Offset(cx, cy + r), thickness, StrokeCap.Round)
                }
                // BARU: belah ketupat terbuka (4 garis membentuk diamond,
                // dengan gap di tengah supaya titik bidik tetap terlihat
                // jelas) — gaya populer di crosshair custom game FPS mobile.
                CrosshairStyle.DIAMOND -> {
                    val gap = r * 0.3f
                    val d = r * 0.7071f
                    val gapD = gap * 0.7071f
                    drawLine(drawColor, Offset(cx - gapD, cy - gapD), Offset(cx - d, cy - d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy - d), Offset(cx, cy - r), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - r), Offset(cx + d, cy - d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + d, cy - d), Offset(cx + gapD, cy - gapD), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gapD, cy + gapD), Offset(cx + d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + d, cy + d), Offset(cx, cy + r), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + r), Offset(cx - d, cy + d), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - d, cy + d), Offset(cx - gapD, cy + gapD), thickness, StrokeCap.Round)
                }
                // BARU: kotak terbuka (4 sudut siku-siku terpisah, seperti
                // bracket kamera) — gaya "frame" yang menandai area bidik
                // tanpa menutupi target di tengah.
                CrosshairStyle.SQUARE -> {
                    val s = r * 0.85f
                    val corner = s * 0.5f
                    // Sudut kiri-atas.
                    drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s + corner, cy - s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - s, cy - s), Offset(cx - s, cy - s + corner), thickness, StrokeCap.Round)
                    // Sudut kanan-atas.
                    drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s - corner, cy - s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + s, cy - s), Offset(cx + s, cy - s + corner), thickness, StrokeCap.Round)
                    // Sudut kiri-bawah.
                    drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s + corner, cy + s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - s, cy + s), Offset(cx - s, cy + s - corner), thickness, StrokeCap.Round)
                    // Sudut kanan-bawah.
                    drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s - corner, cy + s), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + s, cy + s), Offset(cx + s, cy + s - corner), thickness, StrokeCap.Round)
                }
                // FITUR BARU: 4 chevron "V" dari tiap sisi mengarah ke pusat
                // (gaya populer di crosshair custom PUBG Mobile/Free Fire) —
                // implementasi HARUS identik dengan CrosshairPreview.kt dan
                // CrosshairView.onDraw (core/overlay) supaya WYSIWYG.
                CrosshairStyle.CHEVRON -> {
                    val gap = r * 0.35f
                    val arm = r * 0.45f
                    val tip = r * 0.9f
                    // Chevron atas: "V" terbalik, ujung mengarah ke bawah/pusat.
                    drawLine(drawColor, Offset(cx - arm, cy - tip), Offset(cx, cy - gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy - gap), Offset(cx + arm, cy - tip), thickness, StrokeCap.Round)
                    // Chevron bawah.
                    drawLine(drawColor, Offset(cx - arm, cy + tip), Offset(cx, cy + gap), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx, cy + gap), Offset(cx + arm, cy + tip), thickness, StrokeCap.Round)
                    // Chevron kiri.
                    drawLine(drawColor, Offset(cx - tip, cy - arm), Offset(cx - gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx - gap, cy), Offset(cx - tip, cy + arm), thickness, StrokeCap.Round)
                    // Chevron kanan.
                    drawLine(drawColor, Offset(cx + tip, cy - arm), Offset(cx + gap, cy), thickness, StrokeCap.Round)
                    drawLine(drawColor, Offset(cx + gap, cy), Offset(cx + tip, cy + arm), thickness, StrokeCap.Round)
                }
                // FITUR BARU: dua lingkaran konsentris, gaya sniper-scope —
                // implementasi HARUS identik dengan CrosshairPreview.kt dan
                // CrosshairView.onDraw (core/overlay) supaya WYSIWYG.
                CrosshairStyle.DOUBLE_RING -> {
                    drawCircle(drawColor, radius = r, center = Offset(cx, cy), style = Stroke(thickness))
                    drawCircle(drawColor, radius = r * 0.55f, center = Offset(cx, cy), style = Stroke(thickness))
                }
            }
        }
    }
}

/**
 * "Trackpad" posisi X/Y crosshair — kotak persegi yang merepresentasikan
 * SELURUH layar device secara proporsional (rasio lebar:tinggi kotak ini
 * mengikuti rasio lebar:tinggi layar asli, lewat [LocalConfiguration]).
 *
 * REWORK TOTAL (lihat perintah rework — "perbaiki sistem ubah posisi
 * crosshair yang kurang pas karena cuman geser' doang dan tidak
 * memposisikan crosshair sesuka hati, dan itu segitiga bisa ditahan untuk
 * ubah posisi XY"). Implementasi SEBELUMNYA (masih ada di riwayat git kalau
 * perlu dibandingkan) punya DUA masalah:
 * 1. Posisi diubah lewat DELTA per-gerakan jari (`offsetX + dragAmount.x`)
 *    yang di-`coerceIn(-200, 200)` — batas ini SANGAT KECIL dibanding lebar
 *    layar sungguhan (yang bisa >1000px), jadi crosshair mentok di area
 *    sempit di tengah layar dan TIDAK BISA diposisikan ke pojok/tepi mana
 *    pun — persis keluhan "cuma geser doang".
 * 2. Handle visual (lingkaran "derajat") DIAM di tengah — cuma anak panah
 *    dekoratif 4 arah yang tidak benar-benar merepresentasikan posisi X/Y
 *    saat ini, jadi tidak ada umpan balik visual "sekarang crosshair ada di
 *    mana persis".
 *
 * SEBELUMNYA (rework di atas): menyeret jari di MANA PUN dalam kotak ini
 * langsung memindahkan handle (titik terang kecil) ke posisi persis di
 * bawah jari (absolute positioning, bukan delta), dan [onOffsetChange]
 * dipanggil dengan offset piksel LAYAR ASLI yang sesuai — didapat dari
 * memetakan posisi relatif dalam kotak (0..1 dari kiri, 0..1 dari atas) ke
 * rentang penuh lebar/tinggi layar (lihat [screenBoundsPx] yang dihitung
 * dari [LocalConfiguration] di pemanggil [CrosshairSettingsSection]). Ini
 * juga otomatis membuang batas ±200 lama — batas baru adalah SETENGAH
 * lebar/tinggi layar (crosshair bisa digeser sampai tepat di tepi layar,
 * tidak bisa hilang total ke luar layar supaya pengguna tidak "kehilangan"
 * crosshairnya sendiri).
 *
 * BUG FIX RILIS v2.0 (lihat perintah rework — "jadikan atur posisi
 * crosshair lebih mudah bukan sekali drag langsung pindah jauh"):
 * ABSOLUTE POSITIONING di atas TERNYATA memunculkan masalah baru — trackpad
 * ini cuma 220dp sedangkan direpresentasikannya adalah SETENGAH lebar/tinggi
 * layar asli (bisa >500px sungguhan), jadi SENTUHAN PERTAMA di mana pun
 * dalam kotak (bahkan sedikit meleset dari posisi crosshair saat ini)
 * langsung MELOMPATKAN crosshair jauh secara instan ke titik yang dipetakan
 * proporsional — persis keluhan "sekali drag langsung pindah jauh".
 *
 * SEKARANG: kembali ke DELTA per-gerakan jari (BUKAN balik ke bug lama poin
 * 1 di atas!) — bedanya, skala delta ini memakai rasio (SETENGAH lebar/
 * tinggi layar asli) / (lebar/tinggi trackpad 220dp) yang SAMA PERSIS
 * dengan rework absolute-positioning di atas, jadi satu drag PENUH dari
 * sisi ke sisi trackpad tetap bisa memindahkan crosshair dari ujung ke
 * ujung layar (TIDAK reproduksi masalah ±200 hardcode yang lama) — hanya
 * saja sekarang [onDragStart] TIDAK melompat ke posisi jari, melainkan
 * "menggenggam" crosshair dari posisi [offsetX]/[offsetY] TERKINI (dibaca
 * lewat [rememberUpdatedState] supaya selalu segar tanpa perlu me-restart
 * gesture-detection loop [pointerInput] setiap kali offset berubah), lalu
 * gerakan jari SELANJUTNYA menggeser posisi itu secara relatif. Efeknya:
 * sentuhan pertama di mana pun dalam trackpad tidak lagi memindahkan
 * crosshair sama sekali — geseran jari-lah yang memindahkannya, terasa
 * halus dan dapat diprediksi.
 *
 * 4 segitiga di tepi ([DirectionArrow]) DIPERTAHANKAN sebagai penanda arah
 * (sesuai referensi visual asli — "itu segitiga") — tapi sekarang PURA-PURA
 * TIDAK LAGI jadi satu-satunya cara geser (dulu areanya sendiri yang
 * di-drag); segitiga sekarang murni dekorasi statis di tepi trackpad,
 * sedangkan drag-nya berlaku di SELURUH luas kotak, dan segitiga tetap bisa
 * DITAHAN (ditekan+tahan) karena keduanya ada dalam Box yang sama dan
 * pointerInput trackpad menangkap gesture di seluruh area termasuk di atas
 * segitiga.
 */
@Composable
private fun PositionJoystick(
    offsetX: Int,
    offsetY: Int,
    screenBoundsPx: IntSize,
    // BARU: saat true, drag gesture di trackpad diabaikan (lihat
    // pointerInput di bawah) — pasangan tombol kunci di CrosshairSettingsSection.
    locked: Boolean = false,
    onOffsetChange: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackpadSize = 220.dp
    // Batas offset SEKARANG dinamis (setengah lebar/tinggi layar asli),
    // BUKAN hardcode ±200 seperti sebelumnya — lihat KDoc di atas.
    val maxOffsetX = (screenBoundsPx.width / 2).coerceAtLeast(1)
    val maxOffsetY = (screenBoundsPx.height / 2).coerceAtLeast(1)

    // BUG FIX RILIS v2.0 (lihat KDoc lengkap di atas fungsi ini): dibaca di
    // dalam pointerInput lewat rememberUpdatedState, BUKAN offsetX/offsetY
    // parameter composable secara langsung — pointerInput's coroutine hidup
    // lebih lama dari satu recomposition (keys-nya cuma maxOffsetX/Y, tidak
    // termasuk offsetX/Y supaya gesture-detection loop TIDAK ikut restart
    // setiap kali posisi berubah selama drag berlangsung), jadi tanpa
    // rememberUpdatedState, onDragStart bisa membaca offsetX/Y BASI dari
    // saat pointerInput ini pertama kali dipasang, bukan nilai terkini.
    val latestOffsetX by rememberUpdatedState(offsetX)
    val latestOffsetY by rememberUpdatedState(offsetY)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(trackpadSize)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceRaised.copy(alpha = 0.4f))
                .border(1.dp, if (locked) AccentBlue.copy(alpha = 0.3f) else AccentBlueDim, RoundedCornerShape(20.dp))
                .pointerInput(maxOffsetX, maxOffsetY, locked) {
                    // BARU: saat terkunci, tidak mendaftarkan gesture drag
                    // sama sekali — trackpad jadi murni tampilan statis.
                    if (locked) return@pointerInput
                    // Rasio skala: SAMA PERSIS dengan pemetaan absolute
                    // positioning sebelumnya (setengah lebar/tinggi layar
                    // asli dibagi lebar/tinggi trackpad) — supaya satu drag
                    // PENUH dari sisi ke sisi trackpad tetap bisa memindahkan
                    // crosshair dari ujung ke ujung layar, TIDAK reproduksi
                    // batas ±200 hardcode yang lama (lihat KDoc di atas).
                    val scaleX = maxOffsetX.toFloat() * 2f / size.width.toFloat()
                    val scaleY = maxOffsetY.toFloat() * 2f / size.height.toFloat()
                    // Posisi "berjalan" LOKAL untuk gesture yang sedang
                    // aktif — diinisialisasi dari offset TERKINI setiap kali
                    // gesture BARU dimulai (onDragStart), lalu diakumulasi
                    // dari dragAmount selama drag berlangsung. Ini
                    // MENGGENGGAM crosshair dari posisinya sekarang, BUKAN
                    // melompat ke titik sentuh (lihat KDoc "BUG FIX RILIS
                    // v2.0" di atas fungsi ini).
                    var runningX = 0f
                    var runningY = 0f
                    detectDragGestures(
                        onDragStart = {
                            runningX = latestOffsetX.toFloat()
                            runningY = latestOffsetY.toFloat()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        runningX = (runningX + dragAmount.x * scaleX)
                            .coerceIn(-maxOffsetX.toFloat(), maxOffsetX.toFloat())
                        runningY = (runningY + dragAmount.y * scaleY)
                            .coerceIn(-maxOffsetY.toFloat(), maxOffsetY.toFloat())
                        onOffsetChange(runningX.roundToInt(), runningY.roundToInt())
                    }
                },
        ) {
            // 4 segitiga penanda arah di tepi trackpad — dekoratif, TETAP
            // ADA sesuai referensi visual asli ("segitiga"), lihat KDoc di
            // atas soal kenapa drag TIDAK LAGI eksklusif di area segitiga.
            DirectionArrow(Alignment.TopCenter, rotationDeg = 0f)
            DirectionArrow(Alignment.BottomCenter, rotationDeg = 180f)
            DirectionArrow(Alignment.CenterStart, rotationDeg = 270f)
            DirectionArrow(Alignment.CenterEnd, rotationDeg = 90f)

            // Garis silang tipis di tengah trackpad menandai posisi (0,0) —
            // titik tengah layar asli, referensi visual "di mana posisi
            // default" untuk pengguna.
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                drawLine(
                    color = AccentBlueDim.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f, size.height / 2f - 6.dp.toPx()),
                    end = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawLine(
                    color = AccentBlueDim.copy(alpha = 0.4f),
                    start = Offset(size.width / 2f - 6.dp.toPx(), size.height / 2f),
                    end = Offset(size.width / 2f + 6.dp.toPx(), size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Handle: titik terang yang POSISINYA MENGIKUTI offsetX/offsetY
            // saat ini (bukan diam di tengah seperti implementasi lama) —
            // inilah umpan balik visual "sekarang crosshair ada di mana
            // persis" yang sebelumnya tidak ada.
            val handleFractionX = 0.5f + (offsetX.toFloat() / maxOffsetX.toFloat()) * 0.5f
            val handleFractionY = 0.5f + (offsetY.toFloat() / maxOffsetY.toFloat()) * 0.5f
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (handleFractionX.coerceIn(0f, 1f) * trackpadSize.toPx() - HANDLE_SIZE_DP.dp.toPx() / 2f).roundToInt(),
                            y = (handleFractionY.coerceIn(0f, 1f) * trackpadSize.toPx() - HANDLE_SIZE_DP.dp.toPx() / 2f).roundToInt(),
                        )
                    }
                    .size(HANDLE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(AccentBlue)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            )
        }

        // FIX (perbaikan ukuran font terlalu kecil): labelMedium (11sp)
        // dinaikkan ke bodyMedium (13sp) supaya konsisten dengan label
        // slider Size/Transparansi/Ketebalan di atas.
        Text(
            text = stringResource(R.string.crosshair_position_offset_format, offsetX, offsetY),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private const val HANDLE_SIZE_DP = 22

@Composable
private fun DirectionArrow(alignment: Alignment, rotationDeg: Float) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp)
                .rotate(rotationDeg),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = AccentBlueDim)
            }
        }
    }
}

/** Slider vertikal ramping custom (thumb kecil oranye di atas track tipis) — dipakai untuk Size, meniru referensi. */
@Composable
private fun VerticalAccentSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // FIX (perbaikan ukuran font terlalu kecil): labelSmall (10sp)
        // dinaikkan ke bodyMedium (13sp) + Medium weight supaya label
        // "Size" dan angka value-nya jelas terbaca, sejajar dengan
        // perbaikan yang sama di HorizontalAccentSlider (Transparansi/
        // Ketebalan) di bawah.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        // BUG FIX (lihat perintah rework — "fix total slider crosshair
        // yang tidak sesuai dan terlalu licin"): SEBELUMNYA pointerInput
        // dipasang LANGSUNG di Box track yang lebarnya cuma 4.dp — jari
        // harus presisi kena strip sesempit itu untuk mulai drag, dan
        // change.position dihitung relatif terhadap size Box 4dp itu
        // sendiri, jadi geser sedikit saja ke luar strip langsung
        // ter-coerceIn ke 0f/1f (terasa "lompat"/licin).
        //
        // FIX: pointerInput sekarang dipasang di Box PEMBUNGKUS terpisah
        // selebar 40.dp (hit-area nyaman untuk jari), sementara track
        // visual tipis (4.dp) digambar sebagai child di tengahnya lewat
        // Modifier.align — tampilan tetap ramping seperti referensi, tapi
        // area sentuh jauh lebih toleran sehingga tidak licin.
        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                        onValueChange(range.start + newFraction * (range.endInclusive - range.start))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceRaised),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((fraction * 200).dp.coerceAtMost(200.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBlue),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (fraction * 200).dp.coerceAtMost(196.dp))
                        .size(width = 20.dp, height = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlue),
                )
            }
        }
    }
}

/** Slider horizontal ramping custom (thumb kecil oranye di atas track tipis) — dipakai untuk Alpha & Ketebalan. */
@Composable
private fun HorizontalAccentSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // FIX (perbaikan ukuran font Transparansi/Ketebalan yang
            // terlalu kecil): sebelumnya labelSmall (10sp) — hampir tidak
            // terbaca. Dinaikkan ke bodyMedium (13sp) + Medium weight,
            // DISAMAKAN dengan VerticalAccentSlider ("Size") di atas
            // supaya semua slider crosshair konsisten dan mudah dibaca.
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentBlue,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        // BUG FIX (lihat perintah rework — "fix total slider crosshair
        // yang tidak sesuai dan terlalu licin"): pola sama seperti
        // VerticalAccentSlider di atas — pointerInput dipindah ke Box
        // pembungkus setinggi 40.dp (hit-area nyaman), track visual tetap
        // tipis 4.dp sebagai child di tengahnya.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + newFraction * (range.endInclusive - range.start))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceRaised),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBlue),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (fraction * 280).dp.coerceAtMost(276.dp))
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlue),
                )
            }
        }
    }
}

/** Swatch warna besar rounded-square, meniru baris warna di bagian bawah referensi. */
@Composable
private fun ColorSwatchLarge(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(color.toInt()))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = contrastingCheckTint(Color(color.toInt())),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Swatch "custom" — pintu masuk ke [CrosshairColorPickerDialog]. FITUR BARU
 * (permintaan "opsi warna untuk sesuai selera sendiri pakai picker"),
 * ditaruh di ujung kanan baris [ColorSwatchLarge] preset.
 *
 * Latar selalu conic-gradient pelangi (representasi visual "semua warna
 * tersedia di sini", tidak terikat satu warna seperti swatch preset) dengan
 * ikon "+" di tengah. Kalau [isCustomActive] true (warna crosshair saat ini
 * bukan salah satu dari 6 preset), border aktif [AccentBlue] ditampilkan —
 * sama seperti indikator "selected" pada [ColorSwatchLarge] — supaya jelas
 * kalau pengguna sedang memakai hasil pilihan custom, bukan berarti belum
 * memilih apa pun.
 */
@Composable
private fun CustomColorSwatch(
    currentColorArgb: Long,
    isCustomActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF3B30), Color(0xFFFFD60A), Color(0xFF34C759),
                Color(0xFF2F6FED), Color(0xFFAF52DE), Color(0xFFFF3B30),
            ),
        )
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rainbowBrush)
            .border(
                width = if (isCustomActive) 2.dp else 0.dp,
                color = if (isCustomActive) AccentBlue else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (isCustomActive) Color(currentColorArgb.toInt()) else Color.Black.copy(alpha = 0.45f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!isCustomActive) {
                Text(
                    text = "+",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = contrastingCheckTint(Color(currentColorArgb.toInt())),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * BUG FIX (dipertahankan dari rework sebelumnya — "warna custom yang
 * penanda warna nya bug"): pilih tint check mark berdasarkan luminance
 * warna latar, supaya tetap kontras di warna terang maupun gelap.
 */
private fun contrastingCheckTint(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}
