package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val SHEET_CORNER_DP = 38f
/** Jarak kartu dari tepi layar kiri/kanan/bawah — kartu MENGAMBANG, seperti sheet iOS terbaru. */
private const val SHEET_MARGIN_DP = 12f
/** Jarak tambahan saat menyingkirkan kartu ke bawah layar (inset navigasi + bayangan). */
private const val SHEET_EXIT_EXTRA_DP = 140f
/** Lensa: kekuatan TETAP (tidak berubah saat digeser) supaya tepi tidak bergetar. */
private const val LENS_HEIGHT_DP = 22f
private const val LENS_AMOUNT_DP = 44f
private const val RUBBER_BAND = 0.05f

private fun rubberBand(x: Float, limit: Float): Float =
    if (limit <= 0f || x <= 0f) 0f else (1f - 1f / (x * 0.55f / limit + 1f)) * limit

/**
 * Pop-up sambutan bergaya iOS: kartu kaca MENGAMBANG di BAWAH layar (di iOS disebut "sheet" /
 * "bottom sheet"; versi yang mengambang dengan margin dan sudut membulat penuh adalah gaya
 * sheet iOS terbaru). Muncul naik dari bawah dengan spring, Dashboard di belakangnya mundur
 * (mengecil + meredup), dan bisa ditutup dengan tombol atau digeser ke bawah.
 *
 * @param lift 0f = kartu tampil, 1f = kartu sudah turun keluar layar (sedikit negatif saat
 *   ditarik ke atas: rubber band).
 * @param enterProgress 0 → 1 saat kartu naik masuk (dibaca di fase draw).
 */
@Composable
fun WelcomeSheet(
    lift: Animatable<Float, AnimationVector1D>,
    backdrop: Backdrop,
    isEnglish: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    enterProgress: () -> Float = { 1f },
) {
    val palette = LocalAetherPalette.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticEnabled.current
    val reduceMotion = LocalReduceMotionEnabled.current
    val style = LocalAetherStyle.current
    val status by PrivilegeManager.status.collectAsStateWithLifecycle()

    val accent = palette.accent
    val accent2 = remember(accent) { accent.shiftHueSheet(42f) }
    // Tint tipis seperti drawer, tapi dijaga >= 0.30 supaya teks tetap terbaca di atas Dashboard.
    val glassTint = (liquidGlassTintAlpha(style.glassLevel.coerceIn(0f, 1f)) * 0.62f).coerceIn(0.30f, 0.94f)

    val cornerPx = with(density) { SHEET_CORNER_DP.dp.toPx() }
    val exitExtraPx = with(density) { SHEET_EXIT_EXTRA_DP.dp.toPx() }

    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    var rawLift by remember { mutableFloatStateOf(0f) }

    val dismiss: (Float) -> Unit = { liftVel ->
        if (!dismissing) {
            dismissing = true
            scope.launch {
                haptic.performIfEnabled(hapticsEnabled, HapticFeedbackType.LongPress)
                val spec: AnimationSpec<Float> =
                    if (reduceMotion) tween(150) else spring(dampingRatio = 1f, stiffness = 300f)
                lift.animateTo(1f, spec, initialVelocity = liftVel.coerceIn(0f, 10f))
                onDismissed()
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (!dismissing) {
            val travel = (sheetHeightPx + exitExtraPx).coerceAtLeast(1f)
            rawLift = (rawLift + delta / travel).coerceIn(-1f, 1f)
            val target = if (rawLift >= 0f) rawLift else -rubberBand(-rawLift, RUBBER_BAND)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { lift.snapTo(target) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Penahan sentuhan: Dashboard di belakang tidak bisa disentuh selama pop-up tampil.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(SHEET_MARGIN_DP.dp)
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(cornerPx) },
                    effects = {
                        // Lensa murni di seluruh tepi kartu — tanpa blur, tanpa dispersi warna.
                        lens(LENS_HEIGHT_DP.dp.toPx(), LENS_AMOUNT_DP.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(width = 1.2.dp, alpha = 0.7f) },
                    shadow = { Shadow(alpha = 0.35f) },
                    layerBlock = {
                        // Naik dari bawah saat muncul (enter), turun saat ditutup / digeser (lift).
                        val travel = size.height + exitExtraPx
                        translationY = ((1f - enterProgress()) + lift.value) * travel
                    },
                    onDrawSurface = {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(palette.surface, accent, 0.10f).copy(alpha = glassTint),
                                    palette.surface.copy(alpha = glassTint * 0.88f),
                                ),
                            ),
                        )
                    },
                )
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = !dismissing,
                    onDragStarted = { rawLift = lift.value.coerceAtLeast(0f) },
                    onDragStopped = { velocity ->
                        val travel = (sheetHeightPx + exitExtraPx).coerceAtLeast(1f)
                        val liftVel = velocity / travel // + = kartu turun
                        // Proyeksi ~180 ms ke depan: flick ke bawah menutup, tarikan pendek kembali.
                        val projected = lift.value + liftVel * 0.18f
                        if (projected > 0.35f) {
                            dismiss(liftVel)
                        } else {
                            scope.launch {
                                lift.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
                                    initialVelocity = liftVel.coerceIn(-10f, 10f),
                                )
                            }
                        }
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grabber ala sheet iOS.
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .background(colors.onSurface.copy(alpha = 0.35f), CircleShape),
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Pil status
                Row(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, accent.copy(alpha = 0.30f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (status.rootGranted) palette.success else colors.onSurfaceVariant,
                                CircleShape,
                            ),
                    )
                    Text(
                        text = "AETHERX",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp,
                        color = colors.onSurface,
                    )
                    Text(
                        text = if (status.rootGranted) {
                            if (isEnglish) "ACCESS READY" else "AKSES SIAP"
                        } else {
                            if (isEnglish) "LIMITED MODE" else "MODE TERBATAS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (isEnglish) "Welcome to AetherX" else "Selamat datang di AetherX",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) {
                        "Everything is set. Your dashboard is ready."
                    } else {
                        "Semua pengaturan selesai. Dashboard kamu sudah siap."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Tombol utama
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(Brush.horizontalGradient(listOf(accent, accent2)), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dismiss(0f) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isEnglish) "Get started" else "Mulai",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** Menggeser hue warna aksen untuk ujung gradien tombol. */
private fun Color.shiftHueSheet(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    hsv[1] = hsv[1].coerceAtLeast(0.35f)
    hsv[2] = hsv[2].coerceAtLeast(0.7f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = this.alpha)
}
