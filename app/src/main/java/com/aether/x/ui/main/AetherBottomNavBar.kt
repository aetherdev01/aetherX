package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Navbar bawah bergaya "Liquid Glass" ala iOS 26: kapsul melayang,
 * translusen (kaca buram) dengan sapuan highlight lembut di bagian atas
 * (efek kilau kaca), tab aktif mendapat "chip" kaca bertinta warna primer,
 * dan setiap item punya efek "menyembul" (membesar + naik) saat ditahan —
 * mendekati kesan "bubbly glass" interaktif iOS 26.
 *
 * Catatan: ini pendekatan gaya visual (translusensi + gradient + border
 * bercahaya), BUKAN blur real-time dari konten di belakangnya. Blur asli
 * (konten di bawah bar betul-betul buram melalui kaca) butuh capture layer
 * terpisah (mis. lib "Haze" atau RenderEffect manual) — bisa ditambahkan
 * belakangan kalau efek blur sungguhannya juga diinginkan.
 */
data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
)

@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(percent = 50)
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    val glassBackground = Brush.verticalGradient(
        colors = listOf(
            surface.copy(alpha = 0.90f),
            surface.copy(alpha = 0.70f),
        ),
    )
    val glassRim = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.30f),
            outline.copy(alpha = 0.55f),
        ),
    )

    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(
                elevation = 18.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(barShape)
            .background(glassBackground)
            .border(width = 1.dp, brush = glassRim, shape = barShape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavBarItem(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun NavBarItem(
    item: AetherNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "navItemColor",
    )

    // "Chip" kaca bertinta primer — pop-in halus di belakang ikon saat aktif.
    val highlightScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navHighlightScale",
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "navHighlightAlpha",
    )
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f * highlightAlpha),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * highlightAlpha),
        ),
    )

    // Efek "menyembul" ala liquid glass: konten naik + membesar saat ditahan,
    // lalu pegas kembali begitu jari dilepas.
    val bulgeScale by animateFloatAsState(
        targetValue = if (isPressed) 1.24f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navBulgeScale",
    )
    val bulgeLift by animateDpAsState(
        targetValue = if (isPressed) (-6).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navBulgeLift",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(y = bulgeLift)
                .scale(bulgeScale),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 26.dp)
                    .scale(highlightScale)
                    .clip(RoundedCornerShape(13.dp))
                    .background(highlightBrush)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f * highlightAlpha),
                        shape = RoundedCornerShape(13.dp),
                    ),
            )
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(y = bulgeLift)
                .padding(top = 3.dp, start = 2.dp, end = 2.dp),
        )
    }
}
