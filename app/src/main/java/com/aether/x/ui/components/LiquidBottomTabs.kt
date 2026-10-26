/*
 * Adapted from Kyant0/AndroidLiquidGlass KMP catalog components (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 */
package com.aether.x.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.aether.x.ui.theme.LocalAetherStyle
import com.aether.x.ui.theme.LocalAetherPalette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

/** Blur minimum kaca navbar (dp). Elemen di belakang navbar selalu ter-blur, tidak pernah tajam. */
internal const val NAV_MIN_BLUR_DP = 18f

/** Continuous selection progress driven by the moving capsule, avoiding stale tab colors. */
internal val LocalLiquidBottomTabSelectionProgress =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

// ---------------------------------------------------------------------------------------------
// Penyetelan visual kapsul & tab. Semua angka yang sering diutak-atik ada di sini.
// ---------------------------------------------------------------------------------------------

/** Skala ikon + label SEMUA tab (idle, pita lensa, dan foreground). 1f = ukuran lama. */
private const val NAV_TAB_CONTENT_SCALE = 1.16f

/** Pembesaran ikon/label di dalam kapsul saat ditahan (dikali [NAV_TAB_CONTENT_SCALE]). */
private const val CAPSULE_MAGNIFY = 1.2f

/** Skala kapsul saat ditahan (78dp / 56dp). */
private const val CAPSULE_PRESSED_SCALE = 78f / 56f

/**
 * Lensa kapsul: amount lebih besar menghasilkan fringe merah/biru yang lebih jelas. Tinggi tetap
 * dijaga supaya efek refraksi tidak memenuhi seluruh kapsul.
 */
private const val CAPSULE_LENS_HEIGHT_DP = 8.5f
private const val CAPSULE_LENS_AMOUNT_DP = 17f

/** Aberasi tipis tetap aktif saat idle; interaksi menaikkannya sampai kekuatan penuh. */
private const val CAPSULE_LENS_IDLE_PROGRESS = 0.28f

/**
 * Squash & stretch kapsul saat bergerak. Simetris kiri/kanan, jenuh halus (tanh) dan kecil, jadi
 * kapsul tidak pernah memipih. [CAPSULE_STRETCH_MAX] = lebar maks +9%; tinggi menyusut
 * [CAPSULE_SQUASH_RATIO] kalinya. [CAPSULE_STRETCH_SPEED_REF] = kecepatan (tab/detik) di mana
 * efek mencapai ~76% dari maksimum.
 */
private const val CAPSULE_STRETCH_MAX = 0.09f
private const val CAPSULE_SQUASH_RATIO = 0.55f
private const val CAPSULE_STRETCH_SPEED_REF = 14f

/**
 * Perpindahan tab jauh. Spring posisi bawaan kaku (1000) dan tidak peduli jarak, jadi lompat 4 tab
 * melaju ~47 tab/detik (terasa kasar). Jarak > [FAR_DIRECT_MAX_TABS] ditempuh lewat "lompatan"
 * berantai sepanjang [FAR_HOP_TABS] tab; lompatan berikutnya disambung saat kapsul sudah menempuh
 * [FAR_HOP_HANDOFF] dari lompatan sekarang, jadi kapsul meluncur kontinu (puncak ~21 tab/detik)
 * dengan durasi yang ikut jarak. Target akhir tetap benar sehingga lepas-tekan lensa tetap sinkron.
 */
private const val FAR_DIRECT_MAX_TABS = 2.0f
private const val FAR_HOP_TABS = 1.2f
private const val FAR_HOP_HANDOFF = 0.6f
private const val FAR_HOP_TIMEOUT_MS = 450L

/** State gesture non-snapshot; hanya disentuh dari callback gesture. */
private class TabDragState {
    var target = 0f
    var travelled = 0f
}

/**
 * Menggerakkan kapsul ke tab tujuan. Dekat -> perilaku bawaan [DampedDragAnimation]. Jauh ->
 * lompatan berantai (lihat [FAR_HOP_TABS]). Dipanggil dari callback gesture dan perubahan dari luar.
 */
private class CapsuleTravel(private val scope: CoroutineScope) {
    private var job: Job? = null

    /** Tujuan akhir perjalanan terakhir; NaN bila jari mengambil alih (drag). */
    var lastTarget: Float = Float.NaN
        private set

    /** Jari mulai menggeser: hentikan perjalanan otomatis, drag yang menggerakkan kapsul. */
    fun takeOver() {
        job?.cancel()
        job = null
        lastTarget = Float.NaN
    }

