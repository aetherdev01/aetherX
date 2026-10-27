package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.aether.x.ui.components.performIfEnabled
import com.aether.x.ui.components.rememberChromaticAberration
import com.aether.x.ui.theme.LocalAetherPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Halaman ke-2 onboarding: "layer" bergaya lockscreen / panel notifikasi iPhone.
 *
 * Layer menutupi Dashboard (yang sudah tampil hidup di belakangnya). User menggesernya
 * ke atas — layer mengikuti jari, tepinya berpendar RGB-split ala lensa kaca
 * (chromatic aberration) yang makin kuat saat ditarik DAN saat ditarik cepat, lalu
 * terlepas dan menyingkap Dashboard. Lepas sebelum batas → layer kembali halus (spring).
 *
 * Transisi memakai kecepatan jari (velocity hand-off) sehingga gerakan setelah dilepas
 * menyambung mulus dari gerakan drag, bukan mulai dari nol.
 *
 * @param lift 0f = layer menutupi penuh, 1f = layer sudah keluar layar. Di-hoist supaya
 *   Dashboard di belakang bisa ikut menajam (blur → jelas) seiring layer terangkat.
 */
@Composable
fun UnlockCurtain(
    lift: Animatable<Float, AnimationVector1D>,
    isEnglish: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAetherPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticEnabled.current
    val reduceMotion = LocalReduceMotionEnabled.current

    var heightPx by remember { mutableFloatStateOf(0f) }
    var unlocking by remember { mutableStateOf(false) }

    val accent = palette.accent
    val accent2 = remember(accent) { accent.shiftHue(42f) }

    // Denyut aberrasi sesaat saat layer pertama kali muncul (lensa "fokus").
    val pulse = remember { Animatable(0f) }
    // Energi gerak: naik saat jari bergerak cepat, meluruh halus saat melambat / berhenti.
    val energy = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        pulse.snapTo(1f)
        delay(200)
        pulse.animateTo(0f, tween(durationMillis = 1500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }

    // Jam animasi ambient (blob cahaya) & riak orb. Mode kurangi gerakan → posisi diam.
    val infinite = rememberInfiniteTransition(label = "curtain-ambient")
    val ambientT: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.25f) }
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
            label = "curtain-ambient-t",
        )
    }
    val rippleT: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.2f) }
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
            label = "curtain-ripple-t",
        )
    }

    val idlePx = with(density) { 1.2.dp.toPx() }
    val dragPx = with(density) { 14.dp.toPx() }
    val energyPx = with(density) { 8.dp.toPx() }
    val pulsePx = with(density) { 9.dp.toPx() }
    val chroma = rememberChromaticAberration {
        if (reduceMotion) {
            0f
        } else {
            val p = lift.value.coerceIn(0f, 1f)
            idlePx + dragPx * (p * (2f - p)) + energyPx * energy.value + pulsePx * pulse.value
        }
    }

    val unlock: (Float) -> Unit = { liftVel ->
        if (!unlocking) {
            unlocking = true
            scope.launch {
                haptic.performIfEnabled(hapticsEnabled, HapticFeedbackType.LongPress)
                energy.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            }
            scope.launch {
                // Fling cepat → spring yang melanjutkan kecepatan jari. Ketukan → easing panjang
                // yang lembut di ujung (easeOutQuint).
                val spec: AnimationSpec<Float> = when {
                    reduceMotion -> tween(120)
                    liftVel > 0.8f -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)
                    else -> tween(640, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                }
                lift.animateTo(1f, spec, initialVelocity = liftVel.coerceIn(0f, 8f))
                onUnlocked()
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (heightPx > 0f && !unlocking) {
            val target = (lift.value - delta / heightPx).coerceIn(0f, 1f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { lift.snapTo(target) }
            if (!reduceMotion) {
                val kick = (abs(delta) / (heightPx * 0.012f)).coerceIn(0f, 1f)
                scope.launch {
                    energy.animateTo(kick, tween(90, easing = LinearEasing))
                    energy.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    val bleedDp = 14.dp
    val bleedPx = with(density) { bleedDp.roundToPx() }

    // Box luar TIDAK bergerak: ia yang menerima drag (menghindari jitter koordinat) dan
    // sekaligus memblokir sentuhan ke Dashboard selama layer masih menutup.
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = !unlocking,
                onDragStopped = { velocity ->
                    // Kecepatan jari (px/s) → kecepatan "lift" (fraksi layar/s), positif = ke atas.
                    val liftVel = -velocity / heightPx.coerceAtLeast(1f)
                    if (lift.value > 0.3f || velocity < -1100f) {
                        unlock(liftVel)
                    } else {
                        scope.launch {
                            lift.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                initialVelocity = liftVel.coerceIn(-8f, 8f),
                            )
                        }
                    }
                },
            ),
    ) {
        // Host layer: sedikit lebih tinggi dari layar (bleed) agar rumbai merah di luar
        // tepi bawah punya ruang untuk digambar oleh efek aberrasi.
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val h = constraints.maxHeight
                    val placeable = measurable.measure(
                        Constraints.fixed(constraints.maxWidth, h + bleedPx),
                    )
                    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                }
                // Terangkat sejauh layar + bleed → fringe tidak tersisa di tepi atas layar.
                .graphicsLayer {
                    translationY = -lift.value.coerceAtLeast(0f) * (size.height + bleedPx)
                }
                .then(chroma),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bleedDp)
                    .graphicsLayer {
                        // Sudut bawah membulat begitu layer mulai terangkat; saat menutup
                        // penuh tetap persegi supaya tidak ada celah di sudut layar.
                        val r = (lift.value * 14f).coerceIn(0f, 1f) * 40.dp.toPx()
                        shape = RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        clip = true
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.bg.copy(alpha = 0.78f),
                                lerp(palette.bg, accent, 0.20f).copy(alpha = 0.76f),
                                lerp(palette.bg, accent2, 0.12f).copy(alpha = 0.82f),
                                palette.bg.copy(alpha = 0.90f),
                            ),
                        ),
                    )
                    .drawBehind {
                        // Dua blob cahaya yang melayang pelan — hidup tapi tidak mengganggu.
                        val ang = ambientT.value * 2f * PI.toFloat()
                        val r1 = size.width * 0.95f
                        val c1 = Offset(
                            size.width * (0.5f + 0.18f * cos(ang)),
                            size.height * (0.34f + 0.05f * sin(ang)),
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
                        val r2 = size.width * 0.8f
                        val c2 = Offset(
                            size.width * (0.5f - 0.28f * cos(ang)),
                            size.height * (0.74f + 0.04f * sin(2f * ang)),
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
            ) {
                CurtainContent(
                    lift = lift,
                    isEnglish = isEnglish,
                    accent = accent,
                    accent2 = accent2,
                    ripple = rippleT,
                    onHintClick = { unlock(0f) },
                )
                // Tepi bawah berupa "bibir kaca": fringe RGB + kilau yang bergeser mengikuti lift.
                LensRim(
                    lift = lift,
                    energy = energy,
                    pulse = pulse,
                    accent = accent,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Tepi bawah layer ala lensa kaca. Digambar manual (di luar shader chromatic aberration)
 * supaya bisa dikontrol penuh:
 *  - lip: pendar kaca tipis di atas tepi,
 *  - glint: kilau cahaya yang bergeser kiri→kanan seiring layer terangkat,
 *  - fringe: tiga pita merah/hijau/biru yang saling menjauh (dispersi), digabung additive
 *    sehingga inti tepi tampak putih dan pinggirnya berwarna — seperti prisma sungguhan.
 */
@Composable
private fun LensRim(
    lift: Animatable<Float, AnimationVector1D>,
    energy: Animatable<Float, AnimationVector1D>,
    pulse: Animatable<Float, AnimationVector1D>,
    accent: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            val p = lift.value.coerceIn(0f, 1f)
            val eased = p * (2f - p)
            val strength = if (reduceMotion) {
                0.3f
            } else {
                (0.28f + 0.55f * eased + 0.5f * energy.value + 0.6f * pulse.value).coerceIn(0f, 1.5f)
            }
            val w = size.width
            val h = size.height
            val band = 1.4.dp.toPx() * (1f + 0.6f * strength)
            val spread = 1.2.dp.toPx() * (0.6f + 3.2f * strength)
            val lipH = 30.dp.toPx() * (0.8f + strength)
            val s1 = strength.coerceAtMost(1f)

            // Lip kaca.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.10f * strength),
                        Color.White.copy(alpha = 0.14f * strength),
                    ),
                    startY = h - lipH,
                    endY = h,
                ),
                topLeft = Offset(0f, h - lipH),
                size = Size(w, lipH),
            )

            // Kilau yang bergeser.
            val cx = w * (0.12f + 0.76f * p)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f * s1), Color.Transparent),
                    center = Offset(cx, h),
                    radius = w * 0.34f,
                ),
                topLeft = Offset(0f, h - lipH),
                size = Size(w, lipH),
            )

            // Fringe dispersi: merah paling bawah, biru paling atas, hijau di tengah.
            fun fringe(color: Color, y: Float) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            color.copy(alpha = 0.60f * s1),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(w, band),
                    blendMode = BlendMode.Plus,
                )
            }
            fringe(Color(0xFFFF2D55), h - band)
            fringe(Color(0xFF30FF9A), h - band - spread)
            fringe(Color(0xFF2E7BFF), h - band - 2f * spread)

            // Garis tipis paling bawah sebagai "mata pisau" kaca.
            drawRect(
                color = Color.White.copy(alpha = 0.32f * s1),
                topLeft = Offset(0f, h - 1.dp.toPx()),
                size = Size(w, 1.dp.toPx()),
            )
        },
    )
}

