package com.aether.x.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.BuildConfig
import com.aether.x.R
import com.aether.x.ui.components.AetherBackground
import kotlinx.coroutines.delay
import com.aether.x.core.locale.str

private const val MIN_SPLASH_MILLIS = 950L

/** Splash status is rendered from actual server/device/token operations reported by SplashViewModel. */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    var entered by remember { mutableStateOf(false) }

    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 260f,
        ),
        label = "splashEntrance",
    )

    val jellyTransition = rememberInfiniteTransition(label = "splash-jelly")
    val jelly by jellyTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashJelly",
    )

    LaunchedEffect(Unit) { entered = true }

    val startedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            val remaining = MIN_SPLASH_MILLIS - (System.currentTimeMillis() - startedAt)
            if (remaining > 0) delay(remaining)
            onSplashFinished()
        }
    }

    AetherBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 390.dp)
                    .alpha(entrance.coerceIn(0f, 1f))
                    .graphicsLayer {
                        val entryScale = 0.94f + entrance.coerceIn(0f, 1f) * 0.06f
                        scaleX = entryScale
                        scaleY = entryScale
                    }
                    // Clean splash: logo and status sit directly on the animated background.
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "AetherX",
                    colorFilter = ColorFilter.tint(colors.onSurface),
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            val pulse = jelly.coerceIn(0f, 1f)
                            val breathe = 0.975f + pulse * 0.05f
                            scaleX = breathe
                            scaleY = 1.025f - (breathe - 1f) * 0.65f
                            rotationZ = (pulse - 0.5f) * 1.4f
                        },
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.onSurface)) { append("AETHER") }
                        withStyle(SpanStyle(color = colors.primary)) { append("X") }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.4.sp,
                )

                Spacer(Modifier.height(7.dp))

                Text(
                    text = if (com.aether.x.core.locale.LocaleManager.current(androidx.compose.ui.platform.LocalContext.current) == com.aether.x.core.locale.AppLanguage.ENGLISH) {
                        "KERNEL CONTROL  ·  PERFORMANCE"
                    } else {
                        "KONTROL KERNEL  ·  PERFORMA"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(22.dp))

                // Keep the splash uncluttered: show only the live operation status.
                // Detailed registration/token stages remain tracked by SplashViewModel.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = colors.primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = uiState.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (uiState.failed) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = str(R.string.splash_verifikasi_perangkat_belum_berhasil),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = str(R.string.splash_periksa_koneksi_internet_lalu_coba),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::retry) {
                        Text(
                            text = str(R.string.splash_coba_lagi),
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                letterSpacing = 1.sp,
            )
        }
    }
}
