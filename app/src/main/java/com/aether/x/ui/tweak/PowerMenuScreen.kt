package com.aether.x.ui.tweak

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.showAetherToast
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * v3.6 — Advanced -> Power Menu (khusus Root).
 *
 * Reboot biasa, soft reboot, restart SystemUI, reboot ke Recovery / Bootloader /
 * Download Mode (Odin), dan matikan perangkat. Semua aksi lewat dialog
 * konfirmasi dan dijalankan memakai shell root yang sama dengan fitur lain.
 */
private enum class PowerAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val command: String,
    val confirmTitle: String,
    val confirmMessage: String,
    val destructive: Boolean = false,
) {
    REBOOT(
        title = "Reboot",
        description = "Mulai ulang perangkat seperti biasa",
        icon = Icons.Outlined.RestartAlt,
        command = "sync; svc power reboot || reboot",
        confirmTitle = "Reboot perangkat?",
        confirmMessage = "Semua aplikasi akan ditutup dan perangkat dimulai ulang.",
    ),
    SOFT_REBOOT(
        title = "Soft Reboot",
        description = "Restart framework Android tanpa mematikan kernel (lebih cepat)",
        icon = Icons.Outlined.Refresh,
        command = "setprop ctl.restart zygote",
        confirmTitle = "Soft reboot?",
        confirmMessage = "Framework Android dimulai ulang. Layar akan sesaat kembali ke logo boot.",
    ),
    SYSTEMUI(
        title = "Restart SystemUI",
        description = "Muat ulang status bar & panel notifikasi",
        icon = Icons.Outlined.PhoneAndroid,
        command = "killall com.android.systemui || pkill -f com.android.systemui",
        confirmTitle = "Restart SystemUI?",
        confirmMessage = "Status bar dan panel notifikasi dimuat ulang sesaat.",
    ),
    RECOVERY(
        title = "Reboot Recovery",
        description = "Mulai ulang langsung ke mode Recovery",
        icon = Icons.Outlined.SettingsBackupRestore,
        command = "sync; reboot recovery",
        confirmTitle = "Reboot ke Recovery?",
        confirmMessage = "Perangkat dimulai ulang ke mode Recovery. Pastikan kamu tahu cara keluar dari Recovery.",
    ),
    BOOTLOADER(
        title = "Reboot Bootloader",
        description = "Mulai ulang ke bootloader / fastboot",
        icon = Icons.Outlined.DeveloperBoard,
        command = "sync; reboot bootloader",
        confirmTitle = "Reboot ke Bootloader?",
        confirmMessage = "Perangkat dimulai ulang ke mode bootloader (fastboot).",
    ),
    DOWNLOAD(
        title = "Download Mode",
        description = "Mode Odin untuk perangkat Samsung",
        icon = Icons.Outlined.Download,
        command = "sync; reboot download",
        confirmTitle = "Reboot ke Download Mode?",
        confirmMessage = "Hanya berlaku di perangkat Samsung. Di perangkat lain perintah ini bisa diabaikan.",
    ),
    POWER_OFF(
        title = "Matikan",
        description = "Matikan perangkat sepenuhnya",
        icon = Icons.Outlined.PowerSettingsNew,
        command = "sync; reboot -p || svc power shutdown",
        confirmTitle = "Matikan perangkat?",
        confirmMessage = "Perangkat akan dimatikan. Kamu perlu menyalakannya lagi secara manual.",
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
            title = action.confirmTitle,
            message = action.confirmMessage,
            confirmLabel = "Ya, lanjutkan",
            confirmIsDestructive = action.destructive,
            onConfirm = {
                pending = null
                scope.launch {
                    val executor = PrivilegeManager.getExecutorAwaitingConnection()
                    if (executor == null) {
                        context.showAetherToast("Akses root tidak tersedia")
                    } else {
                        // Perintah reboot mematikan proses ini sebelum exec() sempat
                        // kembali; hasilnya memang tidak ditunggu.
                        executor.exec(action.command)
                    }
                }
            },
            dismissLabel = "Batal",
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SectionCard(title = "Restart", watermarkIcon = Icons.Outlined.RestartAlt) {
            PowerRow(PowerAction.REBOOT) { pending = it }
            PowerRow(PowerAction.SOFT_REBOOT) { pending = it }
            PowerRow(PowerAction.SYSTEMUI) { pending = it }
        }
        SectionCard(title = "Mode Boot", watermarkIcon = Icons.Outlined.SettingsBackupRestore) {
            PowerRow(PowerAction.RECOVERY) { pending = it }
            PowerRow(PowerAction.BOOTLOADER) { pending = it }
            PowerRow(PowerAction.DOWNLOAD) { pending = it }
        }
        SectionCard(
            title = "Matikan",
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
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
                text = action.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = action.description,
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
