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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentBlueDim
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted

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

    // Notifikasi SISTEM (tray Android, terpisah dari dialog in-app di bawah
    // — FITUR BARU, lihat perintah rework) dipicu setiap kali versi terbaru
    // yang terdeteksi BERUBAH (key = latestVersionCode), bukan setiap kali
    // recomposition biasa — supaya tidak spam notifikasi berulang selama
    // pengguna berada di layar yang sama dengan versi yang sama.
    LaunchedEffect(state.info.latestVersionCode) {
        if (state.visible && state.info.latestVersionCode > state.currentVersionCode) {
            AetherXNotifier.notify(
                context = context,
                kind = AetherXNotifier.NotificationKind.UPDATE,
                title = context.getString(R.string.notif_update_title),
                text = context.getString(R.string.notif_update_text_format, state.info.latestVersionName),
            )
        }
    }

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
                    imageVector = Icons.Outlined.RocketLaunch,
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
                VersionTransitionRow(
                    currentVersionName = state.currentVersionName,
                    newVersionName = state.info.latestVersionName,
                )
            }

            // Garis pembatas: menutup blok info versi (ikon + judul + versi)
            // dan membuka blok deskripsi/changelog di bawahnya, supaya kedua
            // area itu terasa terpisah secara visual alih-alih menyatu.
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = StrokeSubtle,
            )

            if (state.info.description.isNotBlank()) {
                UpdateDescriptionBlock(description = state.info.description)

                // Garis pembatas: menutup blok deskripsi/changelog dan
                // memisahkannya secara visual dari area aksi (tombol
                // download + "Nanti") tepat di bawahnya.
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = StrokeSubtle,
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

/**
 * Baris "v1.5 → v2.0": versi yang sedang terpasang (redup/muted) di kiri,
 * panah di tengah, versi baru (aksen biru, ditekankan) di kanan. Dipakai
 * di [UpdateGate] supaya pengguna langsung paham dari-versi-berapa dia
 * akan pindah, bukan cuma melihat angka versi tujuan saja.
 *
 * Kalau nama versi saat ini tidak diketahui (mis. `BuildConfig.VERSION_NAME`
 * kosong pada build tertentu), baris ini turun jadi tampilan lama: hanya
 * versi tujuan, tanpa panah — supaya tidak menampilkan "→ v2.0" yang
 * menggantung tanpa asal.
 */
@Composable
private fun VersionTransitionRow(currentVersionName: String, newVersionName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currentVersionName.isNotBlank()) {
            Text(
                text = stringResource(R.string.update_version_label, currentVersionName),
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = stringResource(R.string.update_version_label, newVersionName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tinggi maksimum blok deskripsi sebelum konten bisa di-scroll. */
private val DESCRIPTION_MAX_HEIGHT = 180.dp

/**
 * Blok deskripsi/changelog di [UpdateGate]. Menerapkan markdown ringan lewat
 * [parseUpdateDescription] (bold/italic/warna/bullet). Tinggi dibatasi ke
 * [DESCRIPTION_MAX_HEIGHT] dan selalu bisa di-scroll kalau kontennya lebih
 * panjang dari itu — tidak ada lagi mode expand/collapse maupun tombol
 * "Selengkapnya"/"Sembunyikan"; scroll saja sudah cukup untuk melihat
 * seluruh isi deskripsi.
 */
@Composable
private fun UpdateDescriptionBlock(description: String) {
    val lines = parseUpdateDescription(description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DESCRIPTION_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lines.forEach { line ->
            if (line.isBullet) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentBlue,
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
