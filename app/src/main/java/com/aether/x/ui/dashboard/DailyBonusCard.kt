package com.aether.x.ui.dashboard

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CardGiftcard
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
import com.aether.x.core.locale.str
import com.aether.x.R

/**
 * Kartu "Bonus Harian": klaim kredit tambahan (Preset Kernel) dengan menonton
 * satu iklan reward per hari. Hadiah naik tiap hari beruntun (streak 7 hari).
 * Kartu disembunyikan untuk member/VIP karena akses preset mereka tanpa batas.
 */
@Composable
fun DailyBonusCard(
    modifier: Modifier = Modifier,
    viewModel: DailyBonusViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // VIP/member tidak perlu bonus harian, jadi section tidak dirender sama sekali.
    if (state.isMember) return

    val activity = LocalContext.current as? Activity

    // Perbarui status klaim saat kartu ditampilkan.
    LaunchedEffect(Unit) { viewModel.refresh() }

    SectionCard(
        title = str(R.string.daily_bonus_card_bonus_harian),
        modifier = modifier,
        watermarkIcon = Icons.Outlined.CardGiftcard,
        accent = AetherOrange,
    ) {
        // Layout horizontal ringkas supaya bonus tidak mendominasi dashboard.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AetherOrange.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.claimedToday) Icons.Filled.Check else Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    tint = if (state.claimedToday) AetherGreen else AetherOrange,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (state.claimedToday) str(R.string.daily_bonus_card_sudah_diklaim_hari_ini) else str(R.string.daily_bonus_card_kredit, state.nextRewardCredits),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.claimedToday) str(R.string.daily_bonus_card_streak_hari_kembali_besok, state.streak) else str(R.string.daily_bonus_card_bonus_harian_streak_hari, state.streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { viewModel.claim(activity) },
                enabled = !state.claimedToday && !state.claiming && !state.loading,
                modifier = Modifier.height(40.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AetherOrange,
                    contentColor = LocalAetherPalette.current.onBonus,
                ),
            ) {
                when {
                    state.claiming -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalAetherPalette.current.onBonus,
                    )
                    state.claimedToday -> Text(str(R.string.dashboard_order_done), fontWeight = FontWeight.SemiBold)
                    else -> Text(str(R.string.daily_bonus_card_klaim), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        StreakRow(streak = state.streak, claimedToday = state.claimedToday)
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
