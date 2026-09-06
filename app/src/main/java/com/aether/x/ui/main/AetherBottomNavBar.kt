package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

/**
 * Navbar bawah bergaya "Liquid Glass" ala iOS 26.
 *
 * Dua lapis efek liquid terpisah:
 * 1. KACA BAR ITU SENDIRI — blur nyata dari konten di baliknya (Haze), rim
 *    highlight di tepi atas + inner-shadow tipis di tepi bawah supaya kaca
 *    terasa punya ketebalan, bukan cuma translusensi datar.
 * 2. PILL PENANDA TAB AKTIF — satu pill tunggal (bukan chip per-item) yang
 *    bisa DITAHAN lalu DIGESER bebas sepanjang bar. Selama digeser, pill
 *    meregang (morph) jadi kapsul lonjong ala blob/gooey antara posisi
 *    "jangkar" (tab yang lagi aktif) dan posisi jari — mirip liquid glass
 *    asli. Tab baru baru dipilih (onSelect dipanggil) saat jari dilepas,
 *    pill akan snap-settle ke tab terdekat.
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
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(percent = 50)
    val outline = MaterialTheme.colorScheme.outline

    // Rim kaca: terang di atas (cahaya jatuh dari atas), meredup ke bawah,
    // lalu sedikit menggelap lagi di tepi paling bawah — memberi kesan
    // ketebalan kaca, bukan garis tepi 1 warna yang flat.
    val glassRim = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.12f),
            outline.copy(alpha = 0.65f),
        ),
    )

    // Sapuan highlight lembut di sepanjang badan kaca (bukan cuma rim),
    // simulasi cahaya menyapu permukaan cembung kapsul.
    val glassSheen = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.02f),
            Color.Black.copy(alpha = 0.05f),
        ),
    )

    // Lebar tiap slot item, diukur langsung dari layout — dipakai untuk
    // menghitung posisi X pusat tiap tab dengan presisi piksel, termasuk
    // saat lebar berubah (rotasi, ukuran layar berbeda, dll).
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val slotWidthPx = if (items.isNotEmpty() && barWidthPx > 0f) barWidthPx / items.size else 0f

    fun centerXOf(index: Int): Float = (index + 0.5f) * slotWidthPx

    // Posisi "jangkar" pill (tab yang benar-benar aktif, sudah settle).
    val anchorX = remember { Animatable(0f) }
    // Posisi "kepala" pill mengikuti jari saat drag berlangsung.
    var dragHeadX by remember { mutableStateOf<Float?>(null) }
    // Tab yang sedang dilewati pill selama drag — dipakai untuk preview
    // highlight ikon, TIDAK memicu onSelect sampai jari dilepas.
    var previewIndex by remember { mutableStateOf(selectedIndex) }
    var isDragging by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Saat selectedIndex berubah dari luar (mis. navigasi via kode lain),
    // atau saat lebar bar sudah diketahui, jangkar pill ikut pindah halus.
    LaunchedEffect(selectedIndex, slotWidthPx) {
        previewIndex = selectedIndex
        val target = centerXOf(selectedIndex)
        if (slotWidthPx > 0f) {
            anchorX.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    Box(
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
            .hazeEffect(state = hazeState, style = HazeMaterials.thin())
            .background(glassSheen)
            .border(width = 1.dp, brush = glassRim, shape = barShape)
            .onSizeChanged { barWidthPx = it.width.toFloat() }
            .pointerInput(items.size, slotWidthPx) {
                if (slotWidthPx <= 0f) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isDragging = true
                        dragHeadX = offset.x.coerceIn(0f, barWidthPx)
                        val idx = (offset.x / slotWidthPx)
                            .toInt()
                            .coerceIn(0, items.lastIndex)
                        previewIndex = idx
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newX = change.position.x.coerceIn(0f, barWidthPx)
                        dragHeadX = newX
                        val idx = (newX / slotWidthPx)
                            .toInt()
                            .coerceIn(0, items.lastIndex)
                        previewIndex = idx
                    },
                    onDragEnd = {
                        isDragging = false
                        dragHeadX = null
                        val finalIndex = previewIndex
                        scope.launch {
                            anchorX.animateTo(
                                targetValue = centerXOf(finalIndex),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                        if (finalIndex != selectedIndex) onSelect(finalIndex)
                    },
                    onDragCancel = {
                        isDragging = false
                        dragHeadX = null
                        previewIndex = selectedIndex
                        scope.launch {
                            anchorX.animateTo(
                                targetValue = centerXOf(selectedIndex),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        }
                    },
                )
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        // Lapis 1: pill gooey — digambar DI BAWAH ikon/label supaya ikon
        // selalu terbaca jelas di atasnya.
        if (slotWidthPx > 0f) {
            GooeyPill(
                anchorX = anchorX.value,
                headX = dragHeadX,
                isDragging = isDragging,
                pillHeight = 26.dp,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Lapis 2: ikon + label tiap tab.
        Row(
            modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
                    // Highlight ikon ikut preview saat drag, bukan cuma
                    // selectedIndex final — supaya terasa "hidup" saat digeser.
                    selected = if (isDragging) index == previewIndex else index == selectedIndex,
                    onClick = { if (!isDragging) onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Pill tunggal dengan efek gooey/metaball: digambar sebagai dua kapsul
 * ("jangkar" yang diam di tab aktif, dan "kepala" yang mengikuti jari)
 * yang menyatu jadi satu bentuk lonjong menyambung saat keduanya berjarak.
 * Teknik klasik "gooey effect": render ke layer offscreen, blur, lalu
 * naikkan kontras alpha (threshold) supaya pinggiran blur menyatu jadi
 * satu siluet mulus alih-alih dua lingkaran transparan tumpang tindih.
 */
