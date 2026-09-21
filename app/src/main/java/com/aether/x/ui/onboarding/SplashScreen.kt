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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // State untuk progres dan teks status
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentStatusText by remember { mutableStateOf("Menyiapkan antarmuka...") }

    // Animasi masuk (Scale & Alpha)
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "splashScale"
    )

    // Animasi halus untuk pergerakan bar progres
    val progressAnim by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(durationMillis = 400),
        label = "progressBarAnim"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        
        // Daftar simulasi proses (Target Progres, Teks Status)
        val loadingSteps = listOf(
            0.15f to "Menginisialisasi core system...",
            0.35f to "Mengecek environment root...",
            0.50f to "Menghubungkan ke server daemon...",
            0.75f to "Memverifikasi integritas data...",
            0.90f to "Memuat konfigurasi engine...",
            1.00f to "Selesai."
        )

        // Iterasi setiap langkah dengan jeda waktu yang agak acak agar terasa natural
        for (step in loadingSteps) {
            // Beri jeda acak antara 300ms hingga 600ms per step
            delay((300..600).random().toLong())
            currentProgress = step.first
            currentStatusText = step.second
        }

        // Tahan sebentar di progres 100% sebelum pindah layar
        delay(300L)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp) // Membatasi lebar elemen agar rapi
                .alpha(alphaAnim)
                .scale(scaleAnim)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo), 
                contentDescription = "AetherX Logo",
                modifier = Modifier.size(110.dp)
            )
            
            Spacer(modifier = Modifier.height(56.dp))
            
            // Bar Progres Linier
            LinearProgressIndicator(
                progress = { progressAnim },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)), // Menggunakan bentuk pil membulat
                color = MaterialTheme.colorScheme.primary, // Akan mengambil AetherCyan
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            
            Spacer(modifier = Modifier.height(Spacing.md))
            
            // Teks Indikator Status
            Text(
                text = currentStatusText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = AetherMonoFamily, // Memberikan kesan terminal/syslog
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
