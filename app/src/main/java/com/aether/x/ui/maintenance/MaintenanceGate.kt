package com.aether.x.ui.maintenance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.core.notification.AetherXNotifier
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.theme.AccentAmber

@Composable
fun MaintenanceGate(viewModel: MaintenanceViewModel = viewModel()) {
    val status by viewModel.status.collectAsState()
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.maintenance_telegram_url)
    val defaultTitle = stringResource(R.string.maintenance_default_title)
    val notifText = stringResource(R.string.notif_maintenance_text)

    LaunchedEffect(status.enabled) {
        if (status.enabled) {
            AetherXNotifier.notify(
                context = context,
                kind = AetherXNotifier.NotificationKind.MAINTENANCE,
                title = status.title.ifBlank { defaultTitle },
                text = notifText,
                ongoing = true,
            )
        } else {
            AetherXNotifier.cancel(context, AetherXNotifier.NotificationKind.MAINTENANCE)
        }
    }

    if (!status.enabled) return

    PopupDialog(
        onDismissRequest = {},
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        icon = Icons.Outlined.Build,
        iconTint = AccentAmber,
        title = status.title.ifBlank { stringResource(R.string.maintenance_default_title) },
        message = status.message.ifBlank { stringResource(R.string.maintenance_default_message) },
    ) {
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {

                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
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
                    text = stringResource(R.string.maintenance_contact_admin_button),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
