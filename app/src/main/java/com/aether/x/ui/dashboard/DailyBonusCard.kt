package com.aether.x.ui.dashboard

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.core.ads.DailyBonusManager
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.theme.AetherGreen
import com.aether.x.ui.theme.AetherOrange
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.Spacing

/**
 * Kartu "Bonus Harian": klaim kredit tambahan (Preset Kernel) dengan menonton
 * satu iklan reward per hari. Hadiah naik tiap hari beruntun (streak 7 hari).
 * Untuk member kartu hanya berisi info — kuota mereka memang tanpa batas.
 */
@Composable
fun DailyBonusCard(
    modifier: Modifier = Modifier,
    viewModel: DailyBonusViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    // Hari bisa berganti saat layar masih terbuka; cek ulang tiap kartu tampil.
    LaunchedEffect(Unit) { viewModel.refresh() }

    SectionCard(
        title = "Bonus Harian",
        modifier = modifier,
        watermarkIcon = Icons.Outlined.CardGiftcard,
        accent = AetherOrange,
    ) {
        if (state.isMember) {
            Text(
                text = "Member aktif: preset kernel tanpa batas, tidak perlu klaim bonus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.line)) {
            Text(
                text = if (state.claimedToday) {
                    "Sudah diklaim hari ini"
                } else {
                    "Klaim +${state.nextRewardCredits} kredit preset kernel"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (state.claimedToday) {
                    "Kembali besok untuk bonus yang lebih besar. Streak: ${state.streak} hari."
                } else {
                    "Tonton 1 iklan singkat. Makin sering klaim beruntun, makin besar hadiahnya."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StreakRow(
            streak = state.streak,
            claimedToday = state.claimedToday,
        )

        Button(
            onClick = { viewModel.claim(activity) },
            enabled = !state.claimedToday && !state.claiming && !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = AetherOrange,
                contentColor = LocalAetherPalette.current.onBonus,
            ),
        ) {
            when {
                state.claiming -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalAetherPalette.current.onBonus,
                )
                state.claimedToday -> Text("Sudah diklaim", fontWeight = FontWeight.SemiBold)
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("Tonton iklan & klaim", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 7 titik hari: hijau = sudah, oranye = hari yang bisa diklaim sekarang. */
@Composable
private fun StreakRow(streak: Int, claimedToday: Boolean) {
    val cycle = DailyBonusManager.STREAK_CYCLE
    // Jumlah hari yang sudah terisi dalam siklus 7 hari berjalan.
    val inCycle = if (streak <= 0) 0 else ((streak - 1) % cycle) + 1
    // Siklus 7 hari sudah penuh dan hari ini belum klaim -> siklus baru dimulai.
    val filled = if (!claimedToday && inCycle == cycle) 0 else inCycle
    val nextIndex = if (claimedToday) -1 else filled

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(cycle) { index ->
            val done = index < filled
            val isNext = index == nextIndex
            val dotColor = when {
                done -> AetherGreen
                isNext -> AetherOrange
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.line),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = if (done || isNext) 0.22f else 1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = dotColor,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "+${minOf(DailyBonusManager.BASE_CREDITS + index, DailyBonusManager.MAX_CREDITS)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNext) dotColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "H${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
