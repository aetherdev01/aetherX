package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private const val STAGE_SETUP = 0     // halaman 1: pengaturan izin
private const val STAGE_WELCOME = 1   // pop-up sambutan (sheet bawah) di atas Dashboard
private const val STAGE_REVEALED = 2  // pop-up sudah ditutup, Dashboard aktif penuh

/**
 * Alur onboarding:
 *  1. [PermissionSetupScreen] — begitu semua pemeriksaan selesai, otomatis lanjut
 *     (tombol bawah juga lanjut untuk mode terbatas).
 *  2. [WelcomeSheet] — pop-up sambutan bergaya sheet iOS: kartu kaca mengambang di BAWAH layar,
 *     langsung naik begitu setup selesai. Dashboard di belakangnya mundur (mengecil + meredup)
 *     seperti presentasi sheet di iOS.
 *
 * Dashboard dikomposisi satu kali dan tidak dibuat ulang saat pop-up ditutup, jadi tidak ada
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

    // Dashboard direkam sebagai backdrop: kartu kaca membiaskannya lewat lens().
    val dashboardBackdrop = rememberLayerBackdrop()

    var stage by rememberSaveable { mutableIntStateOf(STAGE_SETUP) }
    // 0 = kartu tampil, 1 = kartu sudah turun keluar layar (ditutup).
    val lift = remember { Animatable(if (stage == STAGE_REVEALED) 1f else 0f) }
    // 0 = kartu belum muncul (halaman setup di depan), 1 = kartu sudah naik penuh.
    val reveal = remember { Animatable(if (stage == STAGE_SETUP) 0f else 1f) }
    var setupVisible by remember { mutableStateOf(stage == STAGE_SETUP) }

    // Guard supaya auto-advance dan tap tombol tidak berjalan dua kali.
    var advancing by remember { mutableIntStateOf(0) }
    val goToWelcome: () -> Unit = {
        if (stage == STAGE_SETUP && advancing == 0) {
            advancing = 1
            scope.launch {
                onSetupDone()
                stage = STAGE_WELCOME
                // Dashboard + kartu (backdrop, shader lensa) baru disusun & digambar pertama kali
                // di sini. Beri 2 frame agar pekerjaan berat itu selesai dulu, lalu kartu langsung
                // naik — kalau animasi mulai bersamaan, frame awalnya terlewat (ngelag).
                if (!reduceMotion) repeat(2) { withFrameNanos { } }
                // Spring ala iOS: naik cepat, mendarat lembut dengan sedikit settle.
                reveal.animateTo(
                    1f,
                    if (reduceMotion) tween(150) else spring(dampingRatio = 0.86f, stiffness = 320f),
                )
                setupVisible = false
            }
        }
    }

    // Seberapa jauh Dashboard "mundur" ke belakang: 0 = normal, 1 = mundur penuh (kartu tampil).
    val recede: () -> Float = {
        if (stage == STAGE_SETUP) 0f
        else reveal.value.coerceIn(0f, 1f) * (1f - lift.value.coerceIn(0f, 1f))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Lapis bawah: Dashboard (hidup) ──
        if (stage != STAGE_SETUP) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Direkam SETELAH peredupan & skala (modifier di sini berada di dalamnya),
                    // jadi isi di dalam kartu kaca selaras dengan yang terlihat di sekelilingnya.
                    .layerBackdrop(dashboardBackdrop)
                    .drawWithContent {
                        drawContent()
                        val r = recede()
                        if (r > 0.001f) drawRect(Color.Black.copy(alpha = 0.42f * r))
                    }
                    .graphicsLayer {
                        if (!reduceMotion) {
                            // Efek presentasi sheet iOS: Dashboard mengecil + sudut membulat.
                            val r = recede()
                            val s = 1f - 0.07f * r
                            scaleX = s
                            scaleY = s
                            translationY = 6.dp.toPx() * r
                            shape = RoundedCornerShape(26.dp * r)
                            clip = r > 0.001f
                        }
                    },
            ) {
                dashboard()
            }
        }

        // ── Lapis tengah: pop-up sambutan (sheet bawah) ──
        if (stage == STAGE_WELCOME) {
            WelcomeSheet(
                lift = lift,
                backdrop = dashboardBackdrop,
                isEnglish = isEnglish,
                onDismissed = { stage = STAGE_REVEALED },
                enterProgress = { reveal.value },
            )
        }

        // ── Lapis atas: halaman Setup — memudar cepat saat kartu mulai naik ──
        if (setupVisible) {
            val pageColor = LocalAetherPalette.current.bg
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = (reveal.value / 0.3f).coerceIn(0f, 1f)
                        alpha = 1f - f
                        if (!reduceMotion) {
                            val s = 1f + 0.04f * f // sedikit membesar sambil memudar
                            scaleX = s
                            scaleY = s
                        }
                    }
                    // Halaman harus opak selama masih di depan Dashboard.
                    .background(pageColor),
            ) {
                PermissionSetupScreen(
                    onContinue = goToWelcome,
                    requireAccessToContinue = true,
                    onAllComplete = goToWelcome,
                )
            }
        }
    }
}
