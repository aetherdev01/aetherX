package com.aether.x.ui.booster

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.data.GameMode
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.SurfaceCard
import com.aether.x.ui.theme.SurfaceCardAlt
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextOnCard
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

/**
 * REWORK TOTAL (lihat perintah rework — "rework total tampilan game
 * booster seperti di foto pertama"): SEBELUMNYA layar ini dua-kolom ala
 * ROG (list game kiri sempit, GameBoosterSidebarContent kanan lebar) —
 * lihat riwayat git / GameBoosterScreen.kt.OLD_BACKUP untuk versi lama.
 *
 * SEKARANG dua STATE terpisah:
 * - BELUM ada sesi aktif -> [GameBoosterPickerContent] — list pemilihan
 *   game sederhana (dipertahankan dari versi lama, disederhanakan),
 *   karena foto referensi 1 TIDAK menampilkan state pemilihan game sama
 *   sekali (foto itu adalah tampilan SETELAH game dipilih/booster aktif).
 * - SUDAH ada sesi aktif -> [GameBoosterRadialContent] — tampilan radial
 *   PENUH meniru foto referensi 1: avatar besar di tengah-bawah, gauge
 *   CPU (kiri) & RAM (kanan) vertikal di tepi layar, grid menu icon
 *   melingkari avatar (2 baris x 3 kolom per sisi = 12 menu total).
 *
 * MENU YANG SENGAJA TIDAK DIIMPLEMENTASIKAN (dikonfirmasi user — lihat
 * histori percakapan rework ini): "Tuner Ping" (butuh target host ping
 * jaringan yang belum ditentukan) dan "Kalibrasi Gyro" (butuh akses
 * sensor-level yang tidak selalu tersedia lewat shell command biasa).
 * Kedua slot ini SENGAJA diisi ulang dengan menu lain yang datanya
 * benar-benar tersedia (Crosshair toggle & Boost Cepat) alih-alih
 * dibiarkan kosong atau diisi data dummy yang menyesatkan.
 *
 * Tetap dipaksa LANDSCAPE selama layar ini terbuka, sama seperti versi
 * lama (lihat DisposableEffect di bawah) — perilaku ini TIDAK diminta
 * untuk diubah oleh perintah rework ini.
 */
@Composable
fun GameBoosterScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GameBoosterScreenViewModel = viewModel(),
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val state by viewModel.state.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val sessionToShow = activeSession ?: state.pendingSession

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(SurfaceCardAlt),
    ) {
        if (sessionToShow != null) {
            GameBoosterRadialContent(
                session = sessionToShow,
                viewModel = viewModel,
            )
        } else {
            GameBoosterPickerContent(
                state = state,
                onGameSelected = viewModel::onGameSelected,
            )
        }
    }
}

// ============================ State: pemilihan game ============================

