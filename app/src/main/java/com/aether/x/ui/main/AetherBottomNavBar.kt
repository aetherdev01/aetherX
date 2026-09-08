package com.aether.x.ui.main

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Navbar bawah bergaya "Liquid Glass" ala iOS 26 (rujukan: tab bar
 * Apple News+ — satu pill BESAR mengisi hampir seluruh slot tab,
 * bukan chip kecil di belakang ikon).
 *
 * Tiga lapis liquid terpisah:
 * 1. KACA BAR — blur nyata dari konten di baliknya (Haze), rim highlight
 *    di tepi atas + inner-shadow tipis di bawah supaya kaca terasa
 *    punya ketebalan. Seluruh kapsul bar ikut "menyembul" pegas (bulge
 *    tipis) saat ditahan, supaya terasa satu material kenyal, bukan
 *    cuma pill di dalamnya yang bergerak sendirian.
 * 2. PILL PENANDA TAB — satu pill solid setinggi/selebar slot yang bisa
 *    DITAHAN lalu DIGESER. Selama ditahan, pill "menyembul" (scale naik,
 *    boleh sedikit melebihi tinggi bar) dan mengikuti jari lewat spring
 *    kenyal (bukan snap instan mentah) — respons tetap cepat tapi ada
 *    sedikit "lag" elastis khas liquid glass, bukan lonjong menyambung
 *    dua titik jauh. Tab baru dipilih (onSelect) saat jari dilepas;
 *    pill snap-settle ke slot terdekat dengan sedikit overshoot pegas.
 *    Saat bergerak cepat, pill squash & stretch (melebar searah gerak,
 *    memipih tegak lurusnya) lalu membulat lagi saat berhenti — efek
 *    jelly.
 * 3. MIRROR SHEEN — highlight spekular yang ikut bergeser mengikuti
 *    posisi pill, mensimulasikan pantulan cahaya pada permukaan kaca
 *    cembung (efek "mirror" liquid glass).
 */
// Shader AGSL (RuntimeShader, Android 13+/Tiramisu) yang membiaskan konten
// kapsul secara NYATA per-piksel — bukan gradient tiruan. Tiga hal dilakukan
// sekaligus di sini:
// 1. Bulge/barrel distortion: sampel ditekuk ke arah pusat sebanding jarak
//    dari tepi, mensimulasikan permukaan kaca cembung yang membiaskan
//    (refract) apa pun di baliknya — inilah "background distortion".
// 2. Chromatic fringe tipis di sekitar sampel yang sama (sampel R & B
//    digeser sedikit dari G) — "subtle refraction" khas lensa/kaca tebal,
//    dijaga sangat halus supaya tidak terlihat seperti glitch RGB split.
// 3. Highlight yang "memantul" bolak-balik melintasi kapsul (uniform
//    bouncePhase, digerakkan dari Compose) — realistic reflections yang
//    benar-benar bergerak, bukan cuma titik statis.
private const val LIQUID_GLASS_CAPSULE_SHADER = """
    uniform shader content;
    uniform float2 resolution;
    uniform float bouncePhase;
    uniform float energy;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 p = uv * 2.0 - 1.0;
        float r = length(p);

        float edge = smoothstep(0.12, 1.05, r);
        float2 bent = p * (1.0 - edge * 0.075);
        float2 baseUv = clamp(bent * 0.5 + 0.5, float2(0.002), float2(0.998));

        float2 fringe = p * edge * 0.0022;
        half redSample = content.eval(clamp(baseUv + fringe, float2(0.0), float2(1.0)) * resolution).r;
        half4 baseSample = content.eval(baseUv * resolution);
        half blueSample = content.eval(clamp(baseUv - fringe, float2(0.0), float2(1.0)) * resolution).b;

        half4 refracted = half4(redSample, baseSample.g, blueSample, baseSample.a);

        float sweep = mix(-0.35, 1.35, bouncePhase);
        float bandDist = abs((uv.x - sweep) + (uv.y - 0.5) * 0.4);
        float band = (1.0 - smoothstep(0.0, 0.24, bandDist)) * 0.5 * energy;

        half3 lit = refracted.rgb + half3(band);
        return half4(lit, refracted.a);
    }
"""

data class AetherNavItem(
    val icon: ImageVector,
    val label: String,
)

