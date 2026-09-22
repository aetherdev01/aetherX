package com.aether.x.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.x.R
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing

/**
 * Splash yang benar-benar menjalankan urutan startup ke Firebase lewat
 * [SplashViewModel] (resolusi user ID, registrasi device, sync token FCM,
 * cek status maintenance) — progres & teks status di sini mencerminkan
 * tahap nyata yang sedang berjalan, bukan simulasi delay.
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
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "AetherX Logo",
                modifier = Modifier.size(110.dp),
            )

            Spacer(modifier = Modifier.height(56.dp))

            LinearProgressIndicator(
                progress = { progressAnim },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = AetherMonoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Gagal terhubung (mis. offline) — splash BERHENTI di sini dan
            // menampilkan tombol coba lagi, bukan diam-diam lanjut ke MAIN
            // tanpa user ID tersinkron.
            if (uiState.failed) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(onClick = { viewModel.retry() }) {
                    Text("Coba Lagi")
                }
            }
        }
    }
}
