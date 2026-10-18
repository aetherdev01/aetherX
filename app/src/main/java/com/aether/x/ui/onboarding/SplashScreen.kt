package com.aether.x.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val MIN_SPLASH_MILLIS = 900L

/**
 * Splash minimalis dan mengikuti tema aktif. Hanya menampilkan status startup
 * yang sedang berjalan, bukan menumpuk daftar log dari tahapan sebelumnya.
 * Progress berasal dari panggilan Firebase nyata di [SplashViewModel].
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    val progress by animateFloatAsState(
        targetValue = uiState.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "splashProgress",
    )
    var started by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "splashReveal",
    )
    LaunchedEffect(Unit) { started = true }

    val startedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            val remaining = MIN_SPLASH_MILLIS - (System.currentTimeMillis() - startedAt)
            if (remaining > 0) delay(remaining)
            onSplashFinished()
        }
    }

    val statusColor = when {
        uiState.failed -> colors.error
        uiState.finished -> Color(0xFF2EAD69)
        else -> colors.primary
    }
    val statusText = when {
        uiState.failed -> "Koneksi gagal — periksa internet"
        uiState.finished -> "Terhubung ke server"
        else -> uiState.statusText.ifBlank { "Menghubungkan ke server..." }
    }
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl)
                .graphicsLayer {
                    alpha = reveal
                    val scale = 0.97f + (reveal * 0.03f)
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceContainerHigh)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.72f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "AetherX",
                    modifier = Modifier.size(66.dp),
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onBackground)) { append("AETHER") }
                    withStyle(SpanStyle(color = colors.primary)) { append("X") }
                },
                fontFamily = AetherMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "ROOT  /  KERNEL  /  PERFORMANCE",
                fontFamily = AetherMonoFamily,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xxl))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), shape)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "PROGRES REAL-TIME KE SERVER",
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        modifier = Modifier.padding(start = Spacing.sm),
                        fontFamily = AetherMonoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = statusText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (uiState.failed) {
                Spacer(Modifier.height(Spacing.lg))
                OutlinedButton(
                    onClick = viewModel::retry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                ) {
                    Text("Coba lagi", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            fontFamily = AetherMonoFamily,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xxl),
        )
    }
}
