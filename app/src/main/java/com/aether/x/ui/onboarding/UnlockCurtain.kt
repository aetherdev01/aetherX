package com.aether.x.ui.onboarding

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.permission.PrivilegeManager
import com.aether.x.ui.components.LocalHapticEnabled
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.components.performIfEnabled
import com.aether.x.ui.components.rememberChromaticAberration
import com.aether.x.ui.theme.LocalAetherPalette
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Easing masuk yang panjang & lembut (mirip easeOutQuint). */
private val SoftOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Halaman ke-2 onboarding: "layer" bergaya lockscreen iPhone.
 *
 * - Masuk bertahap (stagger): pil, jam, tanggal, sambutan, lalu petunjuk muncul satu per satu
 *   dengan fade + naik + sedikit zoom, sementara cahaya aksen di belakang jam mengembang pelan.
 * - Jam & tanggal hidup: tiap digit berganti dengan geser vertikal (bukan loncat), titik dua
 *   berdenyut halus. Format 12/24 jam mengikuti pengaturan sistem.
 * - Ditarik ke atas: layer mengikuti jari, isinya bergerak lebih lambat (parallax) dan memudar,
 *   tepinya berpendar RGB-split spektral (chromatic aberration lensa) yang makin kuat.
 * - Dilepas: kalau cukup jauh / cukup cepat, layer meluncur keluar dengan spring yang meneruskan
 *   kecepatan jari (tanpa patahan); kalau tidak, kembali dengan spring tanpa celah di atas.
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

    // Progres masuk 0..1 (menggerakkan stagger konten + cahaya latar).
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) enter.animateTo(1f, tween(durationMillis = 1250, easing = SoftOut))
    }

    // Denyut aberrasi sesaat saat layer pertama kali muncul (lensa "fokus"), turun pelan.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        pulse.snapTo(1f)
        delay(180)
        pulse.animateTo(0f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
    }

    // Cahaya latar bergeser sangat pelan (dibaca di fase draw, tanpa recomposition).
    val drift = rememberInfiniteTransition(label = "curtain-drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18000, easing = LinearEasing)),
        label = "curtain-drift-value",
    )

    val idlePx = with(density) { 1.6.dp.toPx() }
    val dragPx = with(density) { 16.dp.toPx() }
    val pulsePx = with(density) { 9.dp.toPx() }
    val chroma = rememberChromaticAberration {
        if (reduceMotion) {
            0f
        } else {
            val p = lift.value.coerceIn(0f, 1f)
            idlePx + dragPx * (p * (2f - p)) + pulsePx * pulse.value
        }
    }

    val unlock: (Float) -> Unit = { velocityPx ->
        if (!unlocking) {
            unlocking = true
            scope.launch {
                haptic.performIfEnabled(hapticsEnabled, HapticFeedbackType.LongPress)
                if (reduceMotion) {
                    lift.animateTo(1f, tween(durationMillis = 120))
                } else {
                    // Spring teredam kritis + kecepatan awal dari jari: lanjut mulus dari gesture.
                    val v = if (heightPx > 0f) (-velocityPx / heightPx).coerceAtLeast(0f) else 0f
                    lift.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 110f),
                        initialVelocity = v,
                    )
                }
                onUnlocked()
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (heightPx > 0f && !unlocking) {
            val target = (lift.value - delta / heightPx).coerceIn(0f, 1f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { lift.snapTo(target) }
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
                    if (lift.value > 0.3f || velocity < -1100f) {
                        unlock(velocity)
                    } else {
                        scope.launch {
                            lift.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = 220f,
                                ),
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
                // coerceAtLeast(0): saat spring kembali sedikit melewati 0, layer tidak turun
                // dan tidak membuka celah di atas.
                .graphicsLayer { translationY = -lift.value.coerceAtLeast(0f) * size.height }
                .then(chroma),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bleedDp)
                    .graphicsLayer {
                        // Sudut bawah membulat begitu layer mulai terangkat; saat menutup
                        // penuh tetap persegi supaya tidak ada celah di sudut layar.
                        val r = (lift.value * 14f).coerceIn(0f, 1f) * 36.dp.toPx()
                        shape = RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        clip = true
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.bg.copy(alpha = 0.80f),
                                lerp(palette.bg, palette.accent, 0.16f).copy(alpha = 0.78f),
                                palette.bg.copy(alpha = 0.88f),
                            ),
                        ),
                    )
                    .drawBehind {
                        // Dua cahaya aksen yang melayang pelan + mengembang saat masuk.
                        val e = enter.value
                        val ph = drift.value * 2f * PI.toFloat()
                        val r1 = size.width * (0.45f + 0.40f * e)
                        val c1 = Offset(
                            size.width * (0.5f + 0.10f * sin(ph)),
                            size.height * (0.34f + 0.025f * cos(ph)),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.accent.copy(alpha = 0.26f * e), Color.Transparent),
                                center = c1,
                                radius = r1,
                            ),
                            radius = r1,
                            center = c1,
                        )
                        val r2 = size.width * 0.7f
                        val c2 = Offset(
                            size.width * (0.5f - 0.28f * cos(ph)),
                            size.height * (0.80f + 0.03f * sin(ph)),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.accent.copy(alpha = 0.13f * e), Color.Transparent),
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
                    enter = { enter.value },
                    isEnglish = isEnglish,
                    reduceMotion = reduceMotion,
                    onHintClick = { unlock(0f) },
                )
                // Cahaya tipis naik dari tepi bawah supaya batas layer terasa seperti kaca.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, palette.accent.copy(alpha = 0.16f)),
                            ),
                        ),
                )
                // Garis tepi bawah — di sinilah efek lensa paling terlihat saat ditarik.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    palette.accent.copy(alpha = 0.95f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * Masuk bertahap: [progress] 0..1 dipetakan ke jendela [start]..[end]; hasilnya fade, naik
 * [rise], dan zoom 96% → 100% dengan easing keluar kubik. Dibaca di fase layer (tanpa recompose).
 */
