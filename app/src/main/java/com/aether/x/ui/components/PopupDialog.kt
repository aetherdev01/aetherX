package com.aether.x.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.x.ui.theme.AccentRed

/**
 * PopupDialog — pengganti `androidx.compose.material3.AlertDialog` bawaan
 * di seluruh app ini. AlertDialog default Material3 dipakai sebelumnya di
 * beberapa tempat (ClearCacheConfirmDialog di AppManagerScreen,
 * logout confirm di MembershipScreen, CrosshairColorPickerDialog, dan 3
 * dialog di BuildPropScreen) dan tampil kotak putih flat generik — tidak
 * konsisten dengan gaya "gate" dialog yang sudah lebih baik
 * (MaintenanceGate/UpdateGate: rounded 28dp, icon
 * bulat berwarna, padding lega). PopupDialog menyatukan gaya itu jadi satu
 * komponen reusable supaya SEMUA pop up di app konsisten, dan supaya
 * perubahan gaya di masa depan cukup dilakukan di satu tempat.
 *
 * @param icon ikon bulat di atas title. null kalau dialog tidak butuh
 *   ikon (mis. daftar backup yang lebih fokus ke konten tabel).
 * @param iconTint warna ikon + warna dasar lingkaran latar (dipakai
 *   dengan alpha rendah untuk latar, solid untuk ikonnya sendiri).
 * @param title judul dialog, bold & center — kalau perlu title custom
 *   (mis. rata kiri), pakai overload dengan `titleContent`.
 * @param message isi pesan singkat (opsional) — untuk konten lebih
 *   kompleks (color picker, daftar item), pakai [content] alih-alih ini.
 * @param content slot konten bebas tambahan di bawah [message] (opsional,
 *   dibungkus scroll otomatis kalau tinggi berlebih lewat [scrollableContent]).
 * @param confirmLabel label tombol utama. null menyembunyikan tombol utama.
 * @param onConfirm aksi tombol utama.
 * @param confirmIsDestructive true untuk aksi merusak/tidak bisa diulang
 *   (hapus, logout, restore, dsb.) — tombol utama jadi merah alih-alih
 *   warna primary, sesuai konvensi warning color di BuildPropScreen.
 * @param dismissLabel label tombol sekunder (biasanya "Batal"). null
 *   menyembunyikan tombol sekunder.
 * @param onDismissRequest dipanggil saat tombol sekunder ditekan ATAU
 *   pengguna menekan back/tap di luar dialog (kecuali [dismissOnBackPress]/
 *   [dismissOnClickOutside] di-set false).
 */
@Composable
fun PopupDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String? = null,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmIsDestructive: Boolean = false,
    dismissLabel: String? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    scrollableContent: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (content != null) {
                if (scrollableContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }

            if (confirmLabel != null && onConfirm != null) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmIsDestructive) AccentRed else MaterialTheme.colorScheme.primary,
                        contentColor = if (confirmIsDestructive) Color.White else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(text = confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }

            if (dismissLabel != null) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Varian PopupDialog dengan header rata kiri berdampingan (icon kecil +
 * title dalam satu Row), dipakai untuk dialog yang lebih "teknis"/padat
 * info (mis. konfirmasi edit Build.prop dengan diff key-value, daftar
 * backup) — beda dari [PopupDialog] yang headernya selalu center-aligned
 * dengan icon besar di atas (cocok untuk pop up singkat/pesan utama).
 *
 * Row aksi ditaruh di bawah, rata kanan, mengikuti konvensi AlertDialog
 * sebelumnya (confirmButton di kanan, dismissButton di kiri) supaya
 * migrasi dari AlertDialog lama tidak mengubah kebiasaan pengguna soal
 * posisi tombol.
 */
@Composable
fun PopupDialogPanel(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmIsDestructive: Boolean = false,
    dismissLabel: String? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp),
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }

            if (confirmLabel != null || dismissLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (confirmLabel != null && onConfirm != null) {
                        TextButton(onClick = onConfirm) {
                            Text(
                                text = confirmLabel,
                                fontWeight = FontWeight.SemiBold,
                                color = if (confirmIsDestructive) AccentRed else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
