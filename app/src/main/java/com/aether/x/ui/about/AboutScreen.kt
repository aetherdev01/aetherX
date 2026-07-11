package com.aether.x.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.components.SectionCard

/**
 * Tab "About" tersendiri (dulu section "Tentang" ditumpuk di paling bawah
 * tab Settings — lihat perintah rework: dipindah TOTAL ke sini, sudah
 * tidak ada lagi di SettingsScreen). Berisi identitas aplikasi, info
 * maintainer, dan tiga tombol komunitas (WhatsApp, Telegram, YouTube)
 * dengan ikon berwarna resmi masing-masing platform — menggantikan baris
 * tautan GitHub/Telegram polos yang dipakai sebelumnya.
 */
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_about),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        MaintainerHeroCard(versionName = BuildConfig.VERSION_NAME)

        SectionCard(title = stringResource(R.string.about_section_links), watermarkIcon = Icons.Outlined.Link) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CommunityLinkRow(
                    iconRes = R.drawable.ic_social_whatsapp,
                    title = stringResource(R.string.about_link_whatsapp_title),
                    description = stringResource(R.string.about_link_whatsapp_desc),
                    url = stringResource(R.string.about_link_whatsapp_url),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CommunityLinkRow(
                    iconRes = R.drawable.ic_social_telegram,
                    title = stringResource(R.string.about_link_telegram_title),
                    description = stringResource(R.string.about_link_telegram_desc),
                    url = stringResource(R.string.about_link_telegram_url),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CommunityLinkRow(
                    iconRes = R.drawable.ic_social_youtube,
                    title = stringResource(R.string.about_link_youtube_title),
                    description = stringResource(R.string.about_link_youtube_desc),
                    url = stringResource(R.string.about_link_youtube_url),
                )
            }
        }
    }
}

/**
 * Kartu HERO paling atas tab About (REWORK — sebelumnya fokus utamanya
 * identitas aplikasi dengan foto maintainer cuma jadi baris kecil kedua;
 * sekarang dibalik total): foto besar Maintainer ([R.drawable.logo], BUKAN
 * [R.drawable.ic_aetherx_logo] — logo.png ini secara khusus adalah foto
 * yang mewakili Aldi Ahmad Khoirudin selaku maintainer, dipakai di sini
 * SEBAGAI FOTO PROFIL utama, bukan sebagai logo brand aplikasi) jadi
 * elemen pertama yang dilihat, dengan nama & handle di bawahnya sebagai
 * judul utama kartu. Identitas aplikasi (judul, tagline, versi) dipindah
 * jadi info SEKUNDER di baris bawah, dipisah divider.
 */
@Composable
private fun MaintainerHeroCard(versionName: String) {
    SectionCard(title = null, watermarkIcon = Icons.Outlined.Code) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = stringResource(R.string.about_maintainer_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_maintainer_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.about_maintainer_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.about_maintainer_role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 14.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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

/**
 * Baris tombol tautan komunitas (WhatsApp/Telegram/YouTube) dengan ikon
 * berwarna resmi masing-masing platform (lihat ic_social_whatsapp.xml,
 * ic_social_telegram.xml, ic_social_youtube.xml) — TIDAK di-tint satu warna
 * seperti perlakuan logo GitHub sebelumnya, karena ketiga logo ini memang
 * dirancang multi-warna sesuai brand resminya masing-masing.
 */
@Composable
private fun CommunityLinkRow(
    iconRes: Int,
    title: String,
    description: String,
    url: String,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // Tidak ada aplikasi/browser yang bisa menangani intent ini — abaikan
                    // dengan aman daripada membuat aplikasi crash.
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // REWORK: ikon dibungkus Box putih dengan sedikit inset (padding
        // 6dp) sebelum digambar, alih-alih Image di-crop CircleShape secara
        // langsung menempel tepi lingkaran — kalau ada asset vector yang
        // bounding box-nya tidak sempurna simetris (mis. ic_social_whatsapp),
        // inset ini menyerap sedikit ketidaksempurnaan itu sehingga logo
        // tetap terlihat center secara visual, bukan mepet/terpotong ke
        // salah satu sisi.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
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
