package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.LocalHapticEnabled
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.components.liquidGlassTintAlpha
import com.aether.x.ui.components.performIfEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.LocalAetherStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Lensa: refraksi tepi dari library Kyant (sama seperti navbar), tanpa dispersi warna ──
/** Tebal pita refraksi dari tepi bawah layer. Navbar: 8.5dp (kapsul kecil), layer besar butuh lebih tebal. */
private const val LENS_HEIGHT_DP = 36f
/** Pergeseran maksimum di tepi. Rasio tinggi:amount tetap 1:2 seperti navbar (8.5 : 17). */
private const val LENS_AMOUNT_DP = 84f
/**
 * Kekuatan lensa TETAP (tidak berubah saat ditarik, tidak bergantung kecepatan jari). Sebelumnya
 * kekuatan lensa ikut kecepatan jari + posisi tarikan → tepi bergetar seperti jelly.
 */
private const val LENS_STRENGTH = 0.85f

// ── Geometri host layer ──
/**
 * Host dibuat lebih besar dari layar di atas & samping supaya lensa HANYA terlihat di tepi bawah
 * dan lengkungnya. Bleed harus >= tebal pita lensa maksimum (LENS_HEIGHT_DP * LENS_STRENGTH ≈ 31dp
 * + margin) agar tepi atas/samping yang ikut membiaskan jatuh di luar layar.
 */
private const val BLEED_TOP_DP = 64f
private const val BLEED_SIDE_DP = 0f
/** Jarak ekstra saat terangkat penuh supaya bayangan tepi bawah ikut keluar layar. */
private const val EXIT_EXTRA_DP = 32f
/**
 * Radius sudut bawah, TETAP. Karena radius > bleed samping, busur sudut mulai terlihat di dalam
 * layar: tepi bawah naik ~32dp di kiri/kanan layar, dan lensa mengikuti lengkung itu.
 */
private const val CORNER_DP = 40f
/** Batas tarik ke bawah (rubber band), fraksi tinggi layar. */
private const val RUBBER_BAND = 0.04f

/** Rubber band ala iOS: makin jauh ditarik makin berat, dan berhenti di [limit]. */
private fun rubberBand(x: Float, limit: Float): Float =
    if (limit <= 0f || x <= 0f) 0f else (1f - 1f / (x * 0.55f / limit + 1f)) * limit

/**
 * Halaman ke-2 onboarding: layer bergaya lockscreen iPhone di atas Dashboard.
 *
 * - Layer = kaca: [drawBackdrop] mengambil Dashboard di belakangnya ([backdrop]) lalu menerapkan
 *   `lens(...)` — refraksi tepi seperti kapsul LiquidBottomTabs, tanpa dispersi warna —
 *   sehingga tepi bawah membiaskan Dashboard dengan jernih, bukan bayangan ganda.
 * - Ikut jari 1:1, ditarik ke bawah melewati posisi awal terasa karet (rubber band).
 * - Dilepas: keputusan memakai proyeksi kecepatan (bukan cuma jarak), lalu spring yang
 *   MELANJUTKAN kecepatan jari — tidak ada patahan antara drag dan animasi.
 *
 * @param lift 0f = menutup penuh, 1f = sudah keluar layar (sedikit negatif saat rubber band).
 * @param backdrop layer Dashboard (`rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)` di OnboardingFlow).
 */
