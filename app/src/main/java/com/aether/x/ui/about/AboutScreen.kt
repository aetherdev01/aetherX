package com.aether.x.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.components.pressScale
import com.aether.x.ui.components.rememberPressScaleInteractionSource
import com.aether.x.ui.theme.Spacing

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.nav_about),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )

        MaintainerHeroCard(
            versionName = BuildConfig.VERSION_NAME,
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        Text(
            text = stringResource(R.string.about_section_links),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        CommunityLinkRow(
            icon = Icons.Filled.Chat,
            iconColor = Color(0xFF25D366),
            title = stringResource(R.string.about_link_whatsapp_title),
            description = stringResource(R.string.about_link_whatsapp_desc),
            url = stringResource(R.string.about_link_whatsapp_url),
            modifier = Modifier.cardEnterAnimation(index = 1),
        )
        CommunityLinkRow(
            icon = Icons.AutoMirrored.Filled.Send,
            iconColor = Color(0xFF29A9EB),
            title = stringResource(R.string.about_link_telegram_title),
            description = stringResource(R.string.about_link_telegram_desc),
            url = stringResource(R.string.about_link_telegram_url),
            modifier = Modifier.cardEnterAnimation(index = 2),
        )
        CommunityLinkRow(
            icon = Icons.Filled.SmartDisplay,
            iconColor = Color(0xFFFF3B30),
            title = stringResource(R.string.about_link_youtube_title),
            description = stringResource(R.string.about_link_youtube_desc),
            url = stringResource(R.string.about_link_youtube_url),
            modifier = Modifier.cardEnterAnimation(index = 3),
        )
    }
}

@Composable
private fun MaintainerHeroCard(versionName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Image(
                painter = painterResource(id = R.drawable.dev),
                contentDescription = stringResource(R.string.about_maintainer_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_maintainer_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.about_maintainer_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.about_app_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.about_app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CommunityLinkRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interactionSource = rememberPressScaleInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interactionSource, indication = null) {
                openUrl(context, url)
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Buka [url] di browser/app luar. Sebelumnya kegagalan (mis. tidak ada
 * app yang bisa menangani intent) ditangkap lalu DIBUANG begitu saja
 * (catch block kosong) — dari sisi pengguna, tombol terlihat "tidak
 * berfungsi" tanpa penjelasan sama sekali. Sekarang: coba ACTION_VIEW
 * dulu (bisa buka app WhatsApp/Telegram/YouTube langsung kalau
 * terpasang), kalau gagal baru dicoba dengan FLAG_ACTIVITY_NEW_TASK
 * (perlu untuk context tertentu yang bukan Activity langsung), dan
 * kalau tetap gagal, tampilkan Toast supaya pengguna tahu link gagal
 * dibuka (bukan diam tanpa respons).
 */
private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
        return
    } catch (e: ActivityNotFoundException) {
        // lanjut ke fallback di bawah
    }

    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.about_link_open_failed), Toast.LENGTH_SHORT).show()
    }
}