@Composable
private fun GooeyPill(
    anchorX: Float,
    headX: Float?,
    isDragging: Boolean,
    pillHeight: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val stretchAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillStretchAlpha",
    )
    val restScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillRestScale",
    )

    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = 0.42f),
            tint.copy(alpha = 0.20f),
        ),
    )
    val rimColor = tint.copy(alpha = 0.55f)
    val strokeWidth = with(LocalDensity.current) { 1.2.dp.toPx() }
    val pillHeightPx = with(LocalDensity.current) { pillHeight.toPx() }

    Box(
        modifier = modifier.drawBehind {
            val centerY = size.height / 2f
            val baseHalfH = (pillHeightPx / 2f) * restScale
            val baseHalfW = baseHalfH * 1.55f

            if (headX == null || stretchAlpha <= 0.01f) {
                // Diam: satu kapsul rounded-rect biasa di posisi jangkar.
                drawCapsule(
                    center = Offset(anchorX, centerY),
                    halfWidth = baseHalfW,
                    halfHeight = baseHalfH,
                    brush = fillBrush,
                    rimColor = rimColor,
                    strokeWidth = strokeWidth,
                )
                return@drawBehind
            }

            // Sedang drag: gambar bentuk gooey menyambung antara jangkar
            // dan kepala, lebih lonjong (menyempit) semakin jauh jaraknya,
            // meniru elastisitas cairan yang ditarik.
            val dx = headX - anchorX
            val distance = kotlin.math.abs(dx)
            val sign = if (dx < 0f) -1f else 1f
            val stretchFactor = (1f - (distance / (size.width.coerceAtLeast(1f) * 0.9f)))
                .coerceIn(0.35f, 1f)
            val neckHalfH = baseHalfH * stretchFactor * stretchAlpha +
                baseHalfH * (1f - stretchAlpha)
            val headHalfW = baseHalfW * (0.85f + 0.15f * stretchAlpha)

            val path = Path().apply {
                moveTo(anchorX, centerY - baseHalfH)
                // Sisi atas: dari jangkar melengkung ke kepala.
                cubicTo(
                    anchorX + dx * 0.35f, centerY - neckHalfH,
                    headX - sign * headHalfW * 0.4f, centerY - headHalfW * 0.55f,
                    headX, centerY - headHalfW * 0.55f,
                )
                // Ujung kepala (bulat).
                arcTo(
                    rect = Rect(
                        left = headX - headHalfW * 0.55f,
                        top = centerY - headHalfW * 0.55f,
                        right = headX + headHalfW * 0.55f,
                        bottom = centerY + headHalfW * 0.55f,
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                // Sisi bawah: dari kepala kembali ke jangkar.
                cubicTo(
                    headX - sign * headHalfW * 0.4f, centerY + headHalfW * 0.55f,
                    anchorX + dx * 0.35f, centerY + neckHalfH,
                    anchorX, centerY + baseHalfH,
                )
                // Ujung jangkar (bulat).
                arcTo(
                    rect = Rect(
                        left = anchorX - baseHalfW,
                        top = centerY - baseHalfH,
                        right = anchorX + baseHalfW,
                        bottom = centerY + baseHalfH,
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }
            drawPath(path = path, brush = fillBrush)
            drawPath(
                path = path,
                color = rimColor,
                style = Stroke(width = strokeWidth),
            )
        },
    )
}

private fun DrawScope.drawCapsule(
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    brush: Brush,
    rimColor: Color,
    strokeWidth: Float,
) {
    val topLeft = Offset(center.x - halfWidth, center.y - halfHeight)
    val size = Size(halfWidth * 2f, halfHeight * 2f)
    val corner = CornerRadius(halfHeight, halfHeight)
    drawRoundRect(brush = brush, topLeft = topLeft, size = size, cornerRadius = corner)
    drawRoundRect(
        color = rimColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = strokeWidth),
    )
}

@Composable
private fun NavBarItem(
    item: AetherNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "navItemColor",
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
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
        )
    }
}
