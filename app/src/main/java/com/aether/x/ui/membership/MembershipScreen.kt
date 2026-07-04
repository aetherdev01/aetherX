package com.aether.x.ui.membership

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentAmberContainer
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentGreenContainer
import com.aether.x.ui.theme.AccentRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tab Membership tersendiri di bottom navigation — sebelumnya berupa satu
 * kartu di dalam tab Pengaturan (lihat riwayat [MembershipViewModel]),
 * sekarang jadi layar penuh dengan hero status card + form aktivasi + daftar
 * keuntungan, supaya lebih jelas terpisah dari pengaturan umum aplikasi dan
 * lebih rapi secara visual (badge & warna teks konsisten dengan palet resmi
 * di ui/theme/Color.kt, bukan lagi warna mentah yang ditulis inline).
 */
@Composable
fun MembershipScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MembershipViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val expiresAtMillis by viewModel.expiresAtMillis.collectAsStateWithLifecycle()
    val keyInput by viewModel.keyInput.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val activationStage by viewModel.activationStage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.membership_headline),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.membership_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        MembershipHeroCard(status = status, expiresAtMillis = expiresAtMillis)

        if (status != MembershipUiStatus.ACTIVE) {
            SectionCard(title = stringResource(R.string.membership_key_label)) {
                // Kode lisensi diperlakukan seperti sandi: tersembunyi (•••) secara
                // default supaya tidak "bocor" kelihatan orang lain lewat bahu
                // (shoulder-surfing) saat diketik di tempat umum, dengan ikon mata
                // di ujung kanan field untuk show/hide sesuai kebutuhan pengguna.
                var isKeyVisible by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = viewModel::setKeyInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.membership_key_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    enabled = !isSubmitting,
                    // Format lisensi sekarang bebas: huruf besar/kecil dan angka apa
                    // pun (tidak dipaksa satu pola AETX-XXXX-XXXX-XXXX saja seperti
                    // sebelumnya). Kapitalisasi keyboard "Sentences" dipakai murni
                    // supaya keyboard tidak otomatis mengubah huruf jadi UPPERCASE —
                    // apa pun yang diketik pengguna disimpan apa adanya.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    visualTransformation = if (isKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (isKeyVisible) {
                                        R.string.membership_key_hide_cd
                                    } else {
                                        R.string.membership_key_show_cd
                                    },
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        errorBorderColor = AccentRed,
                    ),
                )

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                    )
                }

                Button(
                    onClick = viewModel::activate,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    // Crossfade dikunci ke pasangan (isSubmitting, stage) supaya
                    // bukan cuma transisi teks<->spinner yang halus, tapi juga
                    // perpindahan ANTAR tahap (mis. "Menghubungkan..." ->
                    // "Memverifikasi...") ikut fade, bukan berganti mendadak.
                    // Label tahap mengikuti progres NYATA di MembershipViewModel
                    // (guard lokal -> transaksi Firestore -> evaluasi hasil),
                    // bukan animasi berbasis delay buatan.
                    Crossfade(
                        targetState = isSubmitting to activationStage,
                        label = "membership_activate_button_state",
                    ) { (submitting, stage) ->
                        if (submitting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(
                                        when (stage) {
                                            ActivationStage.CHECKING_GUARD -> R.string.membership_activate_stage_checking_guard
                                            ActivationStage.CONNECTING -> R.string.membership_activate_stage_connecting
                                            ActivationStage.VERIFYING -> R.string.membership_activate_stage_verifying
                                        },
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.membership_activate_button),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.membership_benefits_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BenefitRow(text = stringResource(R.string.membership_benefit_1))
                BenefitRow(text = stringResource(R.string.membership_benefit_2))
                BenefitRow(text = stringResource(R.string.membership_benefit_3))
            }
        }

        // Begitu membership AKTIF, kartu promo "Langganan Membership Pro"
        // tidak relevan lagi (pengguna sudah berlangganan) — disembunyikan,
        // digantikan kartu Device ID + tombol Logout supaya pengguna tetap
        // punya cara melihat identitas perangkat yang terkunci ke lisensinya
        // dan (kalau perlu) melepas sesi lisensi ini dari perangkat.
        if (status == MembershipUiStatus.ACTIVE) {
            DeviceAccountCard(deviceId = viewModel.deviceId, onLogout = viewModel::logout)
        } else {
            MembershipProCard()
        }
    }
}

/**
 * Kartu promo "Langganan Membership Pro": harga tetap Rp10.000 dan tombol
 * yang membuka chat Telegram admin (bukan pembelian/aktivasi otomatis di
 * dalam app — kode lisensi tetap diberikan manual oleh admin lewat bot
 * setelah pembeli menghubungi lewat tautan ini, sesuai alur yang sudah ada
 * di README bot Telegram).
 */
