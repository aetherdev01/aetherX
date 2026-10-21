package com.aether.x.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        AboutPageHeading(modifier = Modifier.cardEnterAnimation(index = 0))
        AboutBrandHero(
            versionName = BuildConfig.VERSION_NAME,
            modifier = Modifier.cardEnterAnimation(index = 1),
        )
        AboutFeatures(modifier = Modifier.cardEnterAnimation(index = 2))
        AboutMaintainer(modifier = Modifier.cardEnterAnimation(index = 3))
        AboutCommunity(modifier = Modifier.cardEnterAnimation(index = 4))
        AboutPageFooter()
    }
}

@Composable
private fun AboutPageHeading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.nav_about),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Kenali aplikasi, fitur, dan pengembang AetherX.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutBrandHero(versionName: String, modifier: Modifier = Modifier) {
    val palette = LocalAetherPalette.current
    val shape = RoundedCornerShape(28.dp)
    val brush = Brush.linearGradient(
        colors = if (palette.isDark) {
            listOf(
                palette.surfaceAlt,
                palette.accentContainer.copy(alpha = 0.82f),
                palette.surface,
            )
        } else {
            listOf(
                palette.accentContainer.copy(alpha = 0.9f),
                palette.surface,
                palette.surfaceAlt,
            )
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .border(1.dp, palette.stroke.copy(alpha = 0.72f), shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 0.dp, top = 0.dp)
                .size(146.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = if (palette.isDark) 0.13f else 0.09f)),
        )
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0xFF101014))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(21.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_aetherx_logo),
                        contentDescription = "AetherX logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(47.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "AETHERX",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "KERNEL MANAGER",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (versionName.contains("beta", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.accentContainer)
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "BETA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.about_version, versionName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Performa dan kontrol,\ndalam satu tempat.",
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
private fun AboutFeatures(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionLabel(
            title = "Yang bisa kamu lakukan",
            subtitle = "Fitur utama AetherX",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aetherCardSurface(shape = RoundedCornerShape(25.dp))
                .padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FeatureLine(
                icon = Icons.Outlined.Speed,
                title = "Monitor performa",
                description = "Pantau informasi perangkat dan aktivitas gaming dengan praktis.",
            )
            FeatureLine(
                icon = Icons.Outlined.Tune,
                title = "Kontrol fleksibel",
                description = "Sesuaikan tweak, overlay, dan tampilan sesuai kebutuhan.",
            )
            FeatureLine(
                icon = Icons.Outlined.Bolt,
                title = "Pengalaman gaming",
                description = "Satukan alat bantu dan pengaturan penting dalam satu aplikasi.",
            )
        }
    }
}

@Composable
private fun FeatureLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LocalAetherPalette.current.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalAetherPalette.current.accent,
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
private fun AboutMaintainer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionLabel(
            title = "Di balik AetherX",
            subtitle = "Dikembangkan dan dirawat secara independen",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aetherCardSurface(shape = RoundedCornerShape(25.dp))
                .padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.dev),
                contentDescription = stringResource(R.string.about_maintainer_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = LocalAetherPalette.current.accent.copy(alpha = 0.42f),
                        shape = CircleShape,
                    ),
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
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AboutCommunity(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SectionLabel(
            title = "Kanal resmi",
            subtitle = "Ikuti kabar terbaru dan panduan AetherX",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            CommunityTile(
                modifier = Modifier.weight(1f),
                title = "Telegram",
                description = stringResource(R.string.about_link_telegram_desc),
                url = stringResource(R.string.about_link_telegram_url),
                iconRes = R.drawable.ic_social_telegram,
            )
            CommunityTile(
                modifier = Modifier.weight(1f),
                title = "YouTube",
                description = stringResource(R.string.about_link_youtube_desc),
                url = stringResource(R.string.about_link_youtube_url),
                iconRes = R.drawable.ic_social_youtube,
            )
        }
    }
}

@Composable
private fun CommunityTile(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    url: String,
    iconRes: Int,
) {
    val context = LocalContext.current
    val interactionSource = rememberPressScaleInteractionSource()
    Column(
        modifier = modifier
            .pressScale(interactionSource)
            .aetherCardSurface(shape = RoundedCornerShape(23.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                openUrl(context, url)
            }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(27.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Buka $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutPageFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(LocalAetherPalette.current.accent.copy(alpha = 0.72f)),
        )
        Text(
            text = "AETHERX  ·  ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Terima kasih telah menggunakan AetherX.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
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
}
