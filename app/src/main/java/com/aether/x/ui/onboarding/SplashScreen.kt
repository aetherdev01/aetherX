package com.aether.x.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.theme.AetherCyan
import com.aether.x.ui.theme.AetherCyanSoft
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted

/**
 * Splash yang benar-benar menjalankan urutan startup ke Firebase lewat
 * [SplashViewModel] (resolusi user ID, registrasi device, sync token FCM,
 * cek status maintenance) — progres & teks status di sini mencerminkan
 * tahap nyata yang sedang berjalan, bukan simulasi delay.
 *
 * Visualnya mengikuti identitas "Aether Cyan" (lihat Color.kt): glow +
 * pulse ring di belakang logo dan progress bar custom bergradasi/berpendar,
 * dipakai supaya splash terasa seperti "sinyal menyala", bukan layar polos.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = viewModel(),
) {
    var startAnimation by remember { mutableStateOf(false) }
    val uiState by viewModel.state.collectAsState()

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha",
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "splashScale",
    )
    val progressAnim by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = tween(durationMillis = 400),
        label = "progressBarAnim",
    )

    // Ambient pulse di belakang logo — dua ring yang mengembang & memudar
    // bergantian (offset 1100ms), plus "napas" pelan pada glow inti, biar
    // splash terasa hidup selagi startup nyata berjalan di background.
    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val ring1 by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val ring2 by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1100, StartOffsetType.FastForward),
        ),
        label = "ring2",
    )
    val breathe by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val cursorAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorBlink",
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // Baru pindah layar setelah SEMUA tahap startup nyata selesai
    // (bukan setelah delay tetap habis).
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onSplashFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .alpha(alphaAnim)
                .scale(scaleAnim),
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Glow inti yang "bernapas" pelan.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AetherCyan.copy(alpha = 0.22f * breathe),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = maxRadius,
                        ),
                        radius = maxRadius,
                        center = center,
                    )

                    // Dua ring sinyal yang mengembang & memudar keluar.
                    listOf(ring1, ring2).forEach { progress ->
                        val radius = maxRadius * 0.42f + (maxRadius * 0.5f) * progress
                        drawCircle(
                            color = AetherCyan.copy(alpha = (1f - progress) * 0.35f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "AetherX Logo",
                    modifier = Modifier.size(110.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            ) {
                val trackHeight = size.height
                val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                drawRoundRect(
                    color = StrokeSubtle,
                    cornerRadius = cornerRadius,
                )

                val fillWidth = size.width * progressAnim.coerceIn(0f, 1f)
                if (fillWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(AetherCyan.copy(alpha = 0.75f), AetherCyan, AetherCyanSoft),
                            endX = fillWidth,
                        ),
                        size = Size(fillWidth, trackHeight),
                        cornerRadius = cornerRadius,
                    )

                    // Titik pendar di ujung depan bar, kesan "energi mengalir".
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AetherCyanSoft.copy(alpha = 0.85f), Color.Transparent),
                            center = Offset(fillWidth, trackHeight / 2f),
                            radius = trackHeight * 2.4f,
                        ),
                        radius = trackHeight * 2.4f,
                        center = Offset(fillWidth, trackHeight / 2f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.statusText,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    fontFamily = AetherMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!uiState.finished && !uiState.failed) {
                    Text(
                        text = "_",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = AetherMonoFamily,
                        color = AetherCyan,
                        modifier = Modifier.alpha(cursorAlpha),
                    )
                }
            }

            // Gagal terhubung (mis. offline) — splash BERHENTI di sini dan
            // menampilkan tombol coba lagi, bukan diam-diam lanjut ke MAIN
            // tanpa user ID tersinkron.
            if (uiState.failed) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                OutlinedButton(
                    onClick = { viewModel.retry() },
                    border = BorderStroke(1.dp, AetherCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AetherCyan),
                ) {
                    Text("Coba Lagi")
                }
            }
        }

        Text(
            text = "AetherX ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            fontFamily = AetherMonoFamily,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xxl)
                .alpha(alphaAnim),
        )
    }
}
