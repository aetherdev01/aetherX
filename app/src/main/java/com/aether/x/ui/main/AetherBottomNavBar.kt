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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Navbar bawah bergaya "Liquid Glass" ala iOS 26 (rujukan: tab bar
 * Apple News+ — satu pill BESAR mengisi hampir seluruh slot tab,
 * bukan chip kecil di belakang ikon).
 *
 * Tiga lapis liquid terpisah:
 * 1. KACA BAR — blur nyata dari konten di baliknya (Haze), rim highlight
 *    di tepi atas + inner-shadow tipis di bawah supaya kaca terasa
 *    punya ketebalan. Seluruh kapsul bar ikut "menyembul" pegas (bulge
 *    tipis) saat ditahan, supaya terasa satu material kenyal, bukan
 *    cuma pill di dalamnya yang bergerak sendirian.
 * 2. PILL PENANDA TAB — satu pill solid setinggi/selebar slot yang bisa
 *    DITAHAN lalu DIGESER. Selama ditahan, pill "menyembul" (scale naik,
 *    boleh sedikit melebihi tinggi bar) dan mengikuti jari lewat spring
 *    kenyal (bukan snap instan mentah) — respons tetap cepat tapi ada
 *    sedikit "lag" elastis khas liquid glass, bukan lonjong menyambung
 *    dua titik jauh. Tab baru dipilih (onSelect) saat jari dilepas;
 *    pill snap-settle ke slot terdekat dengan sedikit overshoot pegas.
 *    Saat bergerak cepat, pill squash & stretch (melebar searah gerak,
 *    memipih tegak lurusnya) lalu membulat lagi saat berhenti — efek
 *    jelly.
 * 3. MIRROR SHEEN — highlight spekular yang ikut bergeser mengikuti
 *    posisi pill, mensimulasikan pantulan cahaya pada permukaan kaca
 *    cembung (efek "mirror" liquid glass).
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

    // Kecepatan gerak pill (satuan: index pecahan per event drag), dipakai
    // untuk efek jelly squash & stretch — bukan animasi berjadwal, tapi
    // dihitung dari selisih posisi mentah antar sample gesture.
    var pillVelocity by remember { mutableFloatStateOf(0f) }
    var lastRawPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    // Spring "settle" untuk perpindahan tab yang sudah final (lepas jari /
    // tap) — sedikit overshoot pegas supaya berhenti terasa kenyal.
    val settleSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    // Spring "follow" dipakai SELAMA jari menahan & menggeser — redaman
    // lebih tinggi (kurang mantul) tapi kekakuan lebih rendah dari settle,
    // supaya pill terasa "berat/kenyal" mengikuti jari (ala iOS 26) alih-
    // alih menempel mentah 1:1 pada posisi sentuhan setiap frame.
    val followSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 380f,
    )

    LaunchedEffect(selectedIndex, items.size) {
        previewIndex = selectedIndex
        if (!isDragging) {
            pillPosition.animateTo(selectedIndex.toFloat(), animationSpec = settleSpec)
        }
    }

    // Efek "menyembul" pada PILL: membesar melampaui tinggi normal bar
    // begitu jari MENYENTUH (isPressed), bukan baru saat drag resmi
    // terdeteksi — supaya responnya instan, lalu mengempis kembali saat
    // jari dilepas.
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

    // Efek "menyembul" pada seluruh KAPSUL BAR: skala naik sedikit saat
    // ditahan, seolah seluruh bar ikut merespons tekanan seperti satu
    // permukaan liquid yang menyatu dengan pill di dalamnya — bukan cuma
    // pill yang bergerak sendirian di atas bar yang diam kaku.
    val barBulge = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        barBulge.animateTo(
            targetValue = if (isPressed) 1.035f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    // Peluruhan velocity: begitu jari berhenti bergerak/terlepas, velocity
    // meluruh halus ke 0 supaya efek jelly mengendur perlahan, bukan
    // langsung patah ke bentuk normal.
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            val decaySpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            )
            val decayAnim = Animatable(pillVelocity)
            decayAnim.animateTo(0f, animationSpec = decaySpec) {
                pillVelocity = value
            }
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .graphicsLayer {
                // Bulge kapsul dipusatkan di tengah bar, jadi mengembang
                // merata ke segala arah alih-alih menggeser posisi bar.
                val scale = barBulge.value
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
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
                    val rawDown = (down.position.x / slotWidthPx) - 0.5f
                    val clampedDown = rawDown.coerceIn(0f, (items.size - 1).toFloat())
                    lastRawPosition = clampedDown
                    pillVelocity = 0f
                    previewIndex = clampedDown.roundToInt().coerceIn(0, items.lastIndex)
                    scope.launch {
                        pillPosition.animateTo(clampedDown, animationSpec = followSpec)
                    }

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
                        // Loop drag: ikuti jari sampai terangkat. Posisi
                        // mentah dikejar lewat spring "follow" (bukan
                        // snapTo instan) supaya gerakan pill terasa kenyal
                        // dan tidak terlalu cepat/kaku — sesuai nuansa
                        // iOS 26 liquid nav.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            change.consume()
                            val dragRaw = (change.position.x / slotWidthPx) - 0.5f
                            val dragClamped = dragRaw.coerceIn(0f, (items.size - 1).toFloat())
                            pillVelocity = dragClamped - lastRawPosition
                            lastRawPosition = dragClamped
                            previewIndex = dragClamped.roundToInt().coerceIn(0, items.lastIndex)
                            scope.launch {
                                pillPosition.animateTo(dragClamped, animationSpec = followSpec)
                            }
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

            // Efek jelly: squash & stretch berdasarkan kecepatan gerak.
            // Semakin cepat pill bergeser, semakin ia melebar horizontal
            // dan memipih vertikal (seperti tetesan cair yang ditarik),
            // lalu membulat kembali begitu berhenti/melambat.
            val speed = abs(pillVelocity).coerceIn(0f, 0.6f)
            val stretchFactor = 1f + (speed / 0.6f) * 0.26f
            val squashFactor = 1f - (speed / 0.6f) * 0.16f

            val pillWidthPx = pillWidth * (0.92f + 0.08f * bulge) * stretchFactor
            val pillHeightPx = pillHeightBase * bulge * squashFactor
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
            // Sheen "mirror" — highlight cembung diagonal yang menempel di
            // pill (bergerak bersamanya lewat translationX di atas), meniru
            // pantulan cahaya pada permukaan kaca liquid yang melengkung.
            val mirrorSheen = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.16f),
                ),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(pillWidthPx * 0.7f, pillHeightPx),
            )
            // Refraksi tepi kiri/kanan — garis tegas tipis yang menekuk
            // terang, khas efek "liquid glass" ala iOS.
            val edgeRefraction = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
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
                    // Lapisan mirror + refraksi tepi, di atas sheen dasar,
                    // supaya kesan "kaca cembung memantulkan cahaya" hidup
                    // dan ikut bergerak seiring pill berpindah slot.
                    .background(mirrorSheen)
                    .background(edgeRefraction)
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
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
