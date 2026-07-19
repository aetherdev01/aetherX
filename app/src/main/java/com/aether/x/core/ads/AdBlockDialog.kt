package com.aether.x.core.ads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R

@Composable
fun AdBlockDialog() {
    val visible by AdBlockDialogState.visible.collectAsStateWithLifecycle()
    if (!visible) return

    val context = LocalContext.current

    AlertDialog(

        onDismissRequest = {},
        title = { Text(stringResource(R.string.adblock_dialog_title)) },
        text = { Text(stringResource(R.string.adblock_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    AdBlockDialogState.dismiss(context)
                    AdBlockDialogState.requestOpenMembership()
                },
            ) { Text(stringResource(R.string.adblock_dialog_membership_button)) }
        },
        dismissButton = {
            TextButton(onClick = { AdBlockDialogState.dismiss(context) }) {
                Text(stringResource(R.string.adblock_dialog_dismiss_button))
            }
        },
    )
}
