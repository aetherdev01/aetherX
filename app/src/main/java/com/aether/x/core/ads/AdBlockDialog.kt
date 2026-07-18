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

    val context = LocalContext.current

    AlertDialog(
        // Kosong SENGAJA (BUKAN lupa) — sesuai pilihan presentasi "Dialog
        // (harus ditutup dulu, lebih tegas)": tap-di-luar dialog atau
        // tombol back TIDAK menutup dialog ini, pengguna WAJIB memilih
        // salah satu tombol di bawah. INI TIDAK SAMA DENGAN "tidak bisa
        // ditutup" — kedua tombol di bawah SELALU menutup dialog ini
        // (lihat [AdBlockDialogState.dismiss], yang sekarang JUGA
        // menyimpan penutupan ini secara permanen supaya dialog TIDAK
        // muncul lagi di sesi berikutnya untuk sinyal adblock yang sama).
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