@Composable
private fun GameBoosterPickerContent(
    state: GameBoosterScreenState,
    onGameSelected: (InstalledGameEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            Text(text = stringResource(R.string.game_booster_title), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
        Text(
            text = stringResource(R.string.game_booster_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        when {
            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(32.dp))
            }
            state.games.isEmpty() -> Text(
                text = stringResource(R.string.game_booster_empty_games),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.games, key = { it.packageName }) { game ->
                    GameBoosterGameRow(
                        entry = game,
                        selected = game.packageName == state.selectedPackage,
                        onClick = { onGameSelected(game) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameBoosterGameRow(
    entry: InstalledGameEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) AccentBlue.copy(alpha = 0.16f) else SurfaceCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) AccentBlue else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ============================ State: radial booster (foto referensi 1) ============================

/**
 * Tampilan radial penuh SETELAH sesi Game Booster aktif — meniru foto
 * referensi 1: avatar besar tengah-bawah, gauge CPU (kiri) & RAM (kanan)
 * vertikal di tepi layar, grid 2x3 menu icon di kiri & kanan avatar.
 */
@Composable
private fun GameBoosterRadialContent(
    session: com.aether.x.core.booster.GameBoosterSession,
    viewModel: GameBoosterScreenViewModel,
) {
    // State UI murni (BUKAN bagian dari GameBoosterSession/preferences —
    // lihat perintah rework foto referensi 1, menu "Information Monitor"):
    // panel detail metrics expand/collapse, hilang begitu layar ini
    // ditutup/di-recompose ulang dari awal — tidak perlu persist antar
    // sesi seperti rotationLocked/touchBoostEnabled di atas.
    var infoMonitorExpanded by remember { mutableStateOf(false) }
    val onInfoMonitorToggle: (Boolean) -> Unit = { infoMonitorExpanded = it }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header atas: judul "AXERON GAME CORNER" tengah, mode pill "AX-MODE".
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.game_booster_radial_ax_mode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextOnCard,
                )
            }
        }

        // Baris utama: gauge CPU (kiri) — avatar + label game (tengah) — gauge RAM (kanan).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RadialSideGauge(
                label = stringResource(R.string.game_booster_metrics_cpu),
                percent = session.metrics.cpuLoadPercent,
                tint = severityColor(session.metrics.cpuLoadPercent),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_aetherx_mark),
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Text(
                    text = session.gameLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.game_booster_sidebar_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }

            RadialSideGauge(
                label = stringResource(R.string.game_booster_metrics_ram),
                percent = session.metrics.ramLoadPercent,
                tint = severityColor(session.metrics.ramLoadPercent),
            )
        }

        // FITUR BARU (lihat perintah rework foto referensi 1, menu
        // "Information Monitor"): panel detail metrics yang expand/collapse
        // lewat infoMonitorExpanded di atas — muncul TEPAT di atas grid
        // menu supaya tidak mendorong avatar/gauge yang sudah dilihat
        // pengguna, hanya area di bawahnya yang bergeser saat toggle.
        androidx.compose.animation.AnimatedVisibility(visible = infoMonitorExpanded) {
            InfoMonitorPanel(metrics = session.metrics)
        }

        // Grid menu bawah: kiri 6 menu, kanan 6 menu, avatar di antaranya
        // (secara visual sudah "mengelilingi" avatar lewat baris di atas).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RadialMenuColumn(
                modifier = Modifier.weight(1f),
                items = listOf(
                    RadialMenuItemData(
                        icon = Icons.Outlined.RestartAlt,
                        label = stringResource(R.string.game_booster_radial_refresh_rate),
                        onClick = viewModel::onForceMaxRefreshRate,
                    ),
                    RadialMenuItemData(
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.game_booster_radial_information_monitor),
                        active = infoMonitorExpanded,
                        onClick = { onInfoMonitorToggle(!infoMonitorExpanded) },
                    ),
                    RadialMenuItemData(
                        icon = Icons.Outlined.Bolt,
                        label = stringResource(R.string.game_booster_radial_quick_preset),
                        // BUG FIX (ditemukan saat implementasi): SEBELUMNYA
                        // onClick di sini identik dengan "Boost Cepat" di
                        // kolom sebelah (dua tombol beda label, efek
                        // sama-sama langsung ke GameMode.BOOST) — sekarang
                        // "Preset Cepat" MEMUTAR mode Hemat->Normal->Boost
                        // secara berurutan setiap tap (lihat nextGameMode
                        // di bawah), sedangkan "Boost Cepat" tetap shortcut
                        // LANGSUNG ke Boost — dua perilaku yang benar-benar
                        // berbeda, sesuai nama masing-masing.
                        onClick = { viewModel.onModeChange(nextGameMode(session.mode)) },
                    ),
                ),
            )
            RadialMenuColumn(
                modifier = Modifier.weight(1f),
                items = listOf(
                    RadialMenuItemData(
                        icon = if (session.rotationLocked) Icons.Outlined.ScreenLockRotation else Icons.Outlined.ScreenRotation,
                        label = stringResource(R.string.game_booster_radial_rotation_lock),
                        active = session.rotationLocked,
                        onClick = { viewModel.onRotationLockToggle(!session.rotationLocked) },
                    ),
                    RadialMenuItemData(
                        icon = Icons.Outlined.NotificationsOff,
                        label = stringResource(R.string.game_booster_radial_dnd),
                        active = session.dndEnabled,
                        onClick = { viewModel.onDndToggle(!session.dndEnabled) },
                    ),
                    RadialMenuItemData(
                        icon = Icons.Outlined.Speed,
                        label = stringResource(R.string.game_booster_radial_quick_boost),
                        onClick = { viewModel.onModeChange(GameMode.BOOST) },
                    ),
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RadialMenuColumn(
                modifier = Modifier.weight(1f),
                items = listOf(
                    RadialMenuItemData(
                        icon = Icons.Outlined.CenterFocusStrong,
                        label = stringResource(R.string.game_booster_radial_crosshair),
                        onClick = { },
                    ),
                    RadialMenuItemData(
                        icon = Icons.Outlined.TouchApp,
                        label = stringResource(R.string.game_booster_radial_touch_boost),
                        active = session.touchBoostEnabled,
                        onClick = { viewModel.onTouchBoostToggle(!session.touchBoostEnabled) },
                    ),
                ),
            )
            RadialMenuColumn(
                modifier = Modifier.weight(1f),
                items = listOf(
                    RadialMenuItemData(
                        icon = Icons.Outlined.Screenshot,
                        label = stringResource(R.string.game_booster_radial_screenshot),
                        onClick = viewModel::onScreenshot,
                    ),
                    RadialMenuItemData(
                        brandedIconRes = R.drawable.ic_social_whatsapp,
                        label = stringResource(R.string.game_booster_radial_whatsapp),
                        onClick = viewModel::onWhatsAppLaunch,
                    ),
                ),
            )
        }
    }
}

