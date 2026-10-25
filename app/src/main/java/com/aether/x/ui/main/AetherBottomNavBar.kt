package com.aether.x.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.ui.components.LiquidBottomTab
import com.aether.x.ui.components.LiquidBottomTabs
import com.aether.x.ui.components.blurRadiusFor
import com.aether.x.ui.components.liquidTabTint
import com.aether.x.ui.components.rememberAetherLiquidBackdrop
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.Spacing
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import dev.chrisbanes.haze.HazeState

/** Tinggi navbar. Kapsul = tinggi ini - 8dp (inset 4dp). */
private val NavBarHeight: Dp = 56.dp

/** Navigation item model retained for all existing call sites. */
data class AetherNavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val accent: Color,
)

/**
 * AetherX bottom navigation rendered with the LiquidBottomTabs implementation from
 * Kyant0/AndroidLiquidGlass. The Haze argument is retained for source compatibility.
 *
 * [contentBackdrop]: layer konten layar (`Modifier.layerBackdrop`). Bila diberikan, kaca navbar
 * ikut membiaskan/mem-blur elemen yang scroll di belakangnya (tembus), bukan hanya latar.
 */
@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    contentBackdrop: Backdrop? = null,
) {
    if (items.isEmpty()) return

    val palette = LocalAetherPalette.current
    val style = LocalAetherStyle.current
    val selectedState = rememberUpdatedState(selectedIndex.coerceIn(0, items.lastIndex))
    val selectedReader = remember { { selectedState.value } }
    val baseBackdrop = rememberAetherLiquidBackdrop()
    val backdrop = if (contentBackdrop != null) {
        rememberCombinedBackdrop(baseBackdrop, contentBackdrop)
    } else {
        baseBackdrop
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Gradient blur di belakang navbar: konten memudar/ter-blur makin ke bawah (tembus),
        // tanpa tepi keras di atasnya.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        if (style.blur > 0.01f) {
                            blur((blurRadiusFor(style.blur.coerceIn(0f, 1f)) * 0.6f).dp.toPx())
                        }
                    },
                    onDrawSurface = {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, palette.bg.copy(alpha = 0.45f)),
                            ),
                        )
                    },
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.xxl + Spacing.md)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .height(NavBarHeight),
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = selectedReader,
                    onTabSelected = onSelect,
                    backdrop = backdrop,
                    tabsCount = items.size,
                    modifier = Modifier.fillMaxSize(),
                    barHeight = NavBarHeight,
                ) {
                    items.forEachIndexed { index, item ->
                        LiquidBottomTab(onClick = { onSelect(index) }) {
                            // Warna dasar textSecondary; crossfade ke accent mengikuti kapsul (fase draw).
                            Column(
                                modifier = Modifier.liquidTabTint(index, palette.accent.copy(alpha = 1f)),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = palette.textSecondary.copy(alpha = 1f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = item.label,
                                    color = palette.textSecondary.copy(alpha = 1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
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
    }
}