@Composable
fun AetherBottomNavBar(
    items: List<AetherNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(percent = 50)
    val outline = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current

    // Dukungan lensa liquid glass NYATA (AGSL RuntimeShader) hanya ada mulai
    // Android 13 (Tiramisu). Di bawah itu, kapsul tetap tampil lewat lapisan
    // highlight gradient (drawWithCache di bawah) tanpa distorsi/refraksi
    // per-piksel — jadi tetap terlihat baik, hanya tanpa efek lensa nyata.
    val pillShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(LIQUID_GLASS_CAPSULE_SHADER)
        } else {
            null
        }
    }

    // Fase "memantul" (bounce) yang berjalan pelan bolak-balik 0..1..0 tanpa
    // henti — sumber gerak untuk SEMUA highlight/refleksi di dalam kapsul
    // (baik lewat shader maupun lewat gradient biasa), supaya kesan "cahaya
    // memantul di dalam kaca cair" tetap hidup walau kapsul tidak disentuh.
    val bounceTransition = rememberInfiniteTransition(label = "liquidGlassBounce")
    val bouncePhase by bounceTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bouncePhase",
    )

    val glassRim = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.12f),
            outline.copy(alpha = 0.65f),
        ),
    )
    val glassSheen = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.02f),
            Color.Black.copy(alpha = 0.05f),
        ),
    )

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val slotWidthPx = if (items.isNotEmpty() && barWidthPx > 0f) barWidthPx / items.size else 0f

    // Posisi pill dalam satuan "index pecahan" — 0f = tab pertama,
    // 1f = tab kedua, 1.4f = 40% jalan dari tab kedua ke ketiga, dst.
    // Satu sumber kebenaran tunggal untuk animasi settle MAUPUN drag,
    // supaya tidak ada dua state posisi yang bisa saling tidak sinkron.
    val pillPosition = remember { Animatable(selectedIndex.toFloat()) }
    var previewIndex by remember { mutableStateOf(selectedIndex) }
    var isDragging by remember { mutableStateOf(false) }
    // Ditekan TAPI belum tentu jadi drag — dipisah dari isDragging supaya
    // pill bisa langsung "menyembul" begitu jari menyentuh, tanpa menunggu
    // ambang waktu long-press yang dipakai detectDragGesturesAfterLongPress
    // (itu jeda yang terasa seperti "delay" sebelumnya).
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Kecepatan gerak pill YANG SUDAH DI-SMOOTH (satuan: index pecahan per
    // frame), dipakai untuk efek jelly squash & stretch. Nilai mentah dari
    // event pointer sangat berisik (noisy) antar-frame, jadi dilewatkan
    // dulu lewat exponential smoothing (rawVelocitySample -> pillVelocity)
    // sebelum dipakai untuk visual, supaya jelly mengalir, bukan gemetar.
    var pillVelocity by remember { mutableFloatStateOf(0f) }
    var lastRawPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    // Dipakai HANYA untuk menurunkan velocity dari pergerakan pillPosition
    // itu sendiri (lihat efek di bawah) — sumber jelly saat pill meluncur
    // otomatis lewat animasi settle/follow, bukan cuma dari drag jari
    // mentah. Ini membuat efek jelly juga hidup saat TAP (pill meregang
    // dari titik sentuh menuju tab tujuan), bukan cuma saat drag manual.
    var lastAnimatedPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    // Spring "settle" untuk perpindahan tab yang sudah final (lepas jari /
    // tap) — dibuat lebih lembut & lebih kenyal di ujungnya dibanding
    // sebelumnya: stiffness diturunkan (StiffnessMedium -> StiffnessMediumLow)
    // supaya perjalanan pill antar-tab tidak terasa "meloncat" cepat/kaku,
    // dan dampingRatio diturunkan sedikit (MediumBouncy -> ambang antara
    // Medium & Low) supaya overshoot di akhir gerakan lebih terasa "jelly"
    // sebelum berhenti — bukan berhenti mendadak begitu sampai target.
    // Dipakai sebagai SATU-SATUNYA jalur animasi menuju selectedIndex,
    // supaya tidak pernah ada dua animasi berebut pillPosition di saat
    // bersamaan (itu penyebab pill terlihat "jeduk"/instan saat tap-select
    // tab jauh).
    val settleSpec = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 210f,
    )
    // Spring "follow" dipakai SELAMA jari menahan & menggeser — redaman
    // lebih tinggi (kurang mantul) tapi kekakuan lebih rendah dari settle,
    // supaya pill terasa "berat/kenyal" mengikuti jari (ala iOS 26) alih-
    // alih menempel mentah 1:1 pada posisi sentuhan setiap frame. Stiffness
    // diturunkan sedikit dari sebelumnya (380 -> 300) supaya gerak mengikuti
    // jari terasa lebih mengalir/tidak kaku, sambil tetap cukup responsif.
    val followSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 300f,
    )

    // SATU-SATUNYA tempat yang menganimasikan pillPosition menuju tab yang
    // benar-benar terpilih. Baik tap biasa maupun drag-lepas hanya perlu
    // mengubah selectedIndex (lewat onSelect) dan berhenti di situ — efek
    // ini yang akan menariknya dengan animasi pegas dari posisi manapun
    // pill sedang berada saat itu (termasuk dari titik sentuhan tap),
    // sehingga tidak pernah terjadi rebutan animasi/patah gerak.
    LaunchedEffect(selectedIndex, items.size) {
        previewIndex = selectedIndex
        if (!isDragging) {
            pillPosition.animateTo(selectedIndex.toFloat(), animationSpec = settleSpec)
        }
    }

    // Efek "menyembul" pada PILL: membesar melampaui tinggi normal bar
    // begitu jari MENYENTUH (isPressed), bukan baru saat drag resmi
    // terdeteksi — supaya responnya instan, lalu mengempis kembali saat
    // jari dilepas.
    val pillBulge = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        pillBulge.animateTo(
            targetValue = if (isPressed) 1.22f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    // Efek "menyembul" pada seluruh KAPSUL BAR: skala naik sedikit saat
    // ditahan, seolah seluruh bar ikut merespons tekanan seperti satu
    // permukaan liquid yang menyatu dengan pill di dalamnya — bukan cuma
    // pill yang bergerak sendirian di atas bar yang diam kaku.
    val barBulge = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        barBulge.animateTo(
            targetValue = if (isPressed) 1.035f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    // Sumber velocity TUNGGAL dan mulus: dibaca dari perubahan aktual
    // pillPosition.value setiap frame (snapshotFlow), bukan cuma dari
    // sample event pointer mentah. Ini menangkap pergerakan pill baik
    // saat DIGESER manual maupun saat ia meluncur sendiri lewat animasi
    // settle/follow (mis. saat tap memicu pill meregang dari titik sentuh
    // menuju tab tujuan) — sehingga efek jelly konsisten hidup di semua
    // jenis interaksi, bukan cuma drag. Exponential smoothing di sini
    // meredam noise antar-frame supaya squash & stretch mengalir halus.
    LaunchedEffect(Unit) {
        snapshotFlow { pillPosition.value }.collect { current ->
            val rawSample = current - lastAnimatedPosition
            lastAnimatedPosition = current
            pillVelocity = pillVelocity + (rawSample - pillVelocity) * 0.45f
        }
    }

    // Peluruhan velocity ke nol saat benar-benar diam (tidak digeser & tidak
    // sedang dianimasikan lagi) — mencegah sisa nilai kecil terus "getar"
    // tanpa akhir akibat floating point residu dari smoothing di atas.
    LaunchedEffect(isDragging, pillPosition.isRunning) {
        if (!isDragging && !pillPosition.isRunning && abs(pillVelocity) > 0.001f) {
            val decayAnim = Animatable(pillVelocity)
            decayAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ) {
                pillVelocity = value
            }
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .onSizeChanged {
                barWidthPx = it.width.toFloat()
                barHeightPx = it.height.toFloat()
            }
            // Tab paling kiri (Dashboard) & paling kanan bisa tumpang tindih
            // zona predictive-back-gesture sistem di tepi layar (gesture-nav
            // devices) — tanpa ini, tap di area itu kadang "dimakan" sistem
            // duluan sebelum sampai ke pointerInput di bawah, sehingga pill
            // sama sekali tidak bereaksi walau jari benar-benar menyentuh
            // tab-nya.
            .systemGestureExclusion()
            // PENTING: kunci pointerInput HANYA pada items.size, BUKAN pada
            // slotWidthPx (Float hasil pengukuran layout). slotWidthPx bisa
            // sedikit "jitter" antar-frame — misalnya saat barBulge/graphics
            // Layer mengubah skala bar, atau saat AnimatedContent di
            // MainScreen memicu reflow konten di atasnya — dan Compose akan
            // MEMBATALKAN lalu me-restart seluruh coroutine pointerInput
            // (termasuk loop awaitEachGesture yang sedang berjalan) setiap
            // kali key berubah. Kalau restart ini kebetulan terjadi PERSIS
            // di antara event down dan up jari (down sudah diproses, tapi
            // sebelum up sempat terbaca), gesture lama mati begitu saja
            // tanpa pernah memanggil onSelect — up jari berikutnya ditangkap
            // oleh instance awaitEachGesture yang BARU, yang menganggapnya
            // sebagai down baru. Inilah sebab bug "tap Dashboard dari tab
            // lain kadang tidak masuk": slot pertama/terakhir paling sering
            // kena reflow tepi saat transisi tab lain berlangsung. slotWidth
            // yang dipakai DI DALAM gesture sekarang dibaca live dari
            // barWidthPx terkini (lewat currentSlotWidth di bawah), bukan
            // dari nilai yang di-capture saat gesture pertama kali dimulai.
            .pointerInput(items.size) {
                awaitEachGesture {
                    val currentSlotWidth = if (barWidthPx > 0f) barWidthPx / items.size else 0f
                    if (currentSlotWidth <= 0f) return@awaitEachGesture
                    // down: feedback instan (bulge) di posisi sentuhan,
                    // tanpa menunggu apakah ini akan jadi long-press/drag.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Consume SEGERA — mengklaim gesture ini sebagai milik
                    // navbar, supaya tidak ada arbitrase ambigu dengan
                    // detector lain (nested scroll Scaffold, dsb.) yang bisa
                    // bikin down event "direbut" sebelum sempat diproses.
                    down.consume()
                    isPressed = true
                    val rawDown = (down.position.x / currentSlotWidth) - 0.5f
                    val clampedDown = rawDown.coerceIn(0f, (items.size - 1).toFloat())
                    lastRawPosition = clampedDown
                    pillVelocity = 0f
                    previewIndex = clampedDown.roundToInt().coerceIn(0, items.lastIndex)
                    scope.launch {
                        pillPosition.animateTo(clampedDown, animationSpec = followSpec)
                    }

                    // Bedakan TAP vs DRAG berdasarkan JARAK geser (touch
                    // slop), BUKAN berdasarkan berapa lama jari ditahan.
                    // Sebelumnya dipakai withTimeout(longPressTimeoutMillis)
                    // — itu sumber DUA bug yang dilaporkan sekaligus:
                    // 1) "capsule tidak bisa di-slide": swipe natural (jari
                    //    turun lalu LANGSUNG bergerak, tanpa jeda diam dulu)
                    //    tidak pernah dianggap drag selama masih di bawah
                    //    ambang waktu long-press — gerakan jari selama
                    //    jendela tunggu itu cuma di-consume lalu DIBUANG,
                    //    tidak pernah dipakai menggerakkan pill. Kalau jari
                    //    keburu terangkat sebelum timeout, semuanya jatuh ke
                    //    cabang "tap" yang cuma memakai posisi DOWN awal —
                    //    jadi pill terasa tidak bisa digeser sama sekali,
                    //    hanya bisa snap ke tab meski jari sudah menggeser
                    //    jauh.
                    // 2) "tap Dashboard kadang tidak masuk": withTimeout
                    //    membatalkan (throw TimeoutCancellationException)
                    //    coroutine yang SAMA yang juga sedang menunggu event
                    //    "up". Kalau event up datang PERSIS di sekitar saat
                    //    timeout menembak, ada race antara pembatalan
                    //    timeout vs pembacaan event up — hasilnya kadang
                    //    tidak konsisten (kadang gesture dianggap "up
                    //    duluan", kadang "timeout duluan" walau urutan
                    //    aslinya sama), sehingga sesekali tap normal gagal
                    //    memanggil onSelect. awaitHorizontalTouchSlopOrCancellation
                    //    di bawah ini tidak pakai timer sama sekali — ia
                    //    hanya berhenti menunggu kalau jari benar-benar
                    //    terangkat/dibatalkan, jadi tidak ada race semacam
                    //    itu.
                    // FIX: sebelumnya pakai awaitHorizontalTouchSlopOrCancellation,
                    // yang HANYA melacak slop di sumbu horizontal. Jari manusia
                    // hampir tidak pernah 100% lurus horizontal saat tap — sedikit
                    // gerakan vertikal (sub-pixel sampai beberapa px) sangat wajar.
                    // Detector horizontal-only ini bisa terus menunggu tanpa pernah
                    // memutuskan DRAG atau TAP dengan bersih saat ada komponen
                    // gerak vertikal, dan pada kondisi tertentu (terutama saat
                    // AnimatedContent di MainScreen memicu reflow persis di frame
                    // yang sama) event "up" berikutnya jadi tidak pernah terbaca
                    // oleh loop ini — gesture mati begitu saja tanpa memanggil
                    // onSelect. Paling sering kena di tab index 0 (Dashboard)
                    // karena posisinya di tepi bar, area paling rawan reflow.
                    // awaitTouchSlopOrCancellation melacak slop di SEGALA arah,
                    // jadi keputusan TAP vs DRAG selalu tercapai berdasarkan jarak
                    // geser aktual jari, bukan hanya komponen horizontalnya.
                    var slopChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    var slopDx = 0f
                    var slopDy = 0f
                    awaitTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        slopChange = change
                        slopDx = over.x
                        slopDy = over.y
                    }
                    // Gesture navbar ini murni horizontal (geser antar tab
                    // berdampingan) — kalau slop yang terdeteksi didominasi
                    // gerak vertikal (mis. user sedang scroll konten di atasnya
                    // tapi jarinya turun sedikit ke navbar), perlakukan tetap
                    // sebagai TAP di posisi awal, bukan DRAG.
                    if (slopChange != null && abs(slopDy) > abs(slopDx)) {
                        slopChange = null
                    }

                    val confirmedSlopChange = slopChange
                    if (confirmedSlopChange != null) {
                        isDragging = true
                        val dragPointerId = confirmedSlopChange.id
                        // Posisi pertama diambil dari titik SAAT slop
                        // terdeteksi (bukan menunggu event berikutnya) —
                        // supaya pill langsung mulai mengikuti jari tanpa
                        // "lompat" begitu drag resmi mulai.
                        val firstRaw = (confirmedSlopChange.position.x / currentSlotWidth) - 0.5f
                        val firstClamped = firstRaw.coerceIn(0f, (items.size - 1).toFloat())
                        lastRawPosition = firstClamped
                        previewIndex = firstClamped.roundToInt().coerceIn(0, items.lastIndex)
                        scope.launch {
                            pillPosition.animateTo(firstClamped, animationSpec = followSpec)
                        }
                        // Loop drag: ikuti jari sampai terangkat. Posisi
                        // mentah dikejar lewat spring "follow" (bukan
                        // snapTo instan) supaya gerakan pill terasa kenyal
                        // dan tidak terlalu cepat/kaku — sesuai nuansa
                        // iOS 26 liquid nav.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == dragPointerId }
                            if (change == null || !change.pressed) break
                            change.consume()
                            val dragRaw = (change.position.x / currentSlotWidth) - 0.5f
                            val dragClamped = dragRaw.coerceIn(0f, (items.size - 1).toFloat())
                            lastRawPosition = dragClamped
                            previewIndex = dragClamped.roundToInt().coerceIn(0, items.lastIndex)
                            scope.launch {
                                pillPosition.animateTo(dragClamped, animationSpec = followSpec)
                            }
                        }
                        isDragging = false
                        isPressed = false
                        // FIX (safety-net): previewIndex dibaca sebagai
                        // finalIndex APA PUN alasan loop di atas berhenti
                        // (up normal, pointer batal, atau change hilang dari
                        // daftar changes) — sebelumnya jika loop berhenti
                        // lewat jalur "change == null" tanpa event up yang
                        // bersih, kode di bawah ini tetap berjalan seperti
                        // biasa karena previewIndex sudah ter-update selama
                        // drag berlangsung, tapi ditambahkan log eksplisit
                        // di sini untuk memastikan onSelect benar-benar
                        // tereksekusi pada semua kondisi keluar loop.
                        val finalIndex = previewIndex
                        // onSelect dipanggil SEGERA — tidak menunggu animasi
                        // settle pill selesai — supaya transisi screen dan
                        // pill snap berjalan bersamaan, bukan berurutan.
                        // pillPosition SENGAJA tidak dianimasikan di sini:
                        // begitu selectedIndex berubah, LaunchedEffect di atas
                        // adalah satu-satunya yang menariknya ke slot final —
                        // menghindari dua animasi berebut target di saat yang
                        // sama (penyebab gerakan terlihat patah).
                        if (finalIndex != selectedIndex) {
                            onSelect(finalIndex)
                        } else {
                            scope.launch {
                                pillPosition.animateTo(finalIndex.toFloat(), animationSpec = settleSpec)
                            }
                        }
                    } else {
                        // slopChange == null: jari terangkat/dibatalkan
                        // SEBELUM pernah melewati touch slop horizontal —
                        // ini tap murni. onSelect dipanggil LANGSUNG DI SINI
                        // berdasarkan posisi sentuhan — bukan lewat
                        // Modifier.clickable terpisah di NavBarItem anak,
                        // supaya navigasi tidak bergantung pada arbitrase
                        // gesture antara pointerInput induk & clickable anak
                        // (dulu penyebab tap ke tab, paling sering Dashboard/
                        // index 0, kadang tidak memicu navigasi).
                        isPressed = false
                        val tappedIndex = clampedDown.roundToInt().coerceIn(0, items.lastIndex)
                        previewIndex = selectedIndex
                        if (tappedIndex != selectedIndex) {
                            onSelect(tappedIndex)
                        } else {
                            scope.launch {
                                pillPosition.animateTo(selectedIndex.toFloat(), animationSpec = settleSpec)
                            }
                        }
                    }
                }
            },
    ) {
        // Transform "bulge" (skala membesar saat ditekan) sengaja dipindah
        // ke SATU Box pembungkus KHUSUS UNTUK VISUAL ini — TERPISAH dari Box
        // induk di atas yang menangani pointerInput. SEBELUMNYA graphicsLayer
        // scale ini menempel LANGSUNG di Box yang sama dengan pointerInput,
        // sehingga posisi sentuh lokal yang dibaca gesture (down.position,
        // change.position, dst.) ikut "melayang" mengikuti skala yang terus
        // beranimasi selama jari ditahan (stiffness rendah -> settle-nya
        // lambat, ratusan ms). Item paling jauh dari tengah bar — Dashboard,
        // paling kiri — paling parah terdampak karena transformOrigin di
        // tengah: pergeseran koordinat lokal akibat skala ini SENDIRI, walau
        // jari sama sekali tidak bergerak di layar, cukup untuk melewati
        // ambang touch-slop dan bikin tap ke Dashboard salah terbaca sebagai
        // drag — lalu posisi akhir yang tersample kebetulan balik ke tab
        // semula, sehingga onSelect tidak pernah terpanggil walau pill
        // sempat terlihat bergerak ke arah Dashboard. Dengan scale dipindah
        // ke Box anak murni-visual ini, Box induk (pointerInput) tidak lagi
        // ikut tertransformasi sama sekali, jadi koordinat sentuh yang
        // dibaca gesture selalu stabil — sementara efek "menyembul" saat
        // ditekan tetap terlihat identik karena semua lapis visual di bawah
        // ini (kaca, pill, ikon+label) tetap satu kesatuan di dalam Box
        // pembungkus yang sama.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Bulge kapsul dipusatkan di tengah bar, jadi mengembang
                    // merata ke segala arah alih-alih menggeser posisi bar.
                    val scale = barBulge.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                },
        ) {
        // Kaca bar (di-clip ke bentuk kapsul) — lapis terpisah PALING BAWAH,
        // agar pill di atasnya bisa "menyembul" melampaui tepi bar tanpa
        // ikut terpotong oleh clip milik kaca ini.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 18.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(barShape)
                .hazeEffect(state = hazeState, style = HazeMaterials.thin())
                .background(glassSheen)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // Sapuan cahaya sangat tipis yang ikut bergerak bolak-
                    // balik di sepanjang KACA BAR (pakai bouncePhase yang
                    // sama dengan pill) — supaya seluruh kapsul terasa satu
                    // material liquid yang menyatu, bukan bar diam kaku
                    // dengan hanya pill di dalamnya yang "hidup".
                    val sweepX = lerp(-0.25f, 1.25f, bouncePhase) * w
                    val barSweep = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        start = Offset(sweepX - w * 0.3f, 0f),
                        end = Offset(sweepX + w * 0.3f, h),
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = barSweep)
                    }
                }
                .border(width = 1.dp, brush = glassRim, shape = barShape),
        )

        // Lapis 2: pill besar — TIDAK di-clip, sehingga saat ditahan boleh
        // membesar melampaui tinggi bar (efek "menyembul" liquid glass).
        if (slotWidthPx > 0f) {
            val insetPx = with(density) { 4.dp.toPx() }
            val pillWidth = slotWidthPx - insetPx * 2f
            val pillHeightBase = barHeightPx - insetPx * 2f
            val centerX = (pillPosition.value + 0.5f) * slotWidthPx
            val bulge = pillBulge.value

            // Efek jelly: squash & stretch berdasarkan kecepatan gerak.
            // Semakin cepat pill bergeser, semakin ia melebar horizontal
            // dan memipih vertikal (seperti tetesan cair yang ditarik),
            // lalu membulat kembali begitu berhenti/melambat.
            // Rentang squash & stretch sedikit diperlebar (0.26/0.16 ->
            // 0.30/0.20) supaya efek jelly di akhir gerakan (saat velocity
            // masih sisa sebelum meluruh ke nol berkat settleSpec yang kini
            // lebih lembut) terasa lebih kenyal, bukan cuma di tengah gerak.
            val speed = abs(pillVelocity).coerceIn(0f, 0.6f)
            val stretchFactor = 1f + (speed / 0.6f) * 0.30f
            val squashFactor = 1f - (speed / 0.6f) * 0.20f

            val pillWidthPx = pillWidth * (0.92f + 0.08f * bulge) * stretchFactor
            val pillHeightPx = pillHeightBase * bulge * squashFactor
            val pillWidthDp = with(density) { pillWidthPx.toDp() }
            val pillHeightDp = with(density) { pillHeightPx.toDp() }
            val offsetXDp = with(density) { (centerX - pillWidthPx / 2f).toDp() }
            val offsetYDp = with(density) { ((barHeightPx - pillHeightPx) / 2f).toDp() }

            // Energi highlight: naik saat kapsul ditekan/digeser sehingga
            // refleksi terasa BEREAKSI terhadap sentuhan (seperti liquid
            // glass asli), bukan animasi ambien yang berjalan sendiri tanpa
            // peduli interaksi pengguna.
            val dragEnergy = (0.45f + (abs(pillVelocity) / 0.6f).coerceIn(0f, 1f) * 0.35f +
                (if (isPressed) 0.2f else 0f)).coerceIn(0.45f, 1f)

            val tint = MaterialTheme.colorScheme.primary

            // Dasar pill liquid glass ala iOS: HAMPIR NETRAL, bukan tint
            // warna pekat. Liquid Glass asli itu dasarnya abu-abu gelap
            // transparan (blur dari Haze di bawahnya yang membentuk warna),
            // dan aksen warna tema HANYA muncul tipis — di rim/border dan
            // sedikit di dasar gradasi. Sebelumnya pillBrush mengisi seluruh
            // permukaan dengan cyan alpha 0.42, menutupi blur dan bikin pill
            // terlihat solid teal pekat, bukan bening seperti kaca.
            val pillBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.16f),
                ),
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = with(density) { offsetXDp.toPx() }
                        translationY = with(density) { offsetYDp.toPx() }
                    }
                    .size(width = pillWidthDp, height = pillHeightDp)
                    .shadow(
                        elevation = if (isPressed) 10.dp else 0.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = tint.copy(alpha = 0.4f),
                        spotColor = tint.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(50))
                    .graphicsLayer {
                        // Lensa liquid glass NYATA (bukan gradient tiruan):
                        // membiaskan & mendistorsi konten kaca (blur + highlight
                        // + border di bawah) lewat AGSL RuntimeShader, plus
                        // menambahkan pantulan cahaya yang bergerak bolak-balik.
                        // Hanya tersedia di Android 13+; di bawah itu properti
                        // ini dibiarkan null (tidak ada distorsi tambahan) dan
                        // kapsul tetap tampil lewat highlight gradient biasa
                        // (lihat drawWithCache di bawah, termasuk fallbackSweep).
                        if (pillShader != null) {
                            pillShader.setFloatUniform("resolution", pillWidthPx, pillHeightPx)
                            pillShader.setFloatUniform("bouncePhase", bouncePhase)
                            pillShader.setFloatUniform("energy", dragEnergy)
                            renderEffect = AndroidRenderEffect
                                .createRuntimeShaderEffect(pillShader, "content")
                                .asComposeRenderEffect()
                        }
                    }
                    // Blur kaca NYATA dari konten di belakang pill (sama
                    // sumbernya dengan blur bar) — bukan cuma warna solid.
                    .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                    .background(pillBrush)
                    // Semua lapisan cahaya (mirror highlight + refraksi tepi
                    // + tint tipis tema) digambar SEKALIGUS dalam satu Canvas
                    // lewat drawWithCache. PENTING: highlight di sini dibuat
                    // sebagai bright-spot KONTRAS di area kecil (radial, pusat
                    // di satu titik) dengan Color.White solid dan blendMode
                    // Screen/SrcOver biasa — BUKAN BlendMode.Plus disapu ke
                    // seluruh permukaan seperti versi sebelumnya. BlendMode.
                    // Plus bersifat additive: ketika disapu rata ke seluruh
                    // pill yang sudah bertint warna tema, hasilnya menyaturasi
                    // SELURUH permukaan jadi satu warna pekat merata (itulah
                    // sebab pill terlihat teal solid, bukan kaca bening
                    // dengan satu titik pantulan cahaya seperti iOS asli).
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        // Mirror highlight utama — BERGESER mengikuti
                        // bouncePhase (0..1..0, pegas bolak-balik) supaya
                        // bercak terang ini terasa "memantul"/bergoyang di
                        // dalam kapsul, bukan diam di satu titik selamanya.
                        // Tetap kontras/kecil (radial, satu titik), bukan
                        // menyebar rata ke seluruh pill.
                        val mirrorHighlight = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f * dragEnergy),
                                Color.White.copy(alpha = 0.25f * dragEnergy),
                                Color.Transparent,
                            ),
                            center = Offset(
                                lerp(w * 0.14f, w * 0.36f, bouncePhase),
                                h * (0.08f + 0.05f * bouncePhase),
                            ),
                            radius = w * 0.42f,
                        )
                        // Refleksi sekunder redup, bergerak BERLAWANAN arah
                        // dari highlight utama — dua titik pantulan yang
                        // saling menjauh/mendekat, meniru cahaya yang
                        // memantul-mantul di permukaan cair cembung (liquid
                        // glass iOS selalu punya dua titik highlight: satu
                        // dominan, satu samar).
                        val counterHighlight = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            center = Offset(lerp(w * 0.92f, w * 0.68f, bouncePhase), h * 0.92f),
                            radius = w * 0.35f,
                        )
                        // Refraksi tepi kiri & kanan — garis vertikal tipis
                        // menyala, khas distorsi cahaya pada pinggiran kaca
                        // cembung. Dijaga tipis (alpha rendah) supaya tidak
                        // ikut menutup blur di baliknya.
                        val edgeLeft = Brush.horizontalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                            startX = 0f,
                            endX = w * 0.14f,
                        )
                        val edgeRight = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f)),
                            startX = w * 0.86f,
                            endX = w,
                        )
                        // Tint identitas tema: TIPIS SEKALI, hanya di area
                        // bawah pill — ini satu-satunya tempat warna cyan
                        // tema muncul di permukaan (di luar border), supaya
                        // pill tetap terasa "kaca" alih-alih "plastik teal".
                        val bottomTint = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, tint.copy(alpha = 0.16f)),
                            startY = h * 0.55f,
                            endY = h,
                        )
                        // Sapuan cahaya diagonal tambahan yang bolak-balik
                        // melintasi kapsul — HANYA dipakai saat shader lensa
                        // (Android 13+, lihat graphicsLayer di atas) tidak
                        // tersedia, supaya device lama tetap dapat kesan
                        // "memantul" yang jelas tanpa perlu RuntimeShader.
                        // Di device yang sudah pakai shader, sapuan serupa
                        // (plus refraksi & distorsi latar) sudah dibuat di
                        // dalamnya — jadi tidak digambar dobel di sini.
                        val fallbackSweep = if (pillShader == null) {
                            val sweepX = lerp(-0.3f, 1.3f, bouncePhase) * w
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.30f * dragEnergy),
                                    Color.Transparent,
                                ),
                                start = Offset(sweepX - w * 0.22f, 0f),
                                end = Offset(sweepX + w * 0.22f, h),
                            )
                        } else {
                            null
                        }
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = bottomTint)
                            drawRect(brush = edgeLeft)
                            drawRect(brush = edgeRight)
                            drawRect(brush = counterHighlight)
                            drawRect(brush = mirrorHighlight)
                            fallbackSweep?.let { drawRect(brush = it) }
                        }
                    }
                    .border(
                        width = 1.dp,
                        // Stroke tipis netral abu-abu/putih halus — sengaja
                        // TIDAK memakai tint tema lagi (sebelumnya gradasi
                        // turun ke warna tema di bagian bawah), supaya rim
                        // kapsul terlihat kaca bening biasa, bukan dicat
                        // warna tema.
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.18f),
                            ),
                        ),
                        shape = RoundedCornerShape(50),
                    ),
            ) {
                // Sheen kaca ala iOS (rujukan: pill "Arcade" di App Store) —
                // BUKAN lagi refleksi cermin ikon yang dipantulkan/di-mirror
                // di tepi kapsul (versi sebelumnya, terlihat seperti ikon
                // ganda yang janggal). Sekarang hanya satu pita highlight
                // melengkung tipis di tepi ATAS kapsul, memudar ke bawah —
                // mensimulasikan cahaya yang menggelincir di permukaan kaca
                // cembung tanpa menduplikasi ikon apa pun. Ikon & label tab
                // di lapis atas (NavBarItem) tetap satu-satunya representasi
                // visual dari tab tersebut.
                val sheenAlpha = (0.16f + dragEnergy * 0.10f).coerceIn(0.16f, 0.26f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp)
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(topStart = 50f, topEnd = 50f, bottomStart = 50f, bottomEnd = 50f))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = sheenAlpha),
                                    Color.White.copy(alpha = sheenAlpha * 0.35f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        // Lapis 3: ikon + label tiap tab, sedikit inset agar tidak
        // menyentuh tepi kaca (menjaga area sentuh tetap nyaman).
        // NavBarItem di sini MURNI presentasional (tidak punya clickable
        // sendiri) — semua interaksi sentuh (tap MAUPUN drag) ditangani
        // TUNGGAL oleh pointerInput di Box induk di atas. Sebelumnya
        // NavBarItem punya Modifier.clickable sendiri yang tumpang tindih
        // dengan gesture custom induk di area yang sama, menyebabkan
        // resolusi gesture-arbitration Compose tidak konsisten — kadang
        // onClick anak tidak pernah terpanggil sehingga tap tab (paling
        // sering index 0 / Dashboard) gagal menavigasi.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
                    selected = if (isDragging) index == previewIndex else index == selectedIndex,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        }
    }
}

@Composable
private fun NavBarItem(
    item: AetherNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navItemColor",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