    /** [settle] = true: akhiri dengan animateToValue (press -> settle -> release, seperti bawaan). */
    fun travel(anim: DampedDragAnimation, target: Float, settle: Boolean) {
        job?.cancel()
        job = null
        lastTarget = target
        val from = anim.value
        if (abs(target - from) <= FAR_DIRECT_MAX_TABS) {
            if (settle) anim.animateToValue(target) else anim.updateValue(target)
            return
        }
        job = scope.launch {
            var pos = from
            var guard = 0
            while (abs(target - pos) > FAR_HOP_TABS * 1.6f && guard++ < 24) {
                val dir = sign(target - pos)
                val segStart = pos
                anim.updateValue(segStart + dir * FAR_HOP_TABS)
                pos = withTimeoutOrNull(FAR_HOP_TIMEOUT_MS) {
                    snapshotFlow { anim.value }
                        .first { (it - segStart) * dir >= FAR_HOP_TABS * FAR_HOP_HANDOFF }
                } ?: anim.value
            }
            if (settle) anim.animateToValue(target) else anim.updateValue(target)
        }
    }
}

/**
 * Liquid BottomTabs (pola capsule/pill Kyant0/AndroidLiquidGlass).
 *
 * Interaksi:
 * - Tekan/tahan di tab mana pun: kapsul langsung berjalan (spring) ke tab itu tanpa menunggu lepas.
 * - Geser: kapsul mengikuti jari. Lepas: pindah ke tab terdekat dan [onTabSelected] dipanggil.
 * - Tap singkat: sama dengan tahan lalu lepas (juga memanggil [onTabSelected] untuk tab yang sama).
 *
 * Tinggi kapsul = [barHeight] - 8dp (inset 4dp rata di semua sisi, konsentris dengan navbar).
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 56f.dp,
    content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount <= 0) return
    val style = LocalAetherStyle.current
    val palette = LocalAetherPalette.current
    val glass = style.glassLevel.coerceIn(0f, 1f)
    val capsuleHeight = barHeight - 8f.dp
    val sizeFactor = capsuleHeight.value / 56f
    // Kapsul idle memakai tint aksen yang bening, bukan abu-abu dari textPrimary.
    val idleCapsuleFill = palette.accent.copy(alpha = 0.075f + 0.025f * glass)
    val idleCapsuleAccent = palette.accent.copy(alpha = 0.035f)
    val containerColor = palette.surface.copy(alpha = liquidGlassTintAlpha(glass))
    val navBlurDp = maxOf(blurRadiusFor(style.blur.coerceIn(0f, 1f)), NAV_MIN_BLUR_DP)
    // Gradient tint vertikal: atas lebih bening, bawah lebih pekat.
    val containerBrush = Brush.verticalGradient(
        listOf(
            containerColor.copy(alpha = (containerColor.alpha * 0.70f).coerceIn(0f, 1f)),
            containerColor.copy(alpha = (containerColor.alpha * 1.15f).coerceIn(0f, 1f)),
        ),
    )
    val tabsBackdrop = rememberLayerBackdrop()
    val selectedTabReaderState = rememberUpdatedState(selectedTabIndex)
    val onTabSelectedState = rememberUpdatedState(onTabSelected)
    val selectedTabReader = remember { { selectedTabReaderState.value() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }
        val tapSlopPx = with(density) { 8f.dp.toPx() }
        val tabWidthState = rememberUpdatedState(tabWidth)
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val fraction = (offsetAnimation.value / width).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabReader) { mutableIntStateOf(selectedTabReader()) }
        val drag = remember { TabDragState() }
        val capsuleTravel = remember(animationScope) { CapsuleTravel(animationScope) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabReader().coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = CAPSULE_PRESSED_SCALE,
                onDragStarted = { position ->
                    // Tahan di tab tujuan -> kapsul langsung "berjalan" ke sana (tanpa menunggu lepas).
                    // Tab jauh ditempuh berantai supaya tidak melesat (lihat CapsuleTravel).
                    val w = tabWidthState.value.coerceAtLeast(1f)
                    val column = (position.x / w).toInt().coerceIn(0, tabsCount - 1)
                    val index = if (isLtr) column else tabsCount - 1 - column
                    drag.target = index.toFloat()
                    drag.travelled = 0f
                    capsuleTravel.travel(this, drag.target, settle = false)
                },
                onDragStopped = {
                    val targetIndex = drag.target.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    val isTap = drag.travelled < tapSlopPx
                    val changed = targetIndex != currentIndex
                    currentIndex = targetIndex
                    capsuleTravel.travel(this, targetIndex.toFloat(), settle = true)
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    // Tap pada tab yang sama tetap diteruskan (mis. Dashboard mereset sub-tab).
                    if (changed || isTap) onTabSelectedState.value(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    val w = tabWidthState.value.coerceAtLeast(1f)
                    drag.travelled += abs(dragAmount.x)
                    drag.target = (drag.target + dragAmount.x / w * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    // Goyangan jari kecil saat tap tidak boleh membatalkan perjalanan ke tab tujuan;
                    // baru setelah melewati slop, jari mengambil alih kapsul.
                    if (drag.travelled >= tapSlopPx) {
                        capsuleTravel.takeOver()
                        updateValue(drag.target)
                    }
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        // Perubahan dari luar (mis. navigasi programatik) menggerakkan kapsul.
        LaunchedEffect(selectedTabReader) {
            snapshotFlow { selectedTabReader() }.collectLatest { index -> currentIndex = index.coerceIn(0, tabsCount - 1) }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                // Gesture sudah menggerakkan kapsul ke tab ini (lastTarget) -> jangan diulang.
                if (capsuleTravel.lastTarget != index.toFloat() &&
                    abs(dampedDragAnimation.targetValue - index) > 0.001f
                ) {
                    capsuleTravel.travel(dampedDragAnimation, index.toFloat(), settle = true)
                }
            }
        }

        // Track the capsule position continuously; tab icon/label colors use this progress.
        val selectionProgress: (Int) -> Float = { index ->
            (1f - abs(dampedDragAnimation.value - index.toFloat())).coerceIn(0f, 1f)
        }

        // Warna base row mengikuti tab yang sedang di bawah kapsul saja (tanpa pewarnaan setengah).
        val baseSelectionProgress: (Int) -> Float = { index ->
            if (dampedDragAnimation.value.fastRoundToInt() == index) 1f else 0f
        }

        // Base row: navbar kaca. Ikon di luar kapsul TIDAK ikut membesar saat ditahan.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { 1f },
            LocalLiquidBottomTabSelectionProgress provides baseSelectionProgress,
        ) {
            Row(
                // Keep the tab hit-area and layout, but do not draw a second
                // full-width frosted capsule behind every tab. Only the moving
                // selected capsule below remains glass, so content behind the
                // navbar is not covered by a broad white/blurred panel.
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // Layer "pita" navbar untuk lensa: salinan navbar yang LEBIH KECIL (setinggi kapsul idle)
        // + ikon/label membesar [CAPSULE_MAGNIFY] dan SEMUA berwarna accent. Tidak terlihat langsung
        // (alpha 0); hanya tampak lewat lensa kapsul, jadi ikon/label yang dilewati kapsul ikut berwarna.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, CAPSULE_MAGNIFY, dampedDragAnimation.pressProgress) },
            LocalLiquidBottomTabSelectionProgress provides { 1f },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                            vibrancy()
                            blur(navBlurDp.dp.toPx())
                            if (progress > 0.001f) {
                                lens(
                                    14f.dp.toPx() * progress * sizeFactor,
                                    14f.dp.toPx() * progress * sizeFactor,
                                )
                            }
                        },
                        // Rim highlight sangat halus agar tidak membentuk garis putih/abu-abu.
                        highlight = { Highlight.Default.copy(alpha = 0.10f) },
                        onDrawSurface = {
                            // Pertahankan tint kaca transparan dengan sedikit aksen, tanpa wash putih/abu.
                            drawRect(containerBrush)
                            drawRect(palette.accent.copy(alpha = 0.025f))
                        },
                    )
                    .height(capsuleHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                    else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        // Chromatic aberration tetap tampak tipis ketika kapsul diam, lalu menguat
                        // saat disentuh/ditarik. Amount lebih besar memperjelas fringe pelangi merah-biru
                        // tanpa menambah garis rim putih. Tinggi lensa tetap kecil agar isi tidak penuh.
                        val opticalProgress = lerp(CAPSULE_LENS_IDLE_PROGRESS, 1f, progress)
                        lens(
                            CAPSULE_LENS_HEIGHT_DP.dp.toPx() * opticalProgress * sizeFactor,
                            CAPSULE_LENS_AMOUNT_DP.dp.toPx() * opticalProgress * sizeFactor,
                            chromaticAberration = true,
                        )
                    },
                    // Tanpa highlight/glow putih di tepi kapsul.
                    highlight = { Highlight.Default.copy(alpha = 0f) },
                    shadow = {
                        // Bayangan dijaga lembut agar tepi kapsul tidak tampak abu-abu gelap.
                        Shadow(alpha = 0.045f + 0.16f * dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        InnerShadow(
                            radius = 5f.dp * progress,
                            alpha = 0.12f * progress,
                        )
                    },
                    layerBlock = {
                        // Skala dasar pegas (overshoot) dibuat SERAGAM di X/Y (rata-rata keduanya;
                        // spring X dan Y bawaan beda redaman) supaya lengkung kapsul tetap sebangun
                        // dengan navbar. Idle = 1f (inset 4dp rata).
                        val sx = dampedDragAnimation.scaleX
                        val sy = dampedDragAnimation.scaleY
                        val base = if (sx.isFinite() && sy.isFinite()) {
                            ((sx + sy) * 0.5f).fastCoerceIn(0.85f, CAPSULE_PRESSED_SCALE * 1.12f)
                        } else {
                            1f
                        }
                        // Squash & stretch dari kecepatan gerak. Sebelumnya velocity yang di-clamp keras
                        // di +-0.2 hampir selalu jenuh saat pindah tab (lebar +25%, tinggi -20% mendadak =
                        // kapsul "gepeng", dan miring ke arah sebaliknya saat ke kiri). Kini simetris,
                        // jenuh halus (tanh), dan kecil. velocity bawaan dinormalisasi lebar range
                        // (tabsCount - 1), dikembalikan ke satuan tab/detik; nilai tak-finite diabaikan.
                        val rawVelocity = dampedDragAnimation.velocity
                        val speedTabs = if (rawVelocity.isFinite()) {
                            abs(rawVelocity) * (tabsCount - 1).coerceAtLeast(1)
                        } else {
                            0f
                        }
                        val stretch = CAPSULE_STRETCH_MAX * tanh(speedTabs / CAPSULE_STRETCH_SPEED_REF)
                        scaleX = base * (1f + stretch)
                        scaleY = base * (1f - CAPSULE_SQUASH_RATIO * stretch)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                        val idle = 1f - progress
                        // Idle: tint aksen bening. Ditahan: fill memudar agar refraksi pita navbar
                        // terlihat jelas di dalam kapsul.
                        drawRect(idleCapsuleFill, alpha = idle)
                        drawRect(idleCapsuleAccent, alpha = idle)
                        if (progress > 0f) {
                            drawRect(palette.accent.copy(alpha = 0.025f * progress))
                        }
                    },
                )
                .height(capsuleHeight)
                .fillMaxWidth(1f / tabsCount),
        )

        // Foreground: ikon tajam untuk idle + SATU-SATUNYA penangkap gesture (tab tidak lagi
        // memakai clickable, karena gesture induk meng-consume gerakan kecil dan membatalkan klik).
        // Memudar saat ditahan agar lensa di dalam kapsul terlihat; tetap hit-testable.
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { 1f },
            LocalLiquidBottomTabSelectionProgress provides selectionProgress,
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        alpha = 1f - dampedDragAnimation.pressProgress.fastCoerceIn(0f, 1f)
                    }
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .then(dampedDragAnimation.modifier),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** One selectable tab used inside LiquidBottomTabs. Sentuhan ditangani oleh LiquidBottomTabs. */
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            // Hanya semantics (TalkBack); tidak menambah pointer input.
            .semantics {
                role = Role.Tab
                semanticsOnClick(action = { onClick(); true })
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                // Ukuran ikon + label dibesarkan di satu tempat ini (berlaku untuk semua layer).
                val targetScale = scale() * NAV_TAB_CONTENT_SCALE
                scaleX = targetScale
                scaleY = targetScale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * Mewarnai konten tab dari warna dasar ke [to] mengikuti posisi kapsul, di fase DRAW.
 * Sebelumnya warna dihitung di composition sehingga setiap frame animasi memicu recomposition
 * semua tab di ketiga layer (berat, terasa tidak responsif). Konten tab harus digambar dengan
 * warna dasar (mis. textSecondary); modifier ini meng-crossfade-nya ke [to].
 */
@Composable
fun Modifier.liquidTabTint(index: Int, to: Color): Modifier {
    val progressOf = LocalLiquidBottomTabSelectionProgress.current
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val p = progressOf(index).coerceIn(0f, 1f)
            if (p > 0f) drawRect(to.copy(alpha = to.alpha * p), blendMode = BlendMode.SrcAtop)
        }
}