private fun Modifier.staggerIn(
    progress: () -> Float,
    start: Float,
    end: Float,
    rise: Dp = 24.dp,
): Modifier = graphicsLayer {
    val t = ((progress() - start) / (end - start)).coerceIn(0f, 1f)
    val e = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = e
    translationY = (1f - e) * rise.toPx()
    val s = 0.96f + 0.04f * e
    scaleX = s
    scaleY = s
}

@Composable
private fun CurtainContent(
    lift: Animatable<Float, AnimationVector1D>,
    enter: () -> Float,
    isEnglish: Boolean,
    reduceMotion: Boolean,
    onHintClick: () -> Unit,
) {
    val palette = LocalAetherPalette.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()

    val locale = remember(isEnglish) {
        if (isEnglish) AppLanguage.ENGLISH.locale else AppLanguage.INDONESIAN.locale
    }
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }
    // Diperbarui tepat di pergantian menit — bukan tiap detik.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - (value % 60_000L) + 50L)
        }
    }
    val timeText = remember(now, is24h, locale) {
        SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", locale).format(Date(now))
    }
    val ampmText = remember(now, is24h, locale) {
        if (is24h) null else SimpleDateFormat("a", locale).format(Date(now)).uppercase(locale)
    }
    val dateText = remember(now, locale) {
        SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now))
    }

    val bob = rememberInfiniteTransition(label = "curtain-hint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "curtain-hint-bob",
    )
    val colon = rememberInfiniteTransition(label = "curtain-colon").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "curtain-colon-alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Parallax: isi bergerak lebih lambat daripada layer, mengecil & memudar halus.
                val p = lift.value.coerceIn(0f, 1f)
                translationY = p * size.height * 0.16f
                val s = 1f - 0.05f * p
                scaleX = s
                scaleY = s
                alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
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
                .staggerIn(enter, 0.00f, 0.45f, rise = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.accent.copy(alpha = 0.13f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = palette.accent,
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

        // ── Jam: tiap digit berganti dengan geser vertikal ──
        Row(
            modifier = Modifier.staggerIn(enter, 0.10f, 0.65f, rise = 34.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val clockStyle = TextStyle(
                fontSize = 86.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                color = colors.onSurface,
                fontFeatureSettings = "tnum",
            )
            timeText.forEachIndexed { index, ch ->
                // Kunci dihitung dari belakang supaya digit menit tidak ikut "loncat" saat
                // panjang teks berubah (mis. 9:59 → 10:00).
                key(timeText.length - index) {
                    if (ch == ':') {
                        Text(
                            text = ":",
                            style = clockStyle,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .graphicsLayer { alpha = if (reduceMotion) 1f else colon.value },
                        )
                    } else {
                        AnimatedContent(
                            targetState = ch,
                            transitionSpec = {
                                if (reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (
                                        slideInVertically(
                                            animationSpec = tween(560, easing = SoftOut),
                                            initialOffsetY = { it / 2 },
                                        ) + fadeIn(tween(420))
                                        ) togetherWith (
                                        slideOutVertically(
                                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                                            targetOffsetY = { -it / 2 },
                                        ) + fadeOut(tween(240))
                                        )
                                }
                            },
                            label = "clock-digit",
                        ) { c ->
                            Text(text = c.toString(), style = clockStyle)
                        }
                    }
                }
            }
            if (ampmText != null) {
                Text(
                    text = ampmText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 16.dp),
                )
            }
        }

        // ── Tanggal: berganti dengan crossfade lembut saat lewat tengah malam ──
        AnimatedContent(
            targetState = dateText,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                }
            },
            modifier = Modifier.staggerIn(enter, 0.20f, 0.75f, rise = 22.dp),
            label = "clock-date",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = if (isEnglish) "Welcome to AetherX" else "Selamat datang di AetherX",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.staggerIn(enter, 0.32f, 0.85f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isEnglish) {
                "Everything is set. Your dashboard is ready."
            } else {
                "Semua pengaturan selesai. Dashboard kamu sudah siap."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.staggerIn(enter, 0.40f, 0.92f),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Petunjuk geser. Bisa juga diketuk (aksesibilitas / TalkBack).
        val hintLabel = if (isEnglish) "Swipe up to open" else "Geser ke atas untuk membuka"
        Column(
            modifier = Modifier
                .staggerIn(enter, 0.55f, 1.00f, rise = 18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = hintLabel,
                    onClick = onHintClick,
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        translationY = -8.dp.toPx() * bob.value
                        alpha = 0.55f + 0.45f * bob.value
                    },
            )
            Text(
                text = hintLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        // Grabber ala home indicator iOS.
        Box(
            modifier = Modifier
                .staggerIn(enter, 0.60f, 1.00f, rise = 10.dp)
                .width(134.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