@Composable
private fun CurtainContent(
    lift: Animatable<Float, AnimationVector1D>,
    isEnglish: Boolean,
    accent: Color,
    accent2: Color,
    ripple: State<Float>,
    onHintClick: () -> Unit,
) {
    val palette = LocalAetherPalette.current
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotionEnabled.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()

    // Masuk berurutan (stagger): pil → orb → judul → subjudul → petunjuk.
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(140)
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
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        // Pil status teratas.
        Row(
            modifier = Modifier
                .enter(0)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.13f))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "AETHERX",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.3.sp,
                color = colors.onSurface,
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (status.rootGranted) palette.success else colors.onSurfaceVariant),
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

        Box(modifier = Modifier.enter(1)) {
            AetherOrb(accent = accent, accent2 = accent2, ripple = ripple)
        }
        Spacer(modifier = Modifier.height(30.dp))
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
            modifier = Modifier.enter(3),
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

        // Petunjuk geser. Bisa juga diketuk (aksesibilitas / TalkBack).
        val hintLabel = if (isEnglish) "Swipe up to open" else "Geser ke atas untuk membuka"
        Column(
            modifier = Modifier
                .enter(4)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = hintLabel,
                    onClick = onHintClick,
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dua chevron yang bergantian menyala (efek "mengalir ke atas").
            Box(modifier = Modifier.size(width = 30.dp, height = 42.dp)) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = 0.25f + 0.75f * bob.value
                            translationY = -5.dp.toPx() * bob.value
                        },
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = 0.25f + 0.75f * (1f - bob.value)
                            translationY = -5.dp.toPx() * (1f - bob.value)
                        },
                )
            }
            Text(
                text = hintLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
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

/**
 * Orb logo di tengah: inti gradien aksen + riak yang memancar keluar. Tiap riak punya
 * pecahan warna merah/biru tipis (RGB-split) yang menyatu dengan tema lensa.
 */
@Composable
private fun AetherOrb(
    accent: Color,
    accent2: Color,
    ripple: State<Float>,
) {
    val orbSize = 96.dp
    Box(
        modifier = Modifier
            .size(196.dp)
            .graphicsLayer {
                val breathe = 1f + 0.025f * sin(ripple.value * 2f * PI.toFloat())
                scaleX = breathe
                scaleY = breathe
            }
            .drawBehind {
                val c = center
                val orbR = orbSize.toPx() / 2f
                val maxR = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.38f), Color.Transparent),
                        center = c,
                        radius = maxR,
                    ),
                    radius = maxR,
                    center = c,
                )
                val sw = 1.5.dp.toPx()
                for (k in 0 until 3) {
                    val ph = (ripple.value + k / 3f) % 1f
                    val inv = 1f - ph
                    val e = 1f - inv * inv * inv
                    val r = orbR + (maxR - orbR) * e
                    val a = inv * inv * 0.55f
                    val split = 1.4.dp.toPx() * e
                    drawCircle(
                        color = Color(0xFFFF3B5C).copy(alpha = a * 0.55f),
                        radius = r + split,
                        center = c,
                        style = Stroke(sw),
                    )
                    drawCircle(
                        color = Color(0xFF2EA8FF).copy(alpha = a * 0.55f),
                        radius = (r - split).coerceAtLeast(0f),
                        center = c,
                        style = Stroke(sw),
                    )
                    drawCircle(
                        color = accent.copy(alpha = a),
                        radius = r,
                        center = c,
                        style = Stroke(sw),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = listOf(accent, accent2)))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
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