@Composable
private fun MembershipProCard() {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.membership_pro_telegram_url)

    SectionCard(title = stringResource(R.string.membership_pro_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.membership_pro_price),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AccentBlue,
                )
                Text(
                    text = stringResource(R.string.membership_pro_price_period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Text(
                text = stringResource(R.string.membership_pro_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // Tidak ada aplikasi/browser yang bisa menangani tautan Telegram —
                        // abaikan dengan aman daripada membuat aplikasi crash. Pesan
                        // membership_pro_telegram_error tersedia kalau nanti mau
                        // ditampilkan lewat Snackbar/Toast di sini.
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF29A9EA),
                    contentColor = Color.White,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_social_telegram),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = stringResource(R.string.membership_pro_cta),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Kartu identitas perangkat, ditampilkan sebagai pengganti promo "Langganan
 * Membership Pro" begitu membership perangkat ini aktif — menampilkan Device
 * ID (ANDROID_ID, sama seperti yang dikunci ke lisensi ini di Firestore) dan
 * tombol Logout untuk melepas cache lisensi lokal dari perangkat ini.
 */
@Composable
private fun DeviceAccountCard(deviceId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.membership_device_section_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.membership_device_id_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(deviceId))
                            Toast.makeText(
                                context,
                                context.getString(R.string.membership_device_id_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.membership_device_id_copy_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = AccentRed,
                ),
            ) {
                Text(
                    text = stringResource(R.string.membership_logout_button),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.membership_logout_confirm_title)) },
            text = { Text(stringResource(R.string.membership_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                ) {
                    Text(stringResource(R.string.membership_logout_confirm_action), color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.membership_logout_cancel))
                }
            },
        )
    }
}

/**
 * Kartu hero besar di puncak tab Membership: ikon mahkota, badge status warna
 * konsisten (hijau = aktif, kuning = kedaluwarsa, netral = belum aktif), dan
 * subjudul tanggal berlaku/berakhir.
 */
@Composable
private fun MembershipHeroCard(status: MembershipUiStatus, expiresAtMillis: Long?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            MembershipIcon(status)
            StatusBadge(status)
        }

        Column {
            Text(
                text = statusHeadline(status),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = statusSubtitle(status, expiresAtMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MembershipIcon(status: MembershipUiStatus) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(iconContainerColor(status)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.WorkspacePremium,
            contentDescription = null,
            tint = iconTintColor(status),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBadge(status: MembershipUiStatus) {
    when (status) {
        MembershipUiStatus.CHECKING -> Spacer(modifier = Modifier.height(1.dp))
        MembershipUiStatus.ACTIVE -> StatusPill(
            text = stringResource(R.string.membership_badge_active),
            containerColor = AccentGreenContainer,
            contentColor = AccentGreen,
            dotColor = AccentGreen,
        )
        MembershipUiStatus.INACTIVE -> StatusPill(
            text = stringResource(R.string.membership_badge_inactive),
            dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MembershipUiStatus.EXPIRED -> StatusPill(
            text = stringResource(R.string.membership_badge_expired),
            containerColor = AccentAmberContainer,
            contentColor = AccentAmber,
            dotColor = AccentAmber,
        )
    }
}

@Composable
private fun iconContainerColor(status: MembershipUiStatus) = when (status) {
    MembershipUiStatus.ACTIVE -> AccentGreenContainer
    MembershipUiStatus.EXPIRED -> AccentAmberContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun iconTintColor(status: MembershipUiStatus) = when (status) {
    MembershipUiStatus.ACTIVE -> AccentGreen
    MembershipUiStatus.EXPIRED -> AccentAmber
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusHeadline(status: MembershipUiStatus): String = when (status) {
    MembershipUiStatus.CHECKING -> stringResource(R.string.membership_status_checking)
    MembershipUiStatus.ACTIVE -> stringResource(R.string.membership_status_active)
    MembershipUiStatus.INACTIVE -> stringResource(R.string.membership_status_inactive)
    MembershipUiStatus.EXPIRED -> stringResource(R.string.membership_status_expired)
}

@Composable
private fun statusSubtitle(status: MembershipUiStatus, expiresAtMillis: Long?): String = when (status) {
    MembershipUiStatus.CHECKING -> stringResource(R.string.membership_status_checking_desc)
    MembershipUiStatus.ACTIVE -> expiresAtMillis?.let {
        stringResource(R.string.membership_status_active_desc, formatDate(it))
    } ?: stringResource(R.string.membership_status_active_desc_no_date)
    MembershipUiStatus.INACTIVE -> stringResource(R.string.membership_status_inactive_desc)
    MembershipUiStatus.EXPIRED -> expiresAtMillis?.let {
        stringResource(R.string.membership_status_expired_desc, formatDate(it))
    } ?: stringResource(R.string.membership_status_expired_desc_no_date)
}

private fun formatDate(millis: Long): String {
    val formatter = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
    return formatter.format(Date(millis))
}
