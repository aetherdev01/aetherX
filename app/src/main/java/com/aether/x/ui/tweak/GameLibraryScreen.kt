package com.aether.x.ui.tweak

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.apps.GameLaunchTracker
import com.aether.x.core.apps.GameProfileCatalog
import com.aether.x.core.apps.InstalledGameEntry
import com.aether.x.core.monitor.GameSession
import com.aether.x.core.monitor.GameSessionRepository
import com.aether.x.data.AppPreferences
import com.aether.x.data.AetherXPreferences
import com.aether.x.ui.components.SectionCard
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

private enum class LibraryFilter(val title: String) {
    ALL("Semua"), RECENT("Baru dimainkan"), FAVORITES("Favorit")
}

@Composable
fun GameLibraryScreen(
    modifier: Modifier = Modifier,
    onConfigureGame: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val preferences = remember(appContext) { AetherXPreferences(appContext) }
    val savedPreferences by preferences.preferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val sessions by GameSessionRepository.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var games by remember { mutableStateOf<List<InstalledGameEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    val favoriteStore = remember(appContext) {
        appContext.getSharedPreferences("aetherx_game_library", Context.MODE_PRIVATE)
    }
    var favorites by remember(favoriteStore) {
        mutableStateOf(favoriteStore.getStringSet("favorite_packages", emptySet()).orEmpty().toSet())
    }

    LaunchedEffect(appContext) {
        GameSessionRepository.initialize(appContext)
        games = GameProfileCatalog.loadInstalledGames(appContext)
        loading = false
    }

    val recentPackages = remember(savedPreferences.recentGamePackages, sessions) {
        (savedPreferences.recentGamePackages + sessions.sortedByDescending { it.startedAtMs }.map { it.packageName })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val recentRank = remember(recentPackages) { recentPackages.withIndex().associate { it.value to it.index } }
    val latestSessions = remember(sessions) {
        sessions.groupBy { it.packageName }.mapValues { (_, entries) -> entries.maxByOrNull { it.startedAtMs } }
    }
    val shownGames = remember(games, query, filter, recentRank, favorites) {
        games.asSequence()
            .filter { game ->
                query.isBlank() || game.label.contains(query.trim(), ignoreCase = true) ||
                    game.packageName.contains(query.trim(), ignoreCase = true)
            }
            .filter { game ->
                when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.RECENT -> recentRank.containsKey(game.packageName)
                    LibraryFilter.FAVORITES -> game.packageName in favorites
                }
            }
            .sortedWith(
                compareBy<InstalledGameEntry> { if (filter == LibraryFilter.ALL) recentRank[it.packageName] ?: Int.MAX_VALUE else Int.MAX_VALUE }
                    .thenBy { it.label.lowercase(Locale.ROOT) },
            )
            .toList()
    }

    fun toggleFavorite(packageName: String) {
        val next = if (packageName in favorites) favorites - packageName else favorites + packageName
        favorites = next
        favoriteStore.edit().putStringSet("favorite_packages", next.toSet()).apply()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Game Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Temukan game terpasang, pin favorit, dan buka langsung atau atur profil performanya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Temukan game", watermarkIcon = Icons.Outlined.Search) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Cari nama atau package game") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.values().forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.title) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (loading) "Memindai game…" else "${shownGames.size} game ditampilkan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${games.count { it.packageName in favorites }} favorit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            shownGames.isEmpty() -> SectionCard(title = null, watermarkIcon = Icons.Outlined.Apps) {
                Text(
                    text = when {
                        filter == LibraryFilter.FAVORITES -> "Belum ada favorit. Tekan ikon bintang pada game untuk menyimpannya di sini."
                        filter == LibraryFilter.RECENT -> "Belum ada game terbaru. Buka game lewat Game Library agar riwayat akses tercatat."
                        games.isEmpty() -> "Game dari katalog AetherX belum terdeteksi terpasang di perangkat ini."
                        else -> "Tidak ada game yang cocok dengan pencarian."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(shownGames, key = { it.packageName }) { game ->
                    val recent = game.packageName in recentRank
                    val session: GameSession? = latestSessions[game.packageName]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.23f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                bitmap = game.icon,
                                contentDescription = game.label,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = game.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = game.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val details = when {
                                    session?.averageFps != null -> "Sesi ${formatLibraryDuration(session.durationMs)} · AVG ${session.averageFps} FPS"
                                    session != null -> "Sesi ${formatLibraryDuration(session.durationMs)}"
                                    recent -> "Baru dimainkan lewat AetherX"
                                    else -> "Game terpasang"
                                }
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (recent || game.packageName in favorites) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (recent) LibraryStatusLabel("TERBARU")
                                        if (game.packageName in favorites) LibraryStatusLabel("FAVORIT")
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val opened = GameLaunchTracker.launchAndTrack(appContext, game.packageName)
                                            if (!opened) Toast.makeText(context, "Game tidak memiliki aktivitas untuk dibuka.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Mainkan ${game.label}", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { onConfigureGame(game.packageName) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(Icons.Outlined.Tune, contentDescription = "Atur profil ${game.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { toggleFavorite(game.packageName) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = if (game.packageName in favorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = if (game.packageName in favorites) "Hapus dari favorit" else "Tambahkan ke favorit",
                                        tint = if (game.packageName in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStatusLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatLibraryDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}
