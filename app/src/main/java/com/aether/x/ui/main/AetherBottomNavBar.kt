package com.aether.x.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Navbar bawah bergaya "Liquid Glass" ala iOS 26 (rujukan: tab bar
 * Apple News+ — satu pill BESAR mengisi hampir seluruh slot tab,
 * bukan chip kecil di belakang ikon).
 *
 * Dua lapis liquid terpisah:
 * 1. KACA BAR — blur nyata dari konten di baliknya (Haze), rim highlight
 *    di tepi atas + inner-shadow tipis di bawah supaya kaca terasa
 *    punya ketebalan.
 * 2. PILL PENANDA TAB — satu pill solid setinggi/selebar slot yang bisa
 *    DITAHAN lalu DIGESER. Selama ditahan, pill "menyembul" (scale naik,
 *    boleh sedikit melebihi tinggi bar) dan mengikuti jari secara halus
 *    berpindah antar slot yang berdekatan — bukan lonjong menyambung
 *    dua titik jauh. Tab baru baru dipilih (onSelect) saat jari
 *    dilepas; pill snap-settle ke slot terdekat dengan sedikit overshoot
 *    pegas untuk kesan liquid.
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
    val density = LocalDensity.current

    val glassRim = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.12f),
            outline.copy(alpha = 0.65f),
        ),
    )
    val glassSheen = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.02f),
            Color.Black.copy(alpha = 0.05f),
        ),
    )

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val slotWidthPx = if (items.isNotEmpty() && barWidthPx > 0f) barWidthPx / items.size else 0f

    // Posisi pill dalam satuan "index pecahan" — 0f = tab pertama,
    // 1f = tab kedua, 1.4f = 40% jalan dari tab kedua ke ketiga, dst.
    // Satu sumber kebenaran tunggal untuk animasi settle MAUPUN drag,
    // supaya tidak ada dua state posisi yang bisa saling tidak sinkron.
    val pillPosition = remember { Animatable(selectedIndex.toFloat()) }
    var previewIndex by remember { mutableStateOf(selectedIndex) }
    var isDragging by remember { mutableStateOf(false) }
    // Ditekan TAPI belum tentu jadi drag — dipisah dari isDragging supaya
    // pill bisa langsung "menyembul" begitu jari menyentuh, tanpa menunggu
    // ambang waktu long-press yang dipakai detectDragGesturesAfterLongPress
    // (itu jeda yang terasa seperti "delay" sebelumnya).
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val settleSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    LaunchedEffect(selectedIndex, items.size) {
        previewIndex = selectedIndex
        if (!isDragging) {
            pillPosition.animateTo(selectedIndex.toFloat(), animationSpec = settleSpec)
        }
    }

    // Efek "menyembul": pill membesar melampaui tinggi normal bar begitu
    // jari MENYENTUH (isPressed), bukan baru saat drag resmi terdeteksi —
    // supaya responnya instan, lalu mengempis kembali saat jari dilepas.
    val pillBulge = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        pillBulge.animateTo(
            targetValue = if (isPressed) 1.22f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .onSizeChanged {
                barWidthPx = it.width.toFloat()
                barHeightPx = it.height.toFloat()
            }
            .pointerInput(items.size, slotWidthPx) {
                if (slotWidthPx <= 0f) return@pointerInput
                awaitEachGesture {
                    // down: feedback instan (bulge) di posisi sentuhan,
                    // tanpa menunggu apakah ini akan jadi long-press/drag.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val raw = (down.position.x / slotWidthPx) - 0.5f
                    val clamped = raw.coerceIn(0f, (items.size - 1).toFloat())
                    previewIndex = clamped.roundToInt().coerceIn(0, items.lastIndex)
                    scope.launch { pillPosition.snapTo(clamped) }

                    // Tunggu ambang long-press sambil tetap memantau apakah
                    // jari terangkat lebih dulu (berarti ini tap biasa).
                    val becameDrag = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) return@withTimeout
                            }
                        }
                        false
                    } catch (timeout: PointerEventTimeoutCancellationException) {
                        true
                    }

                    if (becameDrag) {
                        isDragging = true
                        // Loop drag: ikuti jari sampai terangkat.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            change.consume()
                            val dragRaw = (change.position.x / slotWidthPx) - 0.5f
                            val dragClamped = dragRaw.coerceIn(0f, (items.size - 1).toFloat())
                            previewIndex = dragClamped.roundToInt().coerceIn(0, items.lastIndex)
                            scope.launch { pillPosition.snapTo(dragClamped) }
                        }
                        isDragging = false
                        isPressed = false
                        val finalIndex = previewIndex
                        // onSelect dipanggil SEGERA — tidak menunggu animasi
                        // settle pill selesai — supaya transisi screen dan
                        // pill snap berjalan bersamaan, bukan berurutan.
                        if (finalIndex != selectedIndex) onSelect(finalIndex)
                        scope.launch {
                            pillPosition.animateTo(finalIndex.toFloat(), animationSpec = settleSpec)
                        }
                    } else {
                        // Tap biasa (jari terangkat sebelum ambang long-press):
                        // kembalikan pill ke tab aktif, jangan pindah tab dari
                        // posisi sentuh — tap-to-select tetap lewat onClick item.
                        isPressed = false
                        previewIndex = selectedIndex
                        scope.launch {
                            pillPosition.animateTo(selectedIndex.toFloat(), animationSpec = settleSpec)
                        }
                    }
                }
            },
    ) {
        // Kaca bar (di-clip ke bentuk kapsul) — lapis terpisah PALING BAWAH,
        // agar pill di atasnya bisa "menyembul" melampaui tepi bar tanpa
        // ikut terpotong oleh clip milik kaca ini.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 18.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(barShape)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                .background(glassSheen)
                .border(width = 1.dp, brush = glassRim, shape = barShape),
        )

        // Lapis 2: pill besar — TIDAK di-clip, sehingga saat ditahan boleh
        // membesar melampaui tinggi bar (efek "menyembul" liquid glass).
        if (slotWidthPx > 0f) {
            val insetPx = with(density) { 4.dp.toPx() }
            val pillWidth = slotWidthPx - insetPx * 2f
            val pillHeightBase = barHeightPx - insetPx * 2f
            val centerX = (pillPosition.value + 0.5f) * slotWidthPx
            val bulge = pillBulge.value
            val pillWidthPx = pillWidth * (0.92f + 0.08f * bulge)
            val pillHeightPx = pillHeightBase * bulge
            val pillWidthDp = with(density) { pillWidthPx.toDp() }
            val pillHeightDp = with(density) { pillHeightPx.toDp() }
            val offsetXDp = with(density) { (centerX - pillWidthPx / 2f).toDp() }
            val offsetYDp = with(density) { ((barHeightPx - pillHeightPx) / 2f).toDp() }

            val tint = MaterialTheme.colorScheme.primary
            val pillBrush = Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = 0.42f),
                    tint.copy(alpha = 0.20f),
                ),
            )
            val pillSheen = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.02f),
                ),
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = with(density) { offsetXDp.toPx() }
                        translationY = with(density) { offsetYDp.toPx() }
                    }
                    .size(width = pillWidthDp, height = pillHeightDp)
                    .shadow(
                        elevation = if (isPressed) 10.dp else 0.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = tint.copy(alpha = 0.4f),
                        spotColor = tint.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(50))
                    // Blur kaca NYATA dari konten di belakang pill (sama
                    // sumbernya dengan blur bar) — bukan cuma warna solid.
                    .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                    .background(pillBrush)
                    .background(pillSheen)
                    .border(
                        width = 1.dp,
                        color = tint.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                    ),
            )
        }

        // Lapis 3: ikon + label tiap tab, sedikit inset agar tidak
        // menyentuh tepi kaca (menjaga area sentuh tetap nyaman).
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
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
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
