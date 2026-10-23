package com.aether.x.ui.appmanager

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.appmanager.AppOrigin
import com.aether.x.core.appmanager.InstalledAppEntry
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import com.aether.x.core.locale.str

@Composable
fun AppManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: AppManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val activity = LocalContext.current as? Activity

    var pendingClearCacheEntry by remember { mutableStateOf<InstalledAppEntry?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            delay(3000)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppManagerOverviewCard(
            state = state,
            onRefresh = viewModel::refresh,
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.app_manager_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = StrokeSubtle,
            ),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (thirdParty.isNotEmpty()) {
                            item(key = "header_third_party") {
                                AppManagerSectionHeader(
                                    title = stringResource(R.string.app_manager_section_third_party),
                                    count = thirdParty.size,
                                )
                            }
                            items(thirdParty, key = { it.packageName }) { entry ->
                                AppManagerRow(
                                    entry = entry,
                                    isPending = entry.packageName == state.pendingPackageName,
                                    onToggle = { viewModel.toggleFreeze(entry, activity) },
                                    onForceStop = { viewModel.forceStopApp(entry, activity) },
                                    onRequestClearCache = { pendingClearCacheEntry = entry },
                                )
                            }
                        }
                        if (bloatware.isNotEmpty()) {
                            item(key = "header_bloatware") {
                                AppManagerSectionHeader(
                                    title = stringResource(R.string.app_manager_section_bloatware),
                                    count = bloatware.size,
                                )
                            }
                            items(bloatware, key = { it.packageName }) { entry ->
                                AppManagerRow(
                                    entry = entry,
                                    isPending = entry.packageName == state.pendingPackageName,
                                    onToggle = { viewModel.toggleFreeze(entry, activity) },
                                    onForceStop = { viewModel.forceStopApp(entry, activity) },
                                    onRequestClearCache = { pendingClearCacheEntry = entry },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingClearCacheEntry?.let { entry ->
        ClearCacheConfirmDialog(
            appLabel = entry.label,
            onConfirm = {
                viewModel.clearCacheApp(entry, activity)
                pendingClearCacheEntry = null
            },
            onDismiss = { pendingClearCacheEntry = null },
        )
    }
}

@Composable
private fun ClearCacheConfirmDialog(
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PopupDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.DeleteSweep,
        iconTint = AccentBlue,
        title = stringResource(R.string.app_manager_clear_cache_confirm_title),
        message = stringResource(R.string.app_manager_clear_cache_confirm_desc, appLabel),
        confirmLabel = stringResource(R.string.app_manager_clear_cache_confirm_action),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.crosshair_custom_color_cancel),
    )
}

@Composable
private fun AppManagerOverviewCard(
    state: AppManagerUiState,
    onRefresh: () -> Unit,
) {
    val frozenCount = state.apps.count { it.isFrozen }
    val bloatwareCount = state.apps.count { it.origin == AppOrigin.KNOWN_BLOATWARE }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = MaterialTheme.shapes.extraLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "App Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.app_manager_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = str(R.string.app_manager_muat_ulang_daftar_aplikasi),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppManagerMetric(
                value = state.apps.size.toString(),
                label = str(R.string.app_manager_dikelola),
                modifier = Modifier.weight(1f),
            )
            AppManagerMetric(
                value = frozenCount.toString(),
                label = str(R.string.app_manager_dibekukan),
                modifier = Modifier.weight(1f),
            )
            AppManagerMetric(
                value = bloatwareCount.toString(),
                label = "Bloatware",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Gunakan menu aplikasi untuk hentikan proses atau bersihkan cache. Sakelar membekukan aplikasi; aktifkan kembali bila aplikasi dibutuhkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppManagerMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .aetherCardSurface(shape = MaterialTheme.shapes.large)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AppManagerSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
        )
        StatusPill(
            text = count.toString(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppManagerRow(
    entry: InstalledAppEntry,
    isPending: Boolean,
    onToggle: () -> Unit,
    onForceStop: () -> Unit,
    onRequestClearCache: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
            )
            StatusPill(
                text = if (entry.isFrozen) {
                    stringResource(R.string.app_manager_status_frozen)
                } else {
                    stringResource(R.string.app_manager_status_active)
                },
                containerColor = if (entry.isFrozen) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = if (entry.isFrozen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, enabled = !isPending) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.app_manager_more_options_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_manager_action_force_stop)) },
                    leadingIcon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onForceStop()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_manager_action_clear_cache)) },
                    leadingIcon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRequestClearCache()
                    },
                )
            }
        }

        Switch(
            checked = !entry.isFrozen,
            onCheckedChange = { onToggle() },
            enabled = !isPending,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
        )
    }
}
