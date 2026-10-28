package com.aether.x.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.theme.LocalAetherPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos

/**
 * Arah buka "buku": -1 = sisi bawah halaman terangkat ke ARAH PENONTON lalu naik,
 * +1 = sebaliknya. Kalau di device halaman terlihat terbuka ke arah yang salah, balik tanda ini.
 */
private const val BOOK_FLIP_DIRECTION = -1f
/** Lama halaman terbuka. Kurva iOS (cepat di awal, mendarat lembut). */
private const val BOOK_FLIP_MS = 950
private val BookEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Transformasi buku yang SAMA untuk halaman dan lapisan bayangannya (supaya selalu sejajar). */
private fun GraphicsLayerScope.bookFlip(e: Float, density: Float) {
    transformOrigin = TransformOrigin(0.5f, 0f) // engsel di tepi atas
    cameraDistance = 14f * density
    rotationX = BOOK_FLIP_DIRECTION * 90f * e
}

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
                // Dashboard + curtain (backdrop, shader lensa) baru disusun & digambar pertama kali
                // di sini — pekerjaan berat di main thread. Kalau animasi dimulai bersamaan, frame
                // awalnya terlewat dan terasa ngelag. Biarkan beberapa frame lewat dulu, baru buka.
                if (!reduceMotion) {
                    repeat(3) { withFrameNanos { } }
                    delay(90)
                }
                // Halaman setup terbuka seperti sampul buku: engsel di sisi ATAS, sisi bawah
                // terangkat dari bawah ke atas hingga sejajar pandangan (tegak lurus), lalu hilang.
                reveal.animateTo(
                    1f,
                    if (reduceMotion) tween(150) else tween(BOOK_FLIP_MS, easing = BookEasing),
                )
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
                            // Dashboard tetap tajam & terlihat menembus kaca layer (seperti drawer Tweak).
                            // smoothstep: zoom berubah mulus di awal dan akhir tarikan.
                            val raw = lift.value.coerceIn(0f, 1f)
                            val p = raw * raw * (3f - 2f * raw)
                            val s = 0.95f + 0.05f * p
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

        // ── Bayangan yang jatuh ke layer di bawah, mengikuti tepi bawah halaman yang terangkat ──
        if (setupVisible && !reduceMotion) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val e = reveal.value.coerceIn(0f, 1f)
                        if (e <= 0.001f) return@drawBehind
                        val edge = size.height * cos(Math.toRadians(90.0 * e)).toFloat()
                        val reach = 120.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.50f * (1f - e)), Color.Transparent),
                                startY = edge,
                                endY = edge + reach,
                            ),
                            topLeft = Offset(0f, edge),
                            size = Size(size.width, reach),
                        )
                    },
            )
        }

        // ── Lapis atas: halaman Setup — terbuka seperti buku dari bawah ke atas ──
        if (setupVisible) {
            val density = LocalDensity.current.density
            val pageColor = LocalAetherPalette.current.bg
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = reveal.value.coerceIn(0f, 1f)
                        if (reduceMotion) {
                            alpha = 1f - e
                        } else {
                            bookFlip(e, density)
                            // Halaman dirender SEKALI ke tekstur lalu hanya diputar GPU. Tanpa ini
                            // kartu blur/haze di halaman setup digambar ulang tiap frame saat diputar.
                            compositingStrategy = CompositingStrategy.Offscreen
                            // Di 90° halaman tinggal garis tipis; sembunyikan sisa-sisanya.
                            alpha = if (e >= 0.995f) 0f else 1f
                        }
                    }
                    // Halaman harus opak supaya terlihat seperti kertas, bukan kaca.
                    .background(pageColor),
            ) {
                PermissionSetupScreen(
                    onContinue = goToCurtain,
                    requireAccessToContinue = true,
                    onAllComplete = goToCurtain,
                )
            }
            if (!reduceMotion) {
                // Peredupan halaman di lapisan TERPISAH (hitam statis, hanya alpha yang berubah),
                // supaya tekstur halaman di atas tidak perlu dirender ulang tiap frame.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val e = reveal.value.coerceIn(0f, 1f)
                            bookFlip(e, density)
                            alpha = if (e >= 0.995f) 0f else 0.55f * e * e
                        }
                        .background(Color.Black),
                )
            }
        }
    }
}
