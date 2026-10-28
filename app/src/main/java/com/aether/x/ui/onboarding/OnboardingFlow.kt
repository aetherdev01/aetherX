package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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

    // Dashboard direkam sebagai backdrop: layer curtain membiaskannya lewat lens() seperti navbar.
    val dashboardBackdrop = rememberLayerBackdrop()

    var stage by rememberSaveable { mutableIntStateOf(STAGE_SETUP) }
    // 0 = layer menutup penuh, 1 = layer sudah keluar layar.
    val lift = remember { Animatable(if (stage == STAGE_REVEALED) 1f else 0f) }

    // 0 = halaman setup di depan, 1 = setup sudah mundur ke belakang & layer sudah maju.
    val reveal = remember { Animatable(if (stage == STAGE_SETUP) 0f else 1f) }
    var setupVisible by remember { mutableStateOf(stage == STAGE_SETUP) }

    // Guard supaya auto-advance dan tap tombol tidak berjalan dua kali.
    var advancing by remember { mutableIntStateOf(0) }
    val goToCurtain: () -> Unit = {
        if (stage == STAGE_SETUP && advancing == 0) {
            advancing = 1
            scope.launch {
                onSetupDone()
                stage = STAGE_CURTAIN
                // Transisi ala iOS: setup mundur (mengecil, redup) sambil layer maju dari depan.
                // Spring sedikit memantul di ujung supaya terasa hidup, bukan easing kaku.
                if (reduceMotion) {
                    reveal.animateTo(1f, tween(150))
                } else {
                    reveal.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 180f))
                }
                setupVisible = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Lapis bawah: Dashboard (hidup; tertutup kaca frosted selama layer menutup) ──
        if (stage != STAGE_SETUP) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Di LUAR graphicsLayer: yang direkam sudah termasuk blur + skala Dashboard,
                    // jadi isi di dalam kaca selaras dengan yang terlihat di bawah tepinya.
                    .layerBackdrop(dashboardBackdrop)
                    .graphicsLayer {
                        if (!reduceMotion) {
                            // Dashboard tetap tajam: yang memburamkan adalah kaca layer (frosted,
                            // seperti app drawer) — jadi hanya area di bawah layer yang buram.
                            // smoothstep: zoom berubah mulus di awal dan akhir tarikan.
                            val raw = lift.value.coerceIn(0f, 1f)
                            val p = raw * raw * (3f - 2f * raw)
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
                backdrop = dashboardBackdrop,
                isEnglish = isEnglish,
                onUnlocked = { stage = STAGE_REVEALED },
                enterProgress = { reveal.value },
            )
        }

        // ── Lapis atas: halaman Setup — kartu yang mundur ke belakang saat pindah ke halaman 2 ──
        if (setupVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = reveal.value.coerceIn(0f, 1f)
                        if (reduceMotion) {
                            alpha = 1f - e
                        } else {
                            val sc = 1f - 0.10f * e
                            scaleX = sc
                            scaleY = sc
                            // Naik sedikit + sudut membulat: kartu "terangkat" lalu masuk ke kedalaman.
                            translationY = -24.dp.toPx() * e
                            shape = RoundedCornerShape(36.dp * e)
                            clip = e > 0.001f
                            // Tetap opak di awal supaya tidak terlihat tembus, lalu memudar.
                            alpha = 1f - ((e - 0.15f) / 0.6f).coerceIn(0f, 1f)
                        }
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
}
