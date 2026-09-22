package com.aether.x.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.AetherMonoFamily
import java.util.Locale

/**
 * v3.5 — kartu RAM Cleaner baru di Dashboard, dibangun di atas
 * RamMonitor (native, lihat rammonitor.h) + RamCleanerViewModel (aksi
 * "Bersihkan RAM"). Kartu ini sepenuhnya sembunyi (return tanpa render
 * apapun) kalau modul native gagal dimuat — lihat KDoc
 * [RamCleanerUiState.available].
 */
@Composable
fun RamCleanerCard(modifier: Modifier = Modifier, viewModel: RamCleanerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    if (!state.available) return

    SectionCard(
        title = stringResource(R.string.ram_cleaner_title),
        modifier = modifier,
        watermarkIcon = Icons.Outlined.Memory,
    ) {
        val snapshot = state.snapshot
        val usedPercent = snapshot?.usedPercent
        val usedKb = snapshot?.usedKb
        val totalKb = snapshot?.totalKb

        if (snapshot == null) {
            Text(
                text = stringResource(R.string.ram_cleaner_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (usedKb != null && totalKb != null) {
                        stringResource(
                            R.string.ram_cleaner_usage_format,
                            formatKbShort(usedKb),
                            formatKbShort(totalKb),
                        )
                    } else {
                        stringResource(R.string.ram_cleaner_usage_unknown)
                    },
                    // Sebelumnya SemiBold tanpa letterSpacing — di font monospace
                    // terasa berat/padat dan mepet ke digit di sebelahnya. Weight
                    // diturunkan ke Medium + letterSpacing tipis biar lebih jelas.
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.3.sp),
                    fontFamily = AetherMonoFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (usedPercent != null) {
                    Text(
                        text = stringResource(R.string.ram_cleaner_percent_format, usedPercent.toInt()),
                        // Diturunkan dari titleMedium (16sp) ke titleSmall (14sp)
                        // supaya proporsional dengan teks pemakaian di sebelahnya,
                        // bukan terasa lebih besar sendiri.
                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.3.sp),
                        fontFamily = AetherMonoFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (usedPercent != null) {
                LinearProgressIndicator(
                    progress = { (usedPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp) // Sebelumnya 8dp — sedikit lebih ramping
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (usedPercent >= 90f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            state.lastFreedLabel?.let { freed ->
                Text(
                    text = stringResource(R.string.ram_cleaner_last_freed_format, freed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = viewModel::boostRam,
            enabled = !state.boosting,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            if (state.boosting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ram_cleaner_boost_action),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun formatKbShort(kb: Float): String {
    val gb = kb / 1024f / 1024f
    return if (gb >= 1f) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%.0f MB", kb / 1024f)
    }
}
