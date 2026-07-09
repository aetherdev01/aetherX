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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.SurfaceCard
import com.aether.x.ui.theme.SurfaceRaised
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary

/**
 * Layar Game Booster penuh (lihat perintah rework: "buatkan game booster
 * screen dengan layar lanskap serta ada list game discroll atau gaya rog") —
 * dibuka dari item drawer "Game Booster" (lihat KDoc drawer di
 * `TweakScreen.kt`). Dipaksa LANDSCAPE selama layar ini terbuka (lewat
 * [DisposableEffect] yang mengubah `requestedOrientation` Activity host,
 * dikembalikan ke unspecified saat layar ditutup) — MENIRU tata letak
 * aplikasi game-booster populer (ROG/Game Space) yang selalu landscape
 * karena memang dipakai berdampingan dengan game yang juga landscape.
 *
 * Tata letak: kolom kiri sempit berisi list game ala ROG (ikon besar,
 * scroll vertikal — di landscape kolom kiri secara alami lebih tinggi
 * daripada lebar, sehingga list vertikal lebih pas daripada horizontal di
 * sini); kolom kanan lebar berisi [GameBoosterSidebarContent] untuk game
 * yang SEDANG dipilih/terakhir aktif (bukan floating, karena ini adalah
 * layar penuh, bukan overlay).
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Kolom kiri: list game ala ROG
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Text(text = stringResource(R.string.game_booster_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Text(
                text = stringResource(R.string.game_booster_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            when {
                state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(28.dp))
                }
                state.games.isEmpty() -> Text(
                    text = stringResource(R.string.game_booster_empty_games),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.games, key = { it.packageName }) { game ->
                        GameBoosterGameRow(
                            entry = game,
                            selected = game.packageName == state.selectedPackage,
                            onClick = { viewModel.onGameSelected(game) },
                        )
                    }
                }
            }
        }

        // Kolom kanan: sidebar untuk sesi/game terpilih
        Box(modifier = Modifier.fillMaxHeight().width(300.dp)) {
            val sessionToShow = activeSession ?: state.pendingSession
            if (sessionToShow != null) {
                GameBoosterSidebarContent(
                    session = sessionToShow,
                    actions = GameBoosterActions(
                        onModeChange = viewModel::onModeChange,
                        onDndToggle = viewModel::onDndToggle,
                        onFpsOverlayToggle = viewModel::onFpsOverlayToggle,
                        onScreenshot = viewModel::onScreenshot,
                        onEndSession = viewModel::onEndSession,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.game_booster_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
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
    val backgroundColor = if (selected) AccentBlue.copy(alpha = 0.16f) else SurfaceRaised
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) AccentBlue else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
