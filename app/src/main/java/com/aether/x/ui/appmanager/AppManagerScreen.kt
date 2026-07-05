package com.aether.x.ui.appmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.appmanager.AppOrigin
import com.aether.x.core.appmanager.InstalledAppEntry
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Layar App Manager: freeze/unfreeze aplikasi PIHAK KETIGA (semua) dan
 * aplikasi sistem yang cocok whitelist bloatware terkurasi (lihat KDoc
 * [com.aether.x.core.appmanager.AppManagerCatalog] untuk alasan kenapa
 * TIDAK menampilkan semua app sistem secara bebas).
 *
 * Dipasang di drawer TweakScreen sebagai item terpisah, khusus backend
 * Root — mengikuti pola yang sama seperti KernelManagerSection/
 * GameProfileScreen (masing-masing ViewModel & layar sendiri, bukan
 * ditumpuk ke TweakViewModel).
 *
 * Struktur visual (search field + LazyColumn item row dengan ikon 44dp)
 * SENGAJA meniru [com.aether.x.ui.tweak.GameProfileScreen] persis supaya
 * konsisten secara visual dengan sub-halaman Root lain di drawer yang sama.
 */
@Composable
fun AppManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: AppManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            delay(3000)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.app_manager_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.app_manager_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = StrokeSubtle,
            ),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                state.filteredApps.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.app_manager_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
                else -> {
                    val thirdParty = state.filteredApps.filter { it.origin == AppOrigin.THIRD_PARTY }
                    val bloatware = state.filteredApps.filter { it.origin == AppOrigin.KNOWN_BLOATWARE }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (thirdParty.isNotEmpty()) {
                            item(key = "header_third_party") {
                                AppManagerSectionHeader(stringResource(R.string.app_manager_section_third_party))
                            }
                            items(thirdParty, key = { it.packageName }) { entry ->
                                AppManagerRow(
                                    entry = entry,
                                    isPending = entry.packageName == state.pendingPackageName,
                                    onToggle = { viewModel.toggleFreeze(entry) },
                                )
                            }
                        }
                        if (bloatware.isNotEmpty()) {
                            item(key = "header_bloatware") {
                                AppManagerSectionHeader(stringResource(R.string.app_manager_section_bloatware))
                            }
                            items(bloatware, key = { it.packageName }) { entry ->
                                AppManagerRow(
                                    entry = entry,
                                    isPending = entry.packageName == state.pendingPackageName,
                                    onToggle = { viewModel.toggleFreeze(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppManagerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TextMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun AppManagerRow(
    entry: InstalledAppEntry,
    isPending: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                text = if (entry.isFrozen) {
                    stringResource(R.string.app_manager_status_frozen)
                } else {
                    stringResource(R.string.app_manager_status_active)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.isFrozen) TextMuted else TextSecondary,
            )
        }
        // isFrozen == true berarti Switch OFF (app dinonaktifkan), isFrozen
        // == false berarti Switch ON (app aktif normal) — arah switch
        // mengikuti makna "aktif", bukan makna "frozen", supaya lebih
        // intuitif bagi pengguna awam (ON = jalan seperti biasa).
        Switch(
            checked = !entry.isFrozen,
            onCheckedChange = { onToggle() },
            enabled = !isPending,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
        )
    }
}
