package com.aether.x.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.components.PopupDialog

/**
 * Sheet "Apa yang Baru" — muncul SEKALI setelah app di-update ke versi
 * baru (lihat [WhatsNewViewModel] untuk logika kapan tampil). Item
 * changelog di sini di-hardcode per rilis (bukan diambil dari server)
 * karena ini recap versi yang SEDANG dijalankan user sekarang, bukan
 * pengumuman versi baru yang tersedia — beda tujuan dari
 * [com.aether.x.ui.update.UpdateGate].
 */
@Composable
fun WhatsNewDialog(viewModel: WhatsNewViewModel = viewModel()) {
    val shouldShow by viewModel.shouldShow.collectAsState()
    if (!shouldShow) return

    PopupDialog(
        onDismissRequest = viewModel::dismiss,
        icon = Icons.Outlined.Celebration,
        title = stringResource(R.string.whatsnew_title, BuildConfig.VERSION_NAME),
        confirmLabel = stringResource(R.string.whatsnew_confirm),
        onConfirm = viewModel::dismiss,
        scrollableContent = true,
    ) {
        val items = listOf(
            R.string.whatsnew_item_game_booster,
            R.string.whatsnew_item_kernel_preset,
            R.string.whatsnew_item_theme,
            R.string.whatsnew_item_dashboard_order,
            R.string.whatsnew_item_quick_tile,
            R.string.whatsnew_item_widget,
            R.string.whatsnew_item_notif_action,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { resId ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = stringResource(resId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
