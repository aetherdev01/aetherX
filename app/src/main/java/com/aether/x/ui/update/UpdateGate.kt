package com.aether.x.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim

/**
 * Dipasang SEKALI di root aplikasi (lihat [com.aether.x.MainActivity]),
 * mengikuti pola [com.aether.x.ui.maintenance.MaintenanceGate] — supaya
 * dialog bisa muncul di layar mana pun begitu admin publish versi baru
 * lewat bot Telegram (menu "🚀 Update Versi").
 *
 * BERBEDA dari MaintenanceGate: dialog ini SELALU BISA di-dismiss (tombol
 * back, tap di luar, atau tombol "Nanti") — update di AetherX sepenuhnya
 * opsional, tidak pernah memblokir pemakaian aplikasi. Field `mandatory` di
 * Firestore disiapkan untuk kebutuhan masa depan tapi belum memengaruhi
 * perilaku gate ini.
 */
@Composable
fun UpdateGate(viewModel: UpdateViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    if (!state.visible) return

    Dialog(
        onDismissRequest = { viewModel.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentBlueDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(32.dp),
                )
            }

            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (state.info.latestVersionName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_version_label, state.info.latestVersionName),
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBlue,
                    textAlign = TextAlign.Center,
                )
            }

            if (state.info.description.isNotBlank()) {
                Text(
                    text = state.info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            Button(
                onClick = {
                    if (state.info.downloadUrl.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.info.downloadUrl))
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Tidak ada browser yang bisa menangani link ini — abaikan
                            // dengan aman, dialog tetap tampil supaya pengguna bisa
                            // coba lagi atau salin link secara manual nanti.
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.update_download_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            TextButton(onClick = { viewModel.dismiss() }) {
                Text(
                    text = stringResource(R.string.update_later_button),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
