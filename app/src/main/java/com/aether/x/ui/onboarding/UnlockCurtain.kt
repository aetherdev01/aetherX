package com.aether.x.ui.onboarding

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Halaman ke-2 onboarding: "layer" bergaya lockscreen / panel notifikasi iPhone.
 *
 * Layer menutupi Dashboard (yang sudah tampil hidup di belakangnya). User menggesernya
 * ke atas — layer mengikuti jari, tepinya berpendar RGB-split ala lensa kaca
 * (chromatic aberration) yang makin kuat saat ditarik, lalu terlepas dan menyingkap
 * Dashboard. Lepas sebelum batas → layer memantul balik (spring).
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

    // Denyut aberrasi sesaat saat layer pertama kali muncul (lensa "fokus").
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        pulse.snapTo(1f)
        delay(260)
        pulse.animateTo(0f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
    }

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

    val unlock: () -> Unit = {
        if (!unlocking) {
            unlocking = true
            scope.launch {
                haptic.performIfEnabled(hapticsEnabled, HapticFeedbackType.LongPress)
                lift.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (reduceMotion) 120 else 420,
                        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                    ),
                )
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
                        unlock()
                    } else {
                        scope.launch {
                            lift.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = Spring.StiffnessLow,
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
                .graphicsLayer { translationY = -lift.value * size.height }
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
                        // Cahaya aksen lembut di belakang jam.
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    palette.accent.copy(alpha = 0.24f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height * 0.34f),
                                radius = size.width * 0.85f,
                            ),
                            radius = size.width * 0.85f,
                            center = Offset(size.width / 2f, size.height * 0.34f),
                        )
                    },
            ) {
                CurtainContent(
                    lift = lift,
                    isEnglish = isEnglish,
                    onHintClick = unlock,
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

@Composable
private fun CurtainContent(
    lift: Animatable<Float, AnimationVector1D>,
    isEnglish: Boolean,
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
    val dateText = remember(now, locale) {
        SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now))
    }

    val bob = rememberInfiniteTransition(label = "curtain-hint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "curtain-hint-bob",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (1f - lift.value * 2.4f).coerceIn(0f, 1f) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        // Pil status teratas.
        Row(
            modifier = Modifier
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

        Text(
            text = timeText,
            fontSize = 86.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp,
            color = colors.onSurface,
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = if (isEnglish) "Welcome to AetherX" else "Selamat datang di AetherX",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
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
        )

        Spacer(modifier = Modifier.weight(1f))

        // Petunjuk geser. Bisa juga diketuk (aksesibilitas / TalkBack).
        val hintLabel = if (isEnglish) "Swipe up to open" else "Geser ke atas untuk membuka"
        Column(
            modifier = Modifier
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
                    .graphicsLayer { translationY = -8.dp.toPx() * bob.value },
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
                .width(134.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
