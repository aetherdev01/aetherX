package com.aether.x.ui.booster

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aether.x.R
import com.aether.x.core.apps.GameLaunchTracker
import com.aether.x.core.overlay.GameBoosterOverlayService
import com.aether.x.ui.theme.AetherXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash singkat Game Booster (lihat perintah rework: "saat buka gamenya
 * ada animasi splash dari game boosternya") — activity transparan (tema
 * `Theme.AetherX.Transparent`, lihat AndroidManifest) yang menampilkan
 * animasi logo AetherX (scale-in + fade-in, MENIRU semangat prompt animasi
 * lightning-strike yang sebelumnya dibuat untuk intro logo AetherX) selama
 * kurang lebih 1.4 detik, lalu OTOMATIS:
 * 1. Membuka game lewat [GameLaunchTracker] (tercatat sebagai "terakhir
 *    dipakai" sekaligus, konsisten dengan Dashboard "Aktivitas Game").
 * 2. Memulai [GameBoosterOverlayService] untuk package itu (floating
 *    sidebar akan muncul begitu game selesai loading dan splash ini
 *    ditutup sendiri lewat [finish]).
 *
 * TIDAK menunggu konfirmasi apa pun dari pengguna — activity ini SEPENUHNYA
 * otomatis, murni for-show sebelum transisi ke game.
 */
class GameBoosterSplashActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_GAME_LABEL = "extra_game_label"

        fun launch(context: Context, packageName: String, gameLabel: String) {
            val intent = Intent(context, GameBoosterSplashActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_GAME_LABEL, gameLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val gameLabel = intent.getStringExtra(EXTRA_GAME_LABEL) ?: packageName.orEmpty()

        if (packageName == null) {
            finish()
            return
        }

        setContent {
            AetherXTheme {
                GameBoosterSplashContent(
                    onSplashFinished = {
                        lifecycleScope.launch {
                            GameLaunchTracker.launchAndTrack(this@GameBoosterSplashActivity, packageName)
                            GameBoosterOverlayService.start(this@GameBoosterSplashActivity, packageName, gameLabel)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GameBoosterSplashContent(onSplashFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "splash_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splash_alpha",
    )

    LaunchedEffect(Unit) {
        started = true
        delay(1400L)
        onSplashFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.ic_aetherx_logo),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .alpha(alpha),
        )
        Text(
            text = stringResource(R.string.game_booster_splash_preparing),
            modifier = Modifier
                .alpha(alpha)
                .align(Alignment.BottomCenter),
        )
    }
}
