package com.aether.x.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.LocalReduceMotionEnabled
import kotlinx.coroutines.launch

private const val STAGE_SETUP = 0     // halaman 1: pengaturan izin
private const val STAGE_CURTAIN = 1   // halaman 2: layer geser-ke-atas, Dashboard di belakangnya
private const val STAGE_REVEALED = 2  // layer sudah terbuka, Dashboard aktif penuh

/**
 * Alur onboarding 2 halaman:
 *  1. [PermissionSetupScreen] — begitu semua pemeriksaan selesai, otomatis pindah ke halaman 2
 *     (tombol bawah juga membawa ke halaman 2 untuk mode terbatas).
 *  2. [UnlockCurtain] — layer bergaya panel/lockscreen iPhone dengan efek lensa chromatic
 *     aberration. Dashboard sudah tampil (blur) di belakangnya; geser layer ke atas untuk
 *     menyingkapnya.
 *
 * Dashboard dikomposisi satu kali dan tidak dibuat ulang saat layer terbuka, jadi tidak ada
 * kedipan / reset state. Stage disimpan lewat rememberSaveable (aman untuk rotasi, bahasa,
 * dan kembali dari MANAGE_ACCESS).
 *
 * @param onSetupDone dipanggil (dan ditunggu) SEBELUM Dashboard dikomposisi — dipakai untuk
 *   menyimpan onboardingCompleted & versi changelog agar dialog "Apa yang Baru" tidak muncul
 *   di instalasi baru.
 * @param dashboard konten Dashboard (MainScreen).
 */
@Composable
fun OnboardingFlow(
    onSetupDone: suspend () -> Unit,
    dashboard: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isEnglish = remember(context) { LocaleManager.current(context) == AppLanguage.ENGLISH }
    val reduceMotion = LocalReduceMotionEnabled.current
    val scope = rememberCoroutineScope()

    var stage by rememberSaveable { mutableIntStateOf(STAGE_SETUP) }
    // 0 = layer menutup penuh, 1 = layer sudah keluar layar.
    val lift = remember { Animatable(if (stage == STAGE_REVEALED) 1f else 0f) }

    // Guard supaya auto-advance dan tap tombol tidak berjalan dua kali.
    var advancing by remember { mutableIntStateOf(0) }
    val goToCurtain: () -> Unit = {
        if (stage == STAGE_SETUP && advancing == 0) {
            advancing = 1
            scope.launch {
                onSetupDone()
                stage = STAGE_CURTAIN
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Lapis bawah: Dashboard (hidup, tapi diburamkan selama layer menutup) ──
        if (stage != STAGE_SETUP) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (!reduceMotion) {
                            val p = lift.value.coerceIn(0f, 1f)
                            val radius = 22.dp.toPx() * (1f - p)
                            renderEffect =
                                if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                            val s = 0.93f + 0.07f * p
                            scaleX = s
                            scaleY = s
                        }
                    },
            ) {
                dashboard()
            }
        }

        // ── Lapis tengah: layer geser-ke-atas ──
        if (stage == STAGE_CURTAIN) {
            UnlockCurtain(
                lift = lift,
                isEnglish = isEnglish,
                onUnlocked = { stage = STAGE_REVEALED },
            )
        }

        // ── Lapis atas: halaman Setup; keluar ke kiri saat pindah ke halaman 2 ──
        AnimatedVisibility(
            visible = stage == STAGE_SETUP,
            enter = EnterTransition.None,
            exit = if (reduceMotion) {
                fadeOut(tween(150))
            } else {
                slideOutHorizontally(tween(480, easing = FastOutSlowInEasing)) { -it } +
                    fadeOut(tween(480))
            },
        ) {
            PermissionSetupScreen(
                onContinue = goToCurtain,
                requireAccessToContinue = true,
                onAllComplete = goToCurtain,
            )
        }
    }
}