/** Gauge vertikal read-only (BUKAN slider interaktif — murni display) untuk CPU/RAM di tepi layar. */
@Composable
private fun RadialSideGauge(
    label: String,
    percent: Int?,
    tint: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SurfaceRaised),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((160 * ((percent ?: 0).coerceIn(0, 100) / 100f)).dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(tint),
            )
        }
        Text(
            text = percent?.let { "$it%" } ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

private data class RadialMenuItemData(
    // BUG FIX (lihat perintah rework — "fix ikon whatsapp yang tidak
    // pas"): SEBELUMNYA field ini WAJIB diisi ImageVector generik untuk
    // semua item termasuk WhatsApp (Icons.Outlined.Chat, bubble chat
    // generik yang tidak merepresentasikan WhatsApp sama sekali) —
    // sekarang nullable, dan item WhatsApp memakai [brandedIconRes] di
    // bawah alih-alih ini.
    val icon: ImageVector? = null,
    // FITUR BARU: logo BRANDED resmi (drawable vector multi-warna, mis.
    // R.drawable.ic_social_whatsapp — SAMA PERSIS aset yang dipakai
    // AboutScreen.CommunityLinkRow) untuk item yang punya identitas
    // visual resmi sendiri dan TIDAK BOLEH di-tint jadi satu warna
    // (lihat KDoc CommunityLinkRow soal kenapa). Kalau diisi, ini
    // dipakai MENGGANTIKAN [icon] sepenuhnya — lihat RadialMenuIcon.
    val brandedIconRes: Int? = null,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun RadialMenuColumn(items: List<RadialMenuItemData>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            RadialMenuIcon(item = item, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RadialMenuIcon(item: RadialMenuItemData, modifier: Modifier = Modifier) {
    val tint = if (item.active) AccentBlue else TextSecondary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                // Logo branded (mis. WhatsApp) SELALU dapat latar putih
                // netral (SAMA seperti CommunityLinkRow di AboutScreen)
                // supaya warna resmi logo (hijau WhatsApp, dst.) kontras
                // dan tidak "tenggelam" di latar gelap SurfaceCard.
                .background(if (item.brandedIconRes != null) TextOnCard else if (item.active) AccentBlue.copy(alpha = 0.18f) else SurfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            if (item.brandedIconRes != null) {
                Image(
                    painter = painterResource(id = item.brandedIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            } else if (item.icon != null) {
                Icon(imageVector = item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun severityColor(percent: Int?): androidx.compose.ui.graphics.Color = when {
    percent == null -> TextPrimary
    percent < 60 -> AccentGreen
    percent < 85 -> AccentAmber
    else -> AccentRed
}

/**
 * FITUR BARU (lihat perintah rework foto referensi 1, menu "Information
 * Monitor"): panel ringkas semua metrics sekaligus (FPS/CPU/GPU/RAM/Suhu)
 * dalam satu baris — nilai null ditampilkan sebagai "-" (BUKAN "0", supaya
 * tidak menyesatkan seolah nilainya benar-benar 0 padahal sebenarnya
 * belum terbaca/butuh Root, lihat KDoc GameBoosterMetrics.cpuLoadPercent).
 */
@Composable
private fun InfoMonitorPanel(metrics: com.aether.x.core.booster.GameBoosterMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        InfoMonitorStat(label = stringResource(R.string.game_booster_metrics_fps), value = metrics.fps?.toString())
        InfoMonitorStat(label = stringResource(R.string.game_booster_metrics_cpu), value = metrics.cpuLoadPercent?.let { "$it%" })
        InfoMonitorStat(label = stringResource(R.string.game_booster_metrics_gpu), value = metrics.gpuLoadPercent?.let { "$it%" })
        InfoMonitorStat(label = stringResource(R.string.game_booster_metrics_ram), value = metrics.ramLoadPercent?.let { "$it%" })
        InfoMonitorStat(
            label = stringResource(R.string.game_booster_metrics_temp),
            value = metrics.temperatureCelsius?.let { "%.1f°".format(it) },
        )
    }
}

@Composable
private fun InfoMonitorStat(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value ?: "-", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/**
 * FITUR BARU (lihat perintah rework foto referensi 1, menu "Preset
 * Cepat"): urutan tetap Hemat -> Normal -> Boost -> Hemat -> ...,
 * dipakai supaya "Preset Cepat" MEMUTAR mode alih-alih menduplikasi
 * "Boost Cepat" (lihat komentar BUG FIX di pemanggilnya).
 */
private fun nextGameMode(current: GameMode): GameMode = when (current) {
    GameMode.LOW -> GameMode.MID
    GameMode.MID -> GameMode.BOOST
    GameMode.BOOST -> GameMode.LOW
}
