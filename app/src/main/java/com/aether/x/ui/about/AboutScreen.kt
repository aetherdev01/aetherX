package com.aether.x.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.components.cardEnterAnimation
import com.aether.x.ui.components.pressScale
import com.aether.x.ui.components.rememberPressScaleInteractionSource
import com.aether.x.ui.components.withoutBottom
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.Spacing

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val palette = LocalAetherPalette.current

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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.nav_about),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Informasi aplikasi, pengembang, dan kanal resmi AetherX.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AboutBrandCard(
            versionName = BuildConfig.VERSION_NAME,
            modifier = Modifier.cardEnterAnimation(index = 0),
        )

        AboutSectionHeading(
            eyebrow = "CAPABILITIES",
            title = "Dibuat untuk pengalaman gaming",
            description = "Kontrol penting perangkat dan pengalaman bermain dalam satu tempat.",
        )
        AboutCapabilitiesCard(modifier = Modifier.cardEnterAnimation(index = 1))

        AboutSectionHeading(
            eyebrow = "THE DEVELOPER",
            title = "Di balik AetherX",
            description = "Dikembangkan dan dirawat secara independen.",
        )
        MaintainerCard(modifier = Modifier.cardEnterAnimation(index = 2))

        AboutSectionHeading(
            eyebrow = "STAY CONNECTED",
            title = "Terhubung dengan komunitas",
            description = "Ikuti pengumuman, diskusi, dan panduan melalui kanal resmi.",
        )
        CommunityLinkRow(
            title = stringResource(R.string.about_link_whatsapp_title),
            description = stringResource(R.string.about_link_whatsapp_desc),
            url = stringResource(R.string.about_link_whatsapp_url),
            iconRes = R.drawable.ic_social_whatsapp,
            modifier = Modifier.cardEnterAnimation(index = 3),
        )
        CommunityLinkRow(
            title = stringResource(R.string.about_link_telegram_title),
            description = stringResource(R.string.about_link_telegram_desc),
            url = stringResource(R.string.about_link_telegram_url),
            iconRes = R.drawable.ic_social_telegram,
            modifier = Modifier.cardEnterAnimation(index = 4),
        )
        CommunityLinkRow(
            title = stringResource(R.string.about_link_youtube_title),
            description = stringResource(R.string.about_link_youtube_desc),
            url = stringResource(R.string.about_link_youtube_url),
            iconRes = R.drawable.ic_social_youtube,
            modifier = Modifier.cardEnterAnimation(index = 5),
        )

        AboutFooter()
    }
}

@Composable
private fun AboutBrandCard(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAetherPalette.current
    val shape = RoundedCornerShape(30.dp)
    val backgroundBrush = Brush.linearGradient(
        colors = if (palette.isDark) {
            listOf(palette.surfaceAlt, palette.accentContainer.copy(alpha = 0.86f), palette.surface)
        } else {
            listOf(palette.accentContainer.copy(alpha = 0.82f), palette.surface, palette.surfaceAlt)
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, palette.stroke.copy(alpha = 0.72f), shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 44.dp, y = (-48).dp)
                .size(164.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = if (palette.isDark) 0.16f else 0.10f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 24.dp, y = 44.dp)
                .size(104.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = if (palette.isDark) 0.11f else 0.08f)),
        )

        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF101014))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_aetherx_logo),
                        contentDescription = "AetherX logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = "AETHERX",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.7.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "KERNEL MANAGER",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (versionName.contains("beta", ignoreCase = true)) {
                            TinyBadge(text = "BETA")
                        }
                        Text(
                            text = stringResource(R.string.about_version, versionName),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Kontrol performa, dalam satu tempat.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TinyBadge(text: String) {
    val palette = LocalAetherPalette.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.accentContainer)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AboutSectionHeading(
    eyebrow: String,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.padding(start = 2.dp, top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.25.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCapabilitiesCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(26.dp))
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CapabilityRow(
            icon = Icons.Outlined.Speed,
            title = "Pantau performa",
            description = "Lihat informasi perangkat dan aktivitas game dengan lebih praktis.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        CapabilityRow(
            icon = Icons.Outlined.Tune,
            title = "Kontrol yang fleksibel",
            description = "Atur tweak, overlay, dan tampilan sesuai kebutuhanmu.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        CapabilityRow(
            icon = Icons.Outlined.SportsEsports,
            title = "Dibuat untuk gaming",
            description = "Satukan alat bantu performa dan pengaturan game di satu aplikasi.",
        )
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val palette = LocalAetherPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(palette.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
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
    }
}

@Composable
private fun MaintainerCard(modifier: Modifier = Modifier) {
    val palette = LocalAetherPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .aetherCardSurface(shape = RoundedCornerShape(26.dp))
            .padding(Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Image(
            painter = painterResource(R.drawable.dev),
            contentDescription = stringResource(R.string.about_maintainer_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, palette.accent.copy(alpha = 0.42f), CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.about_maintainer_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_maintainer_role),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_maintainer_handle),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CommunityLinkRow(
    title: String,
    description: String,
    url: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interactionSource = rememberPressScaleInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .aetherCardSurface(shape = RoundedCornerShape(22.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                openUrl(context, url)
            }
            .padding(horizontal = Spacing.card, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(27.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Buka tautan",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AboutFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = "AETHERX  ·  ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Terima kasih telah menjadi bagian dari komunitas AetherX.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Membuka kanal eksternal dengan fallback yang ramah pengguna. */
private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
        return
    } catch (_: ActivityNotFoundException) {
        // Coba lagi dengan flag untuk konteks yang bukan Activity.
    }

    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.about_link_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
