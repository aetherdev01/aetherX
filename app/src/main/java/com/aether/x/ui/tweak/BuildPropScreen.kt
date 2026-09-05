package com.aether.x.ui.tweak

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.buildprop.BuildPropBackup
import com.aether.x.core.buildprop.BuildPropEntry
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun BuildPropScreen(
    modifier: Modifier = Modifier,
    viewModel: BuildPropViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val activity = LocalContext.current as? Activity
    var showBackupSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(state.message) {
        state.message?.let {
            delay(3000)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.buildprop_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.weight(1f).padding(bottom = 12.dp, end = 8.dp),
            )
            IconButton(onClick = { showBackupSheet = true }) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = stringResource(R.string.buildprop_backups_cd),
                    tint = AccentBlue,
                )
            }
        }

        val availablePartitions = state.snapshots.filter { it.exists }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availablePartitions.forEach { snapshot ->
                FilterChip(
                    selected = state.selectedPartition == snapshot.partition,
                    onClick = { viewModel.selectPartition(snapshot.partition) },
                    label = { Text(snapshot.partition.displayLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.18f),
                        selectedLabelColor = AccentBlue,
                    ),
                )
            }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.buildprop_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.large,
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
                state.visibleEntries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.buildprop_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.visibleEntries, key = { it.key + it.lineIndex }) { entry ->
                            BuildPropRow(
                                entry = entry,
                                onRequestEdit = { newValue -> viewModel.requestEdit(entry, newValue) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.pendingEdit?.let { pending ->
        EditConfirmDialog(
            key = pending.entry.key,
            oldValue = pending.entry.value,
            newValue = pending.newValue,
            onConfirm = { viewModel.confirmEdit(activity) },
            onDismiss = viewModel::cancelPendingEdit,
        )
    }

    state.pendingRestore?.let { backup ->
        RestoreConfirmDialog(
            backup = backup,
            onConfirm = { viewModel.confirmRestore(activity) },
            onDismiss = viewModel::cancelPendingRestore,
        )
    }

    if (showBackupSheet) {
        BackupListDialog(
            backups = state.backupsForSelectedPartition,
            onRestore = { backup ->
                showBackupSheet = false
                viewModel.requestRestore(backup)
            },
            onDismiss = { showBackupSheet = false },
        )
    }
}

@Composable
private fun BuildPropRow(
    entry: BuildPropEntry,
    onRequestEdit: (String) -> Unit,
) {
    var editing by remember(entry.lineIndex) { mutableStateOf(false) }
    var draftValue by remember(entry.lineIndex, entry.value) { mutableStateOf(entry.value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = entry.key,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        if (editing) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = { draftValue = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = StrokeSubtle,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    editing = false
                    draftValue = entry.value
                }) {
                    Text(stringResource(R.string.buildprop_action_cancel))
                }
                TextButton(onClick = {
                    editing = false
                    onRequestEdit(draftValue)
                }) {
                    Text(stringResource(R.string.buildprop_action_save))
                }
            }
        } else {
            Text(
                text = entry.value.ifBlank { stringResource(R.string.buildprop_value_empty) },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)),
            )
            TextButton(
                onClick = { editing = true },
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(stringResource(R.string.buildprop_action_edit), color = AccentBlue)
            }
        }
    }
}

@Composable
private fun EditConfirmDialog(
    key: String,
    oldValue: String,
    newValue: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PopupDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.WarningAmber,
        iconTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.buildprop_confirm_edit_title),
        message = stringResource(R.string.buildprop_confirm_edit_warning),
        confirmLabel = stringResource(R.string.buildprop_confirm_edit_button),
        onConfirm = onConfirm,
        confirmIsDestructive = true,
        dismissLabel = stringResource(R.string.buildprop_action_cancel),
    ) {
        Text(
            text = stringResource(R.string.buildprop_confirm_edit_diff_format, key, oldValue, newValue),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    backup: BuildPropBackup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PopupDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.History,
        iconTint = AccentBlue,
        title = stringResource(R.string.buildprop_confirm_restore_title),
        message = stringResource(R.string.buildprop_confirm_restore_warning, backup.partition.displayLabel),
        confirmLabel = stringResource(R.string.buildprop_confirm_restore_button),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.buildprop_action_cancel),
    )
}

@Composable
private fun BackupListDialog(
    backups: List<BuildPropBackup>,
    onRestore: (BuildPropBackup) -> Unit,
    onDismiss: () -> Unit,
) {
    PopupDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.History,
        iconTint = AccentBlue,
        title = stringResource(R.string.buildprop_backups_title),
        dismissLabel = stringResource(R.string.buildprop_action_close),
        scrollableContent = true,
    ) {
        if (backups.isEmpty()) {
            Text(stringResource(R.string.buildprop_backups_empty), color = TextMuted)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                backups.forEach { backup ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm").format(java.util.Date(backup.timestampMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        TextButton(onClick = { onRestore(backup) }) {
                            Text(stringResource(R.string.buildprop_action_restore))
                        }
                    }
                }
            }
        }
    }
}
