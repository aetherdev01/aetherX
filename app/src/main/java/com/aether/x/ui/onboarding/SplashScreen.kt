package com.aether.x.ui.onboarding

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.aether.x.ui.theme.AetherMonoFamily
import com.aether.x.ui.theme.Spacing
import kotlinx.coroutines.delay

// v3.6 — splash bergaya terminal SELALU gelap, apa pun tema aplikasinya. Warna
// dibuat konstan (bukan token tema) karena banyak dipakai di lambda draw yang
// bukan @Composable.
private val SplashBg = Color(0xFF0D0D11)
private val SplashAccent = Color(0xFFA78BFA)
private val SplashAccent2 = Color(0xFFF2B84E)
private val SplashAccentSoft = Color(0xFFC4B5FD)
private val SplashOk = Color(0xFF5BE38A)
private val SplashErr = Color(0xFFFF6459)
private val SplashWarn = Color(0xFFF2B84E)
private val SplashMuted = Color(0xFF8A8A9A)


/** Lama minimum splash tampil supaya animasi boot sempat terbaca (ms). */
private const val MIN_SPLASH_MILLIS = 1300L

/**
 * Splash bergaya developer: layar "boot log" terminal.
 *
 * - Baris log diambil dari tahap startup NYATA di [SplashViewModel]
 *   (identitas, registrasi device, sinkron FCM, cek maintenance) — bukan
 *   animasi palsu — ditambah info device asli (ABI, API level).
 * - Ring berputar + garis scan digambar di fase draw (`drawBehind`), jadi
 *   animasi terus berjalan tanpa recomposition per frame.
 * - Progress berupa bar blok ala terminal dengan gradient cyan -> violet.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()

    val progress by animateFloatAsState(
        targetValue = uiState.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320),
        label = "splashProgress",
    )

    var started by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splashReveal",
    )
    LaunchedEffect(Unit) { started = true }

    // Selesai = startup selesai DAN minimum durasi terpenuhi.
    val startedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            val remaining = MIN_SPLASH_MILLIS - (System.currentTimeMillis() - startedAt)
            if (remaining > 0) delay(remaining)
            onSplashFinished()
        }
    }

    val infinite = rememberInfiniteTransition(label = "splashInfinite")
    val ringAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ring",
    )
    val scan by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "scan",
    )
    val cursor by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBg)
            .drawBehind {
                // Grid titik halus + garis scan yang turun pelan.
                val step = 28.dp.toPx()
                val dot = Color.White.copy(alpha = 0.045f)
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(dot, radius = 1.2f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
                val sy = size.height * scan
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, SplashAccent.copy(alpha = 0.07f), Color.Transparent),
                        startY = sy - 80.dp.toPx(),
                        endY = sy + 80.dp.toPx(),
                    ),
                    topLeft = Offset(0f, sy - 80.dp.toPx()),
                    size = Size(size.width, 160.dp.toPx()),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl)
                .alpha(reveal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Logo + ring berputar ────────────────────────────────────
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = size.minDimension / 2f - inset,
                        style = Stroke(width = stroke),
                    )
                    rotate(ringAngle) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, SplashAccent, SplashAccent2),
                            ),
                            startAngle = 0f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    rotate(-ringAngle * 0.6f) {
                        drawArc(
                            color = SplashAccentSoft.copy(alpha = 0.55f),
                            startAngle = 200f,
                            sweepAngle = 40f,
                            useCenter = false,
                            topLeft = Offset(inset + 8.dp.toPx(), inset + 8.dp.toPx()),
                            size = Size(arcSize.width - 16.dp.toPx(), arcSize.height - 16.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("AETHER") }
                    withStyle(SpanStyle(color = SplashAccent)) { append("X") }
                },
                fontFamily = AetherMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 6.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "root // kernel // tweak",
                fontFamily = AetherMonoFamily,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = SplashMuted,
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // ── Terminal boot log ───────────────────────────────────────
            BootLog(
                state = uiState,
                cursorAlpha = { cursor },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            BlockProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())

            if (uiState.failed) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                OutlinedButton(
                    onClick = viewModel::retry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplashAccent),
                ) {
                    Text("retry --connect", fontFamily = AetherMonoFamily)
                }
            }
        }

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            fontFamily = AetherMonoFamily,
            fontSize = 11.sp,
            color = SplashMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xxl),
        )
    }
}

private data class LogLine(val tag: String, val tagColor: Color, val text: String)

private val StageOrder = listOf(
    SplashStage.RESOLVING_IDENTITY to "resolve identity",
    SplashStage.REGISTERING_DEVICE to "register device",
    SplashStage.SYNCING_NOTIFICATIONS to "sync fcm token",
    SplashStage.CHECKING_MAINTENANCE to "check service status",
)

@Composable
private fun BootLog(
    state: SplashUiState,
    cursorAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    val lines = buildList {
        add(LogLine("sys", SplashAccentSoft, "${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"} · api ${Build.VERSION.SDK_INT}"))
        StageOrder.forEach { (stage, label) ->
            val current = state.stage
            when {
                current == SplashStage.DONE || stage.ordinal < current.ordinal ->
                    add(LogLine(" ok", SplashOk, label))
                stage == current && state.failed ->
                    add(LogLine("err", SplashErr, label))
                stage == current ->
                    add(LogLine(" ..", SplashWarn, label))
            }
        }
        if (state.stage == SplashStage.DONE) add(LogLine(" ok", SplashOk, "ready"))
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$ ",
                fontFamily = AetherMonoFamily,
                fontSize = 12.sp,
                color = SplashAccent,
            )
            Text(
                text = "aetherx --boot",
                fontFamily = AetherMonoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        lines.forEachIndexed { index, line ->
            val isLast = index == lines.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[${line.tag}] ",
                    fontFamily = AetherMonoFamily,
                    fontSize = 12.sp,
                    color = line.tagColor,
                )
                if (isLast) {
                    TypewriterText(text = line.text)
                } else {
                    Text(
                        text = line.text,
                        fontFamily = AetherMonoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLast) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Box(
                        modifier = Modifier
                            .size(width = 7.dp, height = 13.dp)
                            .graphicsLayer { alpha = cursorAlpha() }
                            .background(SplashAccent),
                    )
                }
            }
        }

        if (state.failed) {
            Text(
                text = state.statusText,
                fontFamily = AetherMonoFamily,
                fontSize = 11.sp,
                color = SplashErr,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** Mengetik karakter satu per satu; di-reset tiap [text] berubah. */
@Composable
private fun TypewriterText(text: String) {
    var count by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        var last = 0L
        while (count < text.length) {
            withFrameNanos { now ->
                if (now - last >= 22_000_000L) {
                    last = now
                    count++
                }
            }
        }
    }
    Text(
        text = text.take(count),
        fontFamily = AetherMonoFamily,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Bar progres blok ala terminal: [████████░░░░░░░░] 52% */
@Composable
private fun BlockProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val blocks = 20
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (blocks - 1)) / blocks
            val filled = progress * blocks
            for (i in 0 until blocks) {
                val x = i * (w + gap)
                val fill = (filled - i).coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.07f),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                if (fill > 0f) {
                    val t = i / (blocks - 1f)
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.lerp(SplashAccent, SplashAccent2, t).copy(alpha = 0.4f + 0.6f * fill),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            fontFamily = AetherMonoFamily,
            fontSize = 12.sp,
            color = SplashAccent,
            modifier = Modifier.width(40.dp),
        )
    }
}
