package com.aether.x.ui.tweak

import androidx.annotation.StringRes
import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.showAetherToast
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.launch
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * v3.6 — Advanced -> Power Menu (khusus Root).
 *
 * Reboot biasa, soft reboot, restart SystemUI, reboot ke Recovery / Bootloader /
 * Download Mode (Odin), dan matikan perangkat. Semua aksi lewat dialog
 * konfirmasi dan dijalankan memakai shell root yang sama dengan fitur lain.
 */
private enum class PowerAction(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val command: String,
    @StringRes val confirmTitle: Int,
    @StringRes val confirmMessage: Int,
    val destructive: Boolean = false,
) {
    REBOOT(
        title = R.string.power_reboot_title,
        description = R.string.power_reboot_desc,
        icon = Icons.Outlined.RestartAlt,
        command = "sync; svc power reboot || reboot",
        confirmTitle = R.string.power_reboot_confirm_title,
        confirmMessage = R.string.power_reboot_confirm_msg,
    ),
    SOFT_REBOOT(
        title = R.string.power_soft_reboot_title,
        description = R.string.power_soft_reboot_desc,
        icon = Icons.Outlined.Refresh,
        command = "setprop ctl.restart zygote",
        confirmTitle = R.string.power_soft_reboot_confirm_title,
        confirmMessage = R.string.power_soft_reboot_confirm_msg,
    ),
    SYSTEMUI(
        title = R.string.power_systemui_title,
        description = R.string.power_systemui_desc,
        icon = Icons.Outlined.PhoneAndroid,
        command = "killall com.android.systemui || pkill -f com.android.systemui",
        confirmTitle = R.string.power_systemui_confirm_title,
        confirmMessage = R.string.power_systemui_confirm_msg,
    ),
    RECOVERY(
        title = R.string.power_recovery_title,
        description = R.string.power_recovery_desc,
        icon = Icons.Outlined.SettingsBackupRestore,
        command = "sync; reboot recovery",
        confirmTitle = R.string.power_recovery_confirm_title,
        confirmMessage = R.string.power_recovery_confirm_msg,
    ),
    BOOTLOADER(
        title = R.string.power_bootloader_title,
        description = R.string.power_bootloader_desc,
        icon = Icons.Outlined.DeveloperBoard,
        command = "sync; reboot bootloader",
        confirmTitle = R.string.power_bootloader_confirm_title,
        confirmMessage = R.string.power_bootloader_confirm_msg,
    ),
    DOWNLOAD(
        title = R.string.power_download_title,
        description = R.string.power_download_desc,
        icon = Icons.Outlined.Download,
        command = "sync; reboot download",
        confirmTitle = R.string.power_download_confirm_title,
        confirmMessage = R.string.power_download_confirm_msg,
    ),
    POWER_OFF(
        title = R.string.power_off_title,
        description = R.string.power_off_desc,
        icon = Icons.Outlined.PowerSettingsNew,
        command = "sync; reboot -p || svc power shutdown",
        confirmTitle = R.string.power_off_confirm_title,
        confirmMessage = R.string.power_off_confirm_msg,
        destructive = true,
    ),
}

@Composable
fun PowerMenuScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PowerAction?>(null) }

    pending?.let { action ->
        PopupDialog(
            onDismissRequest = { pending = null },
            icon = action.icon,
            iconTint = if (action.destructive) {
                com.aether.x.ui.theme.AccentRed
            } else {
                MaterialTheme.colorScheme.primary
            },
            title = str(action.confirmTitle),
            message = str(action.confirmMessage),
            confirmLabel = str(R.string.power_menu_ya_lanjutkan),
            confirmIsDestructive = action.destructive,
            onConfirm = {
                pending = null
                scope.launch {
                    val executor = PrivilegeManager.getExecutorAwaitingConnection()
                    if (executor == null) {
                        context.showAetherToast(str(R.string.kernel_manager_error_root_unavailable))
                    } else {
                        // Perintah reboot mematikan proses ini sebelum exec() sempat
                        // kembali; hasilnya memang tidak ditunggu.
                        executor.exec(action.command)
                    }
                }
            },
            dismissLabel = str(R.string.membership_logout_cancel),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SectionCard(title = str(R.string.power_menu_restart), watermarkIcon = Icons.Outlined.RestartAlt) {
            PowerRow(PowerAction.REBOOT) { pending = it }
            PowerRow(PowerAction.SOFT_REBOOT) { pending = it }
            PowerRow(PowerAction.SYSTEMUI) { pending = it }
        }
        SectionCard(title = str(R.string.power_menu_mode_boot), watermarkIcon = Icons.Outlined.SettingsBackupRestore) {
            PowerRow(PowerAction.RECOVERY) { pending = it }
            PowerRow(PowerAction.BOOTLOADER) { pending = it }
            PowerRow(PowerAction.DOWNLOAD) { pending = it }
        }
        SectionCard(
            title = str(R.string.overlay_notification_action_stop),
            watermarkIcon = Icons.Outlined.PowerSettingsNew,
            accent = com.aether.x.ui.theme.AccentRed,
        ) {
            PowerRow(PowerAction.POWER_OFF) { pending = it }
        }
    }
}

@Composable
private fun PowerRow(action: PowerAction, onClick: (PowerAction) -> Unit) {
    val tint = if (action.destructive) com.aether.x.ui.theme.AccentRed else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(16.dp))
            .clickable { onClick(action) }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = str(action.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = str(action.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
