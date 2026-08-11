package com.aether.x.ui.membership

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.StatusPill
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.components.pressScale
import com.aether.x.ui.components.rememberPressScaleInteractionSource
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentAmberContainer
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentGreenContainer
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.OnAccentBlue
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.membership_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        MembershipHeroCard(
            status = status,
            expiresAtMillis = expiresAtMillis,
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        if (status != MembershipUiStatus.ACTIVE) {
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

        SectionCard(
            title = stringResource(R.string.membership_benefits_title),
            watermarkIcon = Icons.Outlined.CardGiftcard,
            modifier = Modifier.cardEnterAnimation(index = 2),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BenefitRow(text = stringResource(R.string.membership_benefit_1))
                BenefitRow(text = stringResource(R.string.membership_benefit_2))
                BenefitRow(text = stringResource(R.string.membership_benefit_3))
                BenefitRow(text = stringResource(R.string.membership_benefit_4))
            }
        }

        if (status == MembershipUiStatus.ACTIVE) {
            DeviceAccountCard(
                deviceId = viewModel.deviceId,
                onLogout = viewModel::logout,
                modifier = Modifier.cardEnterAnimation(index = 3),
            )
        } else {
            MembershipProCard(modifier = Modifier.cardEnterAnimation(index = 3))
        }
    }
}

private enum class MembershipPlan(
    val labelRes: Int,
    val priceRes: Int,
) {
    WEEKLY(R.string.membership_pro_plan_weekly_label, R.string.membership_pro_plan_weekly_price),
    MONTHLY(R.string.membership_pro_plan_monthly_label, R.string.membership_pro_plan_monthly_price),
}

@Composable
private fun MembershipProCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.membership_pro_telegram_url)
    var selectedPlan by remember { mutableStateOf(MembershipPlan.MONTHLY) }
    val selectedLabel = stringResource(selectedPlan.labelRes)
    val selectedPrice = stringResource(selectedPlan.priceRes)

    SectionCard(
        title = stringResource(R.string.membership_pro_title),
        watermarkIcon = Icons.Outlined.WorkspacePremium,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MembershipPlanOption(
                    plan = MembershipPlan.WEEKLY,
                    selected = selectedPlan == MembershipPlan.WEEKLY,
                    onClick = { selectedPlan = MembershipPlan.WEEKLY },
                    modifier = Modifier.weight(1f),
                )
                MembershipPlanOption(
                    plan = MembershipPlan.MONTHLY,
                    selected = selectedPlan == MembershipPlan.MONTHLY,
                    onClick = { selectedPlan = MembershipPlan.MONTHLY },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.membership_pro_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val prefillMessage = context.getString(
                        R.string.membership_pro_telegram_prefill,
                        selectedLabel,
                        selectedPrice,
                    )

                    val uri = Uri.parse(telegramUrl).buildUpon()
                        .appendQueryParameter("text", prefillMessage)
                        .build()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {

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

@Composable
private fun MembershipPlanOption(
    plan: MembershipPlan,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) AccentBlue else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (selected) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val interactionSource = rememberPressScaleInteractionSource()
    Column(
        modifier = modifier
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(plan.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (plan == MembershipPlan.MONTHLY) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentBlue),
                ) {
                    Text(
                        text = stringResource(R.string.membership_pro_plan_monthly_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnAccentBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(plan.priceRes),
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) AccentBlue else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DeviceAccountCard(deviceId: String, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.membership_device_section_title),
        watermarkIcon = Icons.Outlined.PhoneAndroid,
        modifier = modifier,
    ) {
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
        PopupDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = Icons.Outlined.Logout,
            iconTint = AccentRed,
            title = stringResource(R.string.membership_logout_confirm_title),
            message = stringResource(R.string.membership_logout_confirm_message),
            confirmLabel = stringResource(R.string.membership_logout_confirm_action),
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            confirmIsDestructive = true,
            dismissLabel = stringResource(R.string.membership_logout_cancel),
        )
    }
}

@Composable
private fun MembershipHeroCard(status: MembershipUiStatus, expiresAtMillis: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                fontWeight = FontWeight.Bold,
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
