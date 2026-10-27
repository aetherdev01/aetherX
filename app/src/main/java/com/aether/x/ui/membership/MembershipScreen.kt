package com.aether.x.ui.membership

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkspacePremium
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.ui.components.LiquidButton
import com.aether.x.ui.components.withoutBottom
import com.aether.x.R
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.dashboard.DailyBonusCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentAmberContainer
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentGreenContainer
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.AetherViolet
import com.aether.x.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val maskedKey by viewModel.maskedKey.collectAsStateWithLifecycle()
    val isOfflineCache by viewModel.isOfflineCache.collectAsStateWithLifecycle()

    // Tahan status ACTIVE selama pengecekan ulang singkat (ACTIVE -> CHECKING -> ACTIVE) supaya
    // kartu & animasinya tidak hilang-muncul. Confetti hanya saat INACTIVE/EXPIRED -> ACTIVE.
    var shown by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (!(status == MembershipUiStatus.CHECKING && shown == MembershipUiStatus.ACTIVE)) shown = status
    }
    var celebrate by remember { mutableIntStateOf(0) }
    var previousShown by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown == MembershipUiStatus.ACTIVE &&
            (previousShown == MembershipUiStatus.INACTIVE || previousShown == MembershipUiStatus.EXPIRED)
        ) {
            celebrate++
        }
        previousShown = shown
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding.withoutBottom())
            .verticalScroll(rememberScrollState())
            .padding(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.lg,
                bottom = Spacing.lg + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        Column {
            Text(
                text = stringResource(R.string.membership_headline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.membership_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.line),
            )
        }

        MembershipPassCard(
            status = shown,
            expiresAtMillis = expiresAtMillis,
            maskedKey = maskedKey,
            headline = statusHeadline(shown),
            subtitle = statusSubtitle(shown, expiresAtMillis),
            celebrate = celebrate,
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        if (shown == MembershipUiStatus.ACTIVE) {
            LicenseCountdownCard(
                expiresAtMillis = expiresAtMillis,
                modifier = Modifier.cardEnterAnimation(index = 1),
            )
            LicenseDetailsCard(
                expiresAtMillis = expiresAtMillis,
                maskedKey = maskedKey,
                isOfflineCache = isOfflineCache,
                modifier = Modifier.cardEnterAnimation(index = 2),
            )
            DeviceFingerprintCard(
                deviceId = viewModel.deviceId,
                onLogout = viewModel::logout,
                modifier = Modifier.cardEnterAnimation(index = 3),
            )
        }

        // Tunggu status membership selesai diperiksa; bonus hanya untuk non-VIP.
        if (shown == MembershipUiStatus.INACTIVE || shown == MembershipUiStatus.EXPIRED) {
            DailyBonusCard(modifier = Modifier.cardEnterAnimation(index = 1))
        }

        if (shown != MembershipUiStatus.ACTIVE) {
            SectionCard(
                title = stringResource(R.string.membership_key_label),
                watermarkIcon = Icons.Outlined.VpnKey,
                modifier = Modifier.cardEnterAnimation(index = 1),
            ) {

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
                    shape = MaterialTheme.shapes.large,
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

                LiquidButton(
                    onClick = viewModel::activate,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {

                    Crossfade(
                        targetState = isSubmitting to activationStage,
                        label = "membership_activate_button_state",
                    ) { (submitting, stage) ->
                        if (submitting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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

        SectionCard(
            title = stringResource(R.string.membership_benefits_title),
            watermarkIcon = Icons.Outlined.CardGiftcard,
            modifier = Modifier.cardEnterAnimation(index = 2),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BenefitRow(text = stringResource(R.string.membership_benefit_1))
                BenefitRow(text = stringResource(R.string.membership_benefit_2))
                BenefitRow(text = stringResource(R.string.membership_benefit_3))
                BenefitRow(text = stringResource(R.string.membership_benefit_4))
            }
        }

        if (shown != MembershipUiStatus.ACTIVE) {
            MembershipProCard(modifier = Modifier.cardEnterAnimation(index = 3))
        }
    }
}

/**
 * v3.6 — langganan HANYA satu opsi: Rp15.000 / bulan. Paket mingguan dan
 * pilihan paket dihapus total; tidak ada lagi state "selectedPlan".
 */
@Composable
private fun MembershipProCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val purchaseUrl = stringResource(R.string.membership_pro_purchase_url)

    SectionCard(
        title = stringResource(R.string.membership_pro_title),
        watermarkIcon = Icons.Outlined.WorkspacePremium,
        modifier = modifier,
    ) {
        MembershipPlanCard()

        Text(
            text = stringResource(R.string.membership_pro_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LiquidButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(purchaseUrl))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.membership_pro_open_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B7FB8),
                contentColor = Color.White,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.membership_pro_cta),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Kartu harga tunggal: "1 Bulan — Rp15.000 /bulan". */
@Composable
private fun MembershipPlanCard(modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = shape, accent = AetherViolet)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.line)) {
            Text(
                text = stringResource(R.string.membership_pro_plan_monthly_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AetherViolet),
            ) {
                Text(
                    text = stringResource(R.string.membership_pro_plan_monthly_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF140C33),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.membership_pro_plan_monthly_price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AetherViolet,
            )
            Text(
                text = stringResource(R.string.membership_pro_plan_price_unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
internal fun statusHeadline(status: MembershipUiStatus): String = when (status) {
    MembershipUiStatus.CHECKING -> stringResource(R.string.membership_status_checking)
    MembershipUiStatus.ACTIVE -> stringResource(R.string.membership_status_active)
    MembershipUiStatus.INACTIVE -> stringResource(R.string.membership_status_inactive)
    MembershipUiStatus.EXPIRED -> stringResource(R.string.membership_status_expired)
}

@Composable
internal fun statusSubtitle(status: MembershipUiStatus, expiresAtMillis: Long?): String = when (status) {
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
    val formatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}
