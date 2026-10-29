package com.aether.x.ui.tweak

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
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
import com.aether.x.ui.components.aetherCardSurface
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale
import com.aether.x.core.locale.str
import com.aether.x.R

private enum class LibraryFilter(@StringRes val titleRes: Int) {
    ALL(R.string.game_library_filter_all), RECENT(R.string.game_library_filter_recent), FAVORITES(R.string.game_library_filter_favorites)
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
                text = str(R.string.game_library_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = str(R.string.game_library_temukan_game_terpasang_pin_favorit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = str(R.string.game_library_temukan_game), watermarkIcon = Icons.Outlined.Search) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(str(R.string.game_library_cari_nama_atau_package_game)) },
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
                        label = { Text(str(item.titleRes)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (loading) str(R.string.game_library_memindai_game) else str(R.string.game_library_game_ditampilkan, shownGames.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = str(R.string.game_library_favorit, games.count { it.packageName in favorites }),
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
                        filter == LibraryFilter.FAVORITES -> str(R.string.game_library_belum_ada_favorit_tekan_ikon)
                        filter == LibraryFilter.RECENT -> str(R.string.game_library_belum_ada_game_terbaru_buka)
                        games.isEmpty() -> str(R.string.game_library_game_dari_katalog_aetherx_belum)
                        else -> str(R.string.game_library_tidak_ada_game_yang_cocok)
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
                        modifier = Modifier.fillMaxWidth().aetherCardSurface(shape = RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
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
                                    session?.averageFps != null -> str(R.string.game_library_sesi_avg_fps, formatLibraryDuration(session.durationMs), session.averageFps)
                                    session != null -> str(R.string.game_library_sesi, formatLibraryDuration(session.durationMs))
                                    recent -> str(R.string.game_library_baru_dimainkan_lewat_aetherx)
                                    else -> str(R.string.game_library_game_terpasang)
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
                                        if (recent) LibraryStatusLabel(str(R.string.label_latest))
                                        if (game.packageName in favorites) LibraryStatusLabel("FAVORIT")
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val opened = GameLaunchTracker.launchAndTrack(appContext, game.packageName)
                                            if (!opened) Toast.makeText(context, str(R.string.game_library_game_tidak_memiliki_aktivitas_untuk), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = str(R.string.game_library_mainkan, game.label), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { onConfigureGame(game.packageName) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(Icons.Outlined.Tune, contentDescription = str(R.string.game_library_atur_profil, game.label), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { toggleFavorite(game.packageName) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = if (game.packageName in favorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = if (game.packageName in favorites) str(R.string.game_library_hapus_dari_favorit) else str(R.string.game_library_tambahkan_ke_favorit),
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