@Composable
fun UnlockCurtain(
    lift: Animatable<Float, AnimationVector1D>,
    backdrop: Backdrop,
    isEnglish: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    /** 0 → 1 saat layer "maju dari depan" (transisi dari halaman setup). Dibaca di fase draw. */
    enterProgress: () -> Float = { 1f },
) {
    val palette = LocalAetherPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticEnabled.current
    val reduceMotion = LocalReduceMotionEnabled.current

    var heightPx by remember { mutableFloatStateOf(0f) }
    var unlocking by remember { mutableStateOf(false) }
    // Posisi jari tanpa batas (sebelum rubber band) — sumber kebenaran selama drag.
    var rawLift by remember { mutableFloatStateOf(0f) }

    val accent = palette.accent
    // Kaca TANPA blur & tanpa dispersi warna: hanya lensa (refraksi) di tepi. Tint tipis dari Liquid Glass
    // Level (seperti drawer) agar Dashboard di belakang tetap terlihat jelas.
    val style = LocalAetherStyle.current
    val glassTint = (liquidGlassTintAlpha(style.glassLevel.coerceIn(0f, 1f)) * 0.62f).coerceIn(0.06f, 0.94f)
    val accent2 = remember(accent) { accent.shiftHue(42f) }

    val unlock: (Float) -> Unit = { liftVel ->
        if (!unlocking) {
            unlocking = true
            scope.launch {
                haptic.performIfEnabled(hapticsEnabled, HapticFeedbackType.LongPress)
            }
            scope.launch {
                // Spring teredam kritis: mulai dari kecepatan jari, melambat halus di ujung, tanpa
                // memantul. Ketukan (v = 0) mulai pelan lalu mempercepat — terasa seperti easing.
                val spec: AnimationSpec<Float> =
                    if (reduceMotion) tween(120) else spring(dampingRatio = 1f, stiffness = 210f)
                lift.animateTo(1f, spec, initialVelocity = liftVel.coerceIn(0f, 10f))
                onUnlocked()
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (heightPx > 0f && !unlocking) {
            rawLift = (rawLift - delta / heightPx).coerceIn(-1f, 1f)
            val target = if (rawLift >= 0f) rawLift else -rubberBand(-rawLift, RUBBER_BAND)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { lift.snapTo(target) }
        }
    }

    val bleedTopPx = with(density) { BLEED_TOP_DP.dp.roundToPx() }
    val bleedSidePx = with(density) { BLEED_SIDE_DP.dp.roundToPx() }
    val exitExtraPx = with(density) { EXIT_EXTRA_DP.dp.toPx() }
    val cornerPx = with(density) { CORNER_DP.dp.toPx() }

    // Box luar TIDAK bergerak: ia yang menerima drag dan memblokir sentuhan ke Dashboard.
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = !unlocking,
                onDragStarted = { rawLift = lift.value.coerceAtLeast(0f) },
                onDragStopped = { velocity ->
                    // px/s (+ = jari ke bawah) → fraksi layar/s (+ = layer naik).
                    val liftVel = -velocity / heightPx.coerceAtLeast(1f)
                    // Proyeksi ~180 ms ke depan: flick pendek tapi cepat tetap membuka,
                    // tarikan jauh tapi dibalik ke bawah kembali menutup.
                    val projected = lift.value + liftVel * 0.18f
                    if (projected > 0.42f) {
                        unlock(liftVel)
                    } else {
                        scope.launch {
                            lift.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
                                initialVelocity = liftVel.coerceIn(-10f, 10f),
                            )
                        }
                    }
                },
            ),
    ) {
        Box(
            modifier = Modifier
                // Host lebih tinggi dari layar di atas (bleed atas): tepi atas tersembunyi, sisi kiri/kanan
                // dan sudut bawah ikut kena lensa.
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val placeable = measurable.measure(
                        Constraints.fixed(w + 2 * bleedSidePx, h + bleedTopPx),
                    )
                    layout(w, h) { placeable.place(-bleedSidePx, -bleedTopPx) }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = {
                        // Bentuk TETAP (tidak dibaca dari lift) → shader lensa tidak berubah tiap frame.
                        RoundedCornerShape(topStart = 0f, topEnd = 0f, bottomEnd = cornerPx, bottomStart = cornerPx)
                    },
                    effects = {
                        // Lensa murni (refraksi tepi) dengan kekuatan tetap — tanpa dispersi warna.
                        lens(
                            LENS_HEIGHT_DP.dp.toPx() * LENS_STRENGTH,
                            LENS_AMOUNT_DP.dp.toPx() * LENS_STRENGTH,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(width = 1.2.dp, alpha = 0.75f)
                    },
                    shadow = {
                        Shadow(alpha = 0.34f * (abs(lift.value) * 6f).coerceIn(0f, 1f))
                    },
                    layerBlock = {
                        val e = enterProgress()
                        // Layer di bawah halaman setup: membesar halus ke ukuran penuh saat halaman terangkat.
                        val enterScale = 0.94f + 0.06f * e.coerceIn(0f, 1f)
                        scaleX = enterScale
                        scaleY = enterScale
                        val l = lift.value
                        val screenH = size.height - bleedTopPx
                        translationY = if (l >= 0f) {
                            -l * (screenH + exitExtraPx)
                        } else {
                            // Rubber band: layer turun sedikit, tertutup oleh bleed atas.
                            (-l * screenH).coerceAtMost(bleedTopPx * 0.9f)
                        }
                    },
                    onDrawSurface = {
                        // Tint tipis seperti drawer: Dashboard di belakang tetap terbaca.
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(palette.surface, accent, 0.10f).copy(alpha = glassTint),
                                    palette.surface.copy(alpha = glassTint * 0.82f),
                                ),
                            ),
                        )
                        // Bibir kaca: cahaya tipis menumpuk di ~56dp terakhir tepi bawah, sehingga
                        // pita refraksi terlihat seperti ketebalan kaca, bukan sekadar distorsi.
                        val lip = 56.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f)),
                                startY = size.height - lip,
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, size.height - lip),
                            size = androidx.compose.ui.geometry.Size(size.width, lip),
                        )
                    },
                ),
        ) {
            // Cahaya latar statis di layer sendiri (graphicsLayer {}) → tidak ikut me-render ulang
            // backdrop + shader lensa.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { }
                    .drawBehind {
                        val screenH = size.height - bleedTopPx
                        val r1 = size.width * 0.95f
                        val c1 = Offset(
                            size.width * 0.64f,
                            bleedTopPx + screenH * 0.34f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                                center = c1,
                                radius = r1,
                            ),
                            radius = r1,
                            center = c1,
                        )
                        val sc = Offset(size.width / 2f, bleedTopPx + screenH * 0.5f)
                        val sr = size.width * 0.95f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.bg.copy(alpha = 0.42f), Color.Transparent),
                                center = sc,
                                radius = sr,
                            ),
                            radius = sr,
                            center = sc,
                        )
                        val r2 = size.width * 0.8f
                        val c2 = Offset(
                            size.width * 0.22f,
                            bleedTopPx + screenH * 0.76f,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent2.copy(alpha = 0.18f), Color.Transparent),
                                center = c2,
                                radius = r2,
                            ),
                            radius = r2,
                            center = c2,
                        )
                    },
            )
            // Konten dikembalikan ke area layar (buang bleed atas & samping).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = BLEED_SIDE_DP.dp, end = BLEED_SIDE_DP.dp, top = BLEED_TOP_DP.dp),
            ) {
                CurtainContent(
                    lift = lift,
                    isEnglish = isEnglish,
                    accent = accent,
                    onHintClick = { unlock(0f) },
                )
            }
        }
    }
}

