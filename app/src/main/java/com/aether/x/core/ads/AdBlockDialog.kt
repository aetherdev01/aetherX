package com.aether.x.core.ads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.theme.AccentAmber

@Composable
fun AdBlockDialog() {
    val visible by AdBlockDialogState.visible.collectAsStateWithLifecycle()
    if (!visible) return

    val context = LocalContext.current

    PopupDialog(
        onDismissRequest = { AdBlockDialogState.dismiss(context) },
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        icon = Icons.Outlined.Shield,
        iconTint = AccentAmber,
        title = stringResource(R.string.adblock_dialog_title),
        message = stringResource(R.string.adblock_dialog_message),
        confirmLabel = stringResource(R.string.adblock_dialog_membership_button),
        onConfirm = {
            AdBlockDialogState.dismiss(context)
            AdBlockDialogState.requestOpenMembership()
        },
        dismissLabel = stringResource(R.string.adblock_dialog_dismiss_button),
    )
}
