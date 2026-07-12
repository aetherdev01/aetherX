package com.aether.x.core.ads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.R

/**
 * Composable dialog adblock — lihat KDoc [AdBlockDialogState] soal
 * arsitektur lengkapnya. Dipasang SEKALI di
 * [com.aether.x.ui.main.MainScreen] (bukan di dalam salah satu tab),
 * supaya tetap bisa tampil terlepas tab mana yang aktif saat
 * [AdBlockDialogState.visible] jadi true.
 */
@Composable
fun AdBlockDialog() {
    val visible by AdBlockDialogState.visible.collectAsStateWithLifecycle()
    if (!visible) return

    AlertDialog(
        // Kosong SENGAJA (BUKAN lupa) — sesuai pilihan presentasi "Dialog
        // (harus ditutup dulu, lebih tegas)": tap-di-luar dialog atau
        // tombol back TIDAK menutup dialog ini, pengguna WAJIB memilih
        // salah satu tombol di bawah.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.adblock_dialog_title)) },
        text = { Text(stringResource(R.string.adblock_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    AdBlockDialogState.dismiss()
                    AdBlockDialogState.requestOpenMembership()
                },
            ) { Text(stringResource(R.string.adblock_dialog_membership_button)) }
        },
        dismissButton = {
            TextButton(onClick = { AdBlockDialogState.dismiss() }) {
                Text(stringResource(R.string.adblock_dialog_dismiss_button))
            }
        },
    )
}