@Composable
private fun CurtainContent(
    lift: Animatable<Float, AnimationVector1D>,
    isEnglish: Boolean,
    accent: Color,
    onHintClick: () -> Unit,
) {
    val palette = LocalAetherPalette.current
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotionEnabled.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()

    // Masuk berurutan (stagger) setelah halaman setup terangkat: pil → judul → petunjuk.
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(320)
        entrance.animateTo(1f, tween(durationMillis = 1500, easing = LinearEasing))
    }
    fun stage(i: Int): Float {
        val raw = ((entrance.value - i * 0.1f) / 0.55f).coerceIn(0f, 1f)
        val inv = 1f - raw
        return 1f - inv * inv * inv // easeOutCubic
    }
    fun Modifier.enter(i: Int): Modifier = graphicsLayer {
        val s = stage(i)
        alpha = s
        translationY = (1f - s) * 26.dp.toPx()
    }

    val bobInfinite = rememberInfiniteTransition(label = "curtain-hint")
    val bob: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        bobInfinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "curtain-hint-bob",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Parallax kedalaman: isi memudar, mengecil sedikit, dan melaju lebih cepat
                // dari layer sehingga terasa berlapis.
                val p = lift.value.coerceIn(0f, 1f)
                alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                translationY = -p * size.height * 0.2f
                val s = 1f - 0.07f * p
                scaleX = s
                scaleY = s
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // ── Pil status ──
        Row(
            modifier = Modifier
                .enter(0)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.30f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (status.rootGranted) palette.success else colors.onSurfaceVariant),
            )
            Text(
                text = "AETHERX",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.3.sp,
                color = colors.onSurface,
            )
            Text(
                text = when {
                    status.rootGranted -> if (isEnglish) "ACCESS READY" else "AKSES SIAP"
                    else -> if (isEnglish) "LIMITED MODE" else "MODE TERBATAS"
                },
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Sambutan ──
        Text(
            modifier = Modifier.enter(2),
            text = if (isEnglish) "Welcome to AetherX" else "Selamat datang di AetherX",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.enter(2),
            text = if (isEnglish) {
                "Everything is set. Your dashboard is ready."
            } else {
                "Semua pengaturan selesai. Dashboard kamu sudah siap."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Petunjuk geser: teks saja (bisa diketuk untuk aksesibilitas / TalkBack) ──
        val hintLabel = if (isEnglish) "Swipe up to open" else "Geser ke atas untuk membuka"
        Text(
            modifier = Modifier
                .enter(2)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = hintLabel,
                    onClick = onHintClick,
                )
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .graphicsLayer { alpha = 0.55f + 0.45f * bob.value },
            text = hintLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
            color = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Grabber ala home indicator iOS — melebar sedikit saat layer ditarik.
        Box(
            modifier = Modifier
                .width(134.dp)
                .height(5.dp)
                .graphicsLayer { scaleX = 1f + 0.3f * lift.value.coerceIn(0f, 1f) }
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Menggeser hue warna (derajat) — dipakai untuk warna sekunder gradien dari warna aksen tema. */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    hsv[1] = hsv[1].coerceAtLeast(0.35f)
    hsv[2] = hsv[2].coerceAtLeast(0.7f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = this.alpha)
}
