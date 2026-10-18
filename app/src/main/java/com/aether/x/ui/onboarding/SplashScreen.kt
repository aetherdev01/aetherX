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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.components.AetherBackground
import kotlinx.coroutines.delay

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
                    .alpha(entrance.coerceIn(0f, 1f))
                    .graphicsLayer {
                        val entryScale = 0.94f + entrance.coerceIn(0f, 1f) * 0.06f
                        scaleX = entryScale
                        scaleY = entryScale
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "AetherX",
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
                        withStyle(SpanStyle(color = colors.onBackground)) { append("AETHER") }
                        withStyle(SpanStyle(color = colors.primary)) { append("X") }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.4.sp,
                )

                Spacer(Modifier.height(20.dp))

                if (uiState.steps.isNotEmpty()) {
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

                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = uiState.progress,
                        modifier = Modifier.width(260.dp),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        uiState.steps.forEach { step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier.size(width = 22.dp, height = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (step.status) {
                                        SplashStepStatus.ACTIVE -> CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            color = colors.primary,
                                            strokeWidth = 2.dp,
                                        )
                                        SplashStepStatus.COMPLETE -> Text(
                                            text = "✓",
                                            color = colors.primary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        SplashStepStatus.SKIPPED -> Text(
                                            text = "—",
                                            color = colors.onSurfaceVariant,
                                            fontSize = 12.sp,
                                        )
                                        SplashStepStatus.FAILED -> Text(
                                            text = "!",
                                            color = colors.error,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        SplashStepStatus.PENDING -> Text(
                                            text = "•",
                                            color = colors.outline,
                                            fontSize = 16.sp,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (step.status) {
                                            SplashStepStatus.ACTIVE -> colors.onBackground
                                            SplashStepStatus.FAILED -> colors.error
                                            SplashStepStatus.PENDING -> colors.onSurfaceVariant
                                            else -> colors.onSurfaceVariant
                                        },
                                        fontWeight = if (step.status == SplashStepStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (step.status == SplashStepStatus.ACTIVE || step.status == SplashStepStatus.FAILED) {
                                        Text(
                                            text = step.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (step.status == SplashStepStatus.FAILED) colors.error else colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.failed) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Verifikasi perangkat belum berhasil",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Periksa koneksi internet lalu coba lagi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::retry) {
                        Text(
                            text = "Coba lagi",
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
