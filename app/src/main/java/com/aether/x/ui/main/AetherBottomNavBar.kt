package com.aether.x.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.x.ui.components.LiquidBottomTab
import com.aether.x.ui.components.LiquidBottomTabs
import com.aether.x.ui.components.rememberAetherLiquidBackdrop
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size

/** Navigation item model retained for all existing call sites. */
data class AetherNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val accent: Color,
)

/**
 * AetherX bottom navigation rendered with the LiquidBottomTabs implementation from
 * Kyant0/AndroidLiquidGlass. The Haze argument is retained for source compatibility; the
 * backdrop engine samples the shared AetherBackground layer instead.
 */
@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
) {
    if (items.isEmpty()) return

    val palette = LocalAetherPalette.current
    val selectedState = rememberUpdatedState(selectedIndex.coerceIn(0, items.lastIndex))
    val selectedReader = remember { { selectedState.value } }
    val backdrop = rememberAetherLiquidBackdrop()

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Spacing.xxl + Spacing.sm)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(64.dp),
        ) {
            LiquidBottomTabs(
                selectedTabIndex = selectedReader,
                onTabSelected = onSelect,
                backdrop = backdrop,
                tabsCount = items.size,
                modifier = Modifier.fillMaxSize(),
            ) {
                items.forEachIndexed { index, item ->
                    LiquidBottomTab(onClick = { onSelect(index) }) {
                        val isSelected = index == selectedState.value
                        val tint = if (isSelected) palette.accent else palette.textSecondary
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = item.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
