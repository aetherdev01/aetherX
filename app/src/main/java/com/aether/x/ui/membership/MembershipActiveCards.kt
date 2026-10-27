package com.aether.x.ui.membership

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.R
import com.aether.x.core.locale.AppLanguage
import com.aether.x.core.locale.LocaleManager
import com.aether.x.ui.components.LiquidButton
import com.aether.x.ui.components.LocalReduceMotionEnabled
import com.aether.x.ui.components.PopupDialog
import com.aether.x.ui.components.SectionCard
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.theme.AccentAmber
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.AccentGreen
import com.aether.x.ui.theme.AccentRed
import com.aether.x.ui.theme.AetherViolet
import com.aether.x.ui.theme.LocalAetherPalette
import com.aether.x.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/** Siklus langganan bulanan; dipakai sebagai skala cincin "sisa waktu". */
private const val PLAN_CYCLE_DAYS = 30f

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Util
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun rememberIsEnglish(): Boolean {
    val context = LocalContext.current
    return remember(context) { LocaleManager.current(context) == AppLanguage.ENGLISH }
}

private fun tr(isEnglish: Boolean, en: String, id: String): String = if (isEnglish) en else id

private fun appLocale(isEnglish: Boolean): Locale =
    if (isEnglish) AppLanguage.ENGLISH.locale else AppLanguage.INDONESIAN.locale

private fun fmt(pattern: String, millis: Long, locale: Locale): String =
    SimpleDateFormat(pattern, locale).format(Date(millis))

private fun openPurchaseUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.membership_pro_open_failed), Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// 1. Pass card (kartu member) — jelly, sheen, sparkle, confetti
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Kartu member bergaya kartu ID/kredit.
 *
 * Saat ACTIVE: masuk dengan jelly (pegas kurang teredam), "bernapas" halus, kilau (sheen)
 * menyapu tiap beberapa detik, percikan mengorbit di sekitar mahkota, dan confetti saat
 * lisensi baru saja diaktifkan ([celebrate] naik). Kartu juga elastis: ditekan = menyusut
 * lalu memantul, digeser horizontal = melar seperti agar-agar lalu bergoyang kembali.
 * Mengetuk mahkota memberi lompatan jelly tersendiri.
 */
@Composable
internal fun MembershipPassCard(
    status: MembershipUiStatus,
    expiresAtMillis: Long?,
    maskedKey: String?,
    headline: String,
    subtitle: String,
    celebrate: Int,
    modifier: Modifier = Modifier,
) {
    val isEnglish = rememberIsEnglish()
    val palette = LocalAetherPalette.current
    val reduceMotion = LocalReduceMotionEnabled.current
    val scope = rememberCoroutineScope()
    val locale = remember(isEnglish) { appLocale(isEnglish) }

    val active = status == MembershipUiStatus.ACTIVE
    val expired = status == MembershipUiStatus.EXPIRED
    val shape = RoundedCornerShape(26.dp)

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MS)
            value = System.currentTimeMillis()
        }
    }

    // ── state jelly ──
    val jellyX = remember { Animatable(1f) }
    val jellyY = remember { Animatable(1f) }
    val pull = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (active && !reduceMotion) {
            jellyX.snapTo(0.82f)
            jellyY.snapTo(0.82f)
            launch { jellyX.animateTo(1f, spring(dampingRatio = 0.26f, stiffness = 240f)) }
            launch { jellyY.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 240f)) }
        }
    }

    val breathe = rememberInfiniteTransition(label = "pass-breathe").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pass-breathe-value",
    )
    val sheen = rememberInfiniteTransition(label = "pass-sheen").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "pass-sheen-value",
    )

    val onCard = if (active) Color.White else palette.textPrimary
    val onCardMuted = onCard.copy(alpha = if (active) 0.74f else 0.68f)

    val brush = when {
        active -> Brush.linearGradient(
            listOf(
                lerp(palette.premium, Color.Black, 0.38f),
                lerp(palette.premium, palette.accent, 0.5f),
                palette.accent.copy(alpha = 0.92f),
            ),
        )
        expired -> Brush.linearGradient(listOf(palette.warningContainer, palette.surfaceAlt))
        else -> Brush.linearGradient(listOf(palette.surfaceRaised, palette.surfaceAlt))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val amp = if (active && !reduceMotion) 0.010f else 0f
                    val wobble = (breathe.value - 0.5f) * 2f * amp
                    val stretch = abs(pull.value) / size.width.coerceAtLeast(1f) * 0.5f
                    scaleX = jellyX.value * (1f + wobble + stretch)
                    scaleY = jellyY.value * (1f - wobble - stretch * 0.6f)
                    translationX = pull.value * 0.6f
                }
                .pointerInput(reduceMotion) {
                    detectTapGestures(
                        onPress = {
                            if (!reduceMotion) {
                                scope.launch {
                                    launch { jellyX.animateTo(1.05f, spring(0.6f, 800f)) }
                                    launch { jellyY.animateTo(0.93f, spring(0.6f, 800f)) }
                                }
                            }
                            tryAwaitRelease()
                            if (!reduceMotion) {
                                scope.launch {
                                    launch { jellyX.animateTo(1f, spring(0.22f, 260f)) }
                                    launch { jellyY.animateTo(1f, spring(0.30f, 260f)) }
                                }
                            }
                        },
                    )
                }
                .pointerInput(reduceMotion) {
                    val maxPull = 56.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 220f)) }
                        },
                        onDragCancel = {
                            scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 220f)) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        if (!reduceMotion) {
                            val target = (pull.value + dragAmount * 0.55f).coerceIn(-maxPull, maxPull)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { pull.snapTo(target) }
                        }
                    }
                }
                .clip(shape)
                .background(brush)
                .drawBehind {
                    if (active) {
                        // Scrim bawah supaya teks putih tetap terbaca di semua aksen tema.
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                            ),
                        )
                    }
                }
                .drawWithContent {
                    drawContent()
                    val p = sheen.value
                    if (active && !reduceMotion && p < 0.5f) {
                        val k = p / 0.5f
                        val w = size.width
                        val x = -w * 0.4f + k * w * 1.8f
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent),
                                start = Offset(x, 0f),
                                end = Offset(x + w * 0.32f, size.height * 0.55f),
                            ),
                        )
                    }
                }
                .border(
                    BorderStroke(1.dp, onCard.copy(alpha = if (active) 0.20f else 0.10f)),
                    shape,
                )
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 168.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // ── baris atas: mahkota + merek | status ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CrownBadge(active = active, tint = if (active) Color.White else palette.textSecondary)
                        Column {
                            Text(
                                text = "AETHERX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp,
                                color = onCard,
                            )
                            Text(
                                text = if (active) "PRO MEMBER" else tr(isEnglish, "MEMBERSHIP", "KEANGGOTAAN"),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                color = onCardMuted,
                            )
                        }
                    }
                    when (status) {
                        MembershipUiStatus.ACTIVE -> LivePill(
                            text = tr(isEnglish, "ACTIVE", "AKTIF"),
                            container = Color.White.copy(alpha = 0.18f),
                            content = Color.White,
                            dot = Color(0xFF7CFFB2),
                            pulse = !reduceMotion,
                        )
                        MembershipUiStatus.EXPIRED -> LivePill(
                            text = tr(isEnglish, "EXPIRED", "KEDALUWARSA"),
                            container = AccentAmber.copy(alpha = 0.16f),
                            content = AccentAmber,
                            dot = AccentAmber,
                            pulse = false,
                        )
                        MembershipUiStatus.INACTIVE -> LivePill(
                            text = tr(isEnglish, "INACTIVE", "NONAKTIF"),
                            container = palette.textSecondary.copy(alpha = 0.12f),
                            content = palette.textSecondary,
                            dot = palette.textSecondary,
                            pulse = false,
                        )
                        MembershipUiStatus.CHECKING -> LivePill(
                            text = tr(isEnglish, "CHECKING", "MEMERIKSA"),
                            container = palette.textSecondary.copy(alpha = 0.12f),
                            content = palette.textSecondary,
                            dot = palette.textSecondary,
                            pulse = !reduceMotion,
                        )
                    }
                }

                // ── tengah: chip + nomor kunci tersamar / judul status ──
                if (active) {
                    Row(
                        modifier = Modifier.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardChip()
                        Text(
                            text = maskedKey ?: "\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            color = onCard,
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(vertical = 14.dp)) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = onCard,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = onCardMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // ── bawah: berlaku hingga | sisa ──
                if (expiresAtMillis != null && (active || expired)) {
                    val remaining = expiresAtMillis - now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text(
                                text = if (active) tr(isEnglish, "VALID UNTIL", "BERLAKU HINGGA")
                                else tr(isEnglish, "EXPIRED ON", "BERAKHIR PADA"),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                color = onCardMuted,
                            )
                            Text(
                                text = fmt("d MMM yyyy", expiresAtMillis, locale),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = onCard,
                            )
                        }
                        if (active) {
                            Text(
                                text = shortRemaining(remaining, isEnglish),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = onCard,
                            )
                        }
                    }
                }
            }
        }

        ConfettiBurst(trigger = celebrate, modifier = Modifier.matchParentSize())
    }
}

private fun shortRemaining(remaining: Long, isEnglish: Boolean): String {
    val r = remaining.coerceAtLeast(0L)
    val d = r / DAY_MS
    val h = r % DAY_MS / HOUR_MS
    val m = r % HOUR_MS / MINUTE_MS
    return when {
        d > 0 -> tr(isEnglish, "$d days left", "$d hari lagi")
        h > 0 -> tr(isEnglish, "$h hrs left", "$h jam lagi")
        else -> tr(isEnglish, "$m min left", "$m mnt lagi")
    }
}

/** Mahkota + percikan mengorbit. Ketuk = lompatan jelly sendiri. */
@Composable
private fun CrownBadge(active: Boolean, tint: Color) {
    val reduceMotion = LocalReduceMotionEnabled.current
    val scope = rememberCoroutineScope()
    val crownX = remember { Animatable(1f) }
    val crownY = remember { Animatable(1f) }

    val bob = rememberInfiniteTransition(label = "crown-bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crown-bob-value",
    )
    val orbit = rememberInfiniteTransition(label = "crown-orbit").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "crown-orbit-value",
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .drawBehind {
                if (!active || reduceMotion) return@drawBehind
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()
                for (i in 0 until 3) {
                    val a = (orbit.value + i / 3f) * 2f * PI.toFloat()
                    val pulse = 0.55f + 0.45f * sin(a * 2f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f + 0.55f * pulse),
                        radius = 2.4.dp.toPx() * (0.7f + 0.5f * pulse),
                        center = Offset(c.x + cos(a) * radius, c.y + sin(a) * radius),
                    )
                }
            }
            .pointerInput(reduceMotion) {
                detectTapGestures {
                    if (!reduceMotion) {
                        scope.launch {
                            crownX.snapTo(1.38f)
                            crownY.snapTo(0.68f)
                            launch { crownX.animateTo(1f, spring(dampingRatio = 0.18f, stiffness = 300f)) }
                            launch { crownY.animateTo(1f, spring(dampingRatio = 0.22f, stiffness = 300f)) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (active) 0.20f else 0.12f))
                .graphicsLayer {
                    val k = if (active && !reduceMotion) bob.value else 0.5f
                    translationY = -2.5.dp.toPx() * (k - 0.5f) * 2f
                    scaleX = crownX.value
                    scaleY = crownY.value
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CardChip() {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFFF6DD94), Color(0xFFC9A24A))),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF8A6A20).copy(alpha = 0.55f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .height(30.dp)
                .background(Color(0xFF8A6A20).copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun LivePill(text: String, container: Color, content: Color, dot: Color, pulse: Boolean) {
    val t = rememberInfiniteTransition(label = "live-pill").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "live-pill-dot",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer {
                    val k = if (pulse) t.value else 1f
                    alpha = k
                    scaleX = 0.7f + 0.5f * k
                    scaleY = 0.7f + 0.5f * k
                }
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = content,
        )
    }
}

/** Letupan confetti singkat saat lisensi baru diaktifkan. Tidak menangkap sentuhan. */
@Composable
private fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var seed by remember { mutableIntStateOf(0) }
    val colors = listOf(AccentGreen, AetherViolet, AccentBlue, AccentAmber, Color.White)

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            seed = trigger
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1600, easing = LinearOutSlowInEasing))
        }
    }

    Canvas(modifier = modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val rnd = Random(seed)
        val cx = size.width / 2f
        val cy = size.height * 0.38f
        repeat(40) { i ->
            val angle = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = (0.30f + rnd.nextFloat() * 0.70f) * size.width * 0.62f
            val dist = speed * p
            val gravity = size.height * 0.45f * p * p
            val x = cx + cos(angle) * dist
            val y = cy + sin(angle) * dist + gravity
            val a = (1f - p).coerceIn(0f, 1f)
            val s = 5.dp.toPx() * (0.6f + rnd.nextFloat() * 0.8f)
            val spin = rnd.nextFloat() * 360f + p * 540f
            rotate(degrees = spin, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[i % colors.size].copy(alpha = a),
                    topLeft = Offset(x - s / 2f, y - s / 4f),
                    size = Size(s, s / 2f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// 2. Sisa waktu lisensi — cincin + hitung mundur detik demi detik
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LicenseCountdownCard(
    expiresAtMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val isEnglish = rememberIsEnglish()
    val reduceMotion = LocalReduceMotionEnabled.current
    val context = LocalContext.current
    val locale = remember(isEnglish) { appLocale(isEnglish) }
    val purchaseUrl = stringResource(R.string.membership_pro_purchase_url)

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(SECOND_MS - (System.currentTimeMillis() % SECOND_MS) + 5L)
            value = System.currentTimeMillis()
        }
    }

    SectionCard(
        title = tr(isEnglish, "License time remaining", "Sisa waktu lisensi"),
        watermarkIcon = Icons.Outlined.Timer,
        accent = AccentGreen,
        modifier = modifier,
    ) {
        if (expiresAtMillis == null) {
            Text(
                text = tr(
                    isEnglish,
                    "The expiry date is not available yet. It will appear once the license is verified.",
                    "Tanggal berakhir belum tersedia. Akan tampil setelah lisensi terverifikasi.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val remaining = (expiresAtMillis - now).coerceAtLeast(0L)
        val days = (remaining / DAY_MS).toInt()
        val hours = (remaining % DAY_MS / HOUR_MS).toInt()
        val minutes = (remaining % HOUR_MS / MINUTE_MS).toInt()
        val seconds = (remaining % MINUTE_MS / SECOND_MS).toInt()

        val tone = when {
            remaining <= DAY_MS -> AccentRed
            remaining <= 7 * DAY_MS -> AccentAmber
            else -> AccentGreen
        }
        val toneLabel = when {
            remaining <= 0L -> tr(isEnglish, "Re-checking…", "Memeriksa ulang…")
            remaining <= DAY_MS -> tr(isEnglish, "Expires within 24 hours", "Berakhir dalam 24 jam")
            remaining <= 7 * DAY_MS -> tr(isEnglish, "Expiring soon", "Segera berakhir")
            else -> tr(isEnglish, "License in good standing", "Lisensi dalam kondisi baik")
        }

        var ringReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { ringReady = true }
        val fraction = (remaining.toFloat() / (PLAN_CYCLE_DAYS * DAY_MS)).coerceIn(0f, 1f)
        val animatedFraction by animateFloatAsState(
            targetValue = if (ringReady) fraction else 0f,
            animationSpec = tween(if (reduceMotion) 0 else 1200, easing = FastOutSlowInEasing),
            label = "license-ring",
        )

        val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
        val (bigNumber, bigUnit) = when {
            days > 0 -> days to tr(isEnglish, "days", "hari")
            hours > 0 -> hours to tr(isEnglish, "hrs", "jam")
            else -> minutes to tr(isEnglish, "min", "mnt")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(104.dp)) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = tone,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedFraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = bigNumber.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tone,
                    )
                    Text(
                        text = bigUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(tone.copy(alpha = 0.13f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
                    Text(
                        text = toneLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tone,
                    )
                }
                Text(
                    text = tr(isEnglish, "Expires on", "Berakhir pada"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fmt("EEEE, d MMMM yyyy", expiresAtMillis, locale),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tr(isEnglish, "at ", "pukul ") + fmt("HH:mm", expiresAtMillis, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeCell(days, tr(isEnglish, "Days", "Hari"), tone, Modifier.weight(1f))
            TimeCell(hours, tr(isEnglish, "Hours", "Jam"), tone, Modifier.weight(1f))
            TimeCell(minutes, tr(isEnglish, "Min", "Menit"), tone, Modifier.weight(1f))
            TimeCell(seconds, tr(isEnglish, "Sec", "Detik"), tone, Modifier.weight(1f))
        }

        Text(
            text = tr(
                isEnglish,
                "The ring shows the remaining share of a 30-day cycle.",
                "Cincin menunjukkan sisa porsi dari siklus 30 hari.",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (remaining <= 7 * DAY_MS) {
            LiquidButton(
                onClick = { openPurchaseUrl(context, purchaseUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tone,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = tr(isEnglish, "Renew membership", "Perpanjang membership"),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TimeCell(value: Int, label: String, tone: Color, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotionEnabled.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(160)))
                }
            },
            label = "time-cell-$label",
        ) { v ->
            Text(
                text = v.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tone,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// 3. Detail lisensi
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LicenseDetailsCard(
    expiresAtMillis: Long?,
    maskedKey: String?,
    isOfflineCache: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEnglish = rememberIsEnglish()
    val locale = remember(isEnglish) { appLocale(isEnglish) }

    SectionCard(
        title = tr(isEnglish, "License details", "Detail lisensi"),
        watermarkIcon = Icons.Outlined.VpnKey,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DetailRow(
                icon = Icons.Outlined.CheckCircle,
                label = tr(isEnglish, "Status", "Status"),
                value = tr(isEnglish, "Active \u2022 Pro", "Aktif \u2022 Pro"),
                valueColor = AccentGreen,
            )
            DetailDivider()
            DetailRow(
                icon = Icons.Outlined.VpnKey,
                label = tr(isEnglish, "License key", "Kunci lisensi"),
                value = maskedKey ?: "\u2022\u2022\u2022\u2022",
                mono = true,
            )
            DetailDivider()
            DetailRow(
                icon = Icons.Outlined.Event,
                label = tr(isEnglish, "Valid until", "Berlaku hingga"),
                value = expiresAtMillis?.let { fmt("d MMMM yyyy, HH:mm", it, locale) }
                    ?: tr(isEnglish, "Not available", "Belum tersedia"),
            )
            DetailDivider()
            DetailRow(
                icon = if (isOfflineCache) Icons.Outlined.CloudOff else Icons.Outlined.Sync,
                label = tr(isEnglish, "Verification", "Verifikasi"),
                value = if (isOfflineCache) {
                    tr(isEnglish, "Offline cache \u2022 will re-check when online", "Cache offline \u2022 dicek ulang saat online")
                } else {
                    tr(isEnglish, "Live sync with server (about every minute)", "Sinkron langsung dengan server (\u00B11 menit)")
                },
                valueColor = if (isOfflineCache) AccentAmber else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    mono: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// 4. Info fingerprint perangkat
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DeviceFingerprintCard(
    deviceId: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnglish = rememberIsEnglish()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val isFallback = deviceId.startsWith("fallback:")
    val raw = remember(deviceId) { deviceId.removePrefix("fallback:") }
    val groups = remember(raw) { raw.uppercase().chunked(4) }
    val shownText = if (expanded || groups.size <= 6) {
        groups.chunked(4).joinToString("\n") { it.joinToString(" ") }
    } else {
        groups.take(4).joinToString(" ") + "  \u2026  " + groups.takeLast(2).joinToString(" ")
    }
    val deviceName = remember {
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
    }

    SectionCard(
        title = tr(isEnglish, "Device fingerprint", "Sidik jari perangkat"),
        watermarkIcon = Icons.Outlined.Fingerprint,
        accent = AetherViolet,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FingerprintGlyph(seed = raw, color = AetherViolet, modifier = Modifier.size(76.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.13f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = tr(isEnglish, "Bound to this device", "Terikat ke perangkat ini"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGreen,
                    )
                }
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tr(
                        isEnglish,
                        "This pattern is drawn from your fingerprint code.",
                        "Pola ini digambar dari kode fingerprint kamu.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Kode fingerprint (tersamar sebagian; ketuk "Tampilkan" untuk penuh).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aetherCardSurface(shape = MaterialTheme.shapes.medium)
                .animateContentSize()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = tr(isEnglish, "FINGERPRINT CODE", "KODE FINGERPRINT"),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = shownText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (expanded) tr(isEnglish, "Hide", "Sembunyikan") else tr(isEnglish, "Show full", "Tampilkan penuh"),
                        modifier = Modifier.padding(start = 4.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(deviceId))
                        Toast.makeText(
                            context,
                            context.getString(R.string.membership_device_id_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.membership_device_id_copy_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DetailRow(
                icon = Icons.Outlined.Memory,
                label = tr(isEnglish, "Fingerprint source", "Sumber fingerprint"),
                value = if (isFallback) {
                    tr(isEnglish, "ANDROID_ID (fallback \u2022 native failed)", "ANDROID_ID (fallback \u2022 native gagal)")
                } else {
                    tr(isEnglish, "Native engine (device ID + build + model)", "Mesin native (ID perangkat + build + model)")
                },
                valueColor = if (isFallback) AccentAmber else MaterialTheme.colorScheme.onSurface,
            )
            DetailDivider()
            DetailRow(
                icon = Icons.Outlined.Fingerprint,
                label = tr(isEnglish, "Code length", "Panjang kode"),
                value = tr(isEnglish, "${raw.length} characters", "${raw.length} karakter"),
            )
            DetailDivider()
            DetailRow(
                icon = Icons.Outlined.PhoneAndroid,
                label = tr(isEnglish, "System", "Sistem"),
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
        }

        Text(
            text = tr(
                isEnglish,
                "Your license is tied to this fingerprint. It only works on the device that activated it; sign out to release it.",
                "Lisensi kamu terikat ke fingerprint ini. Hanya berlaku di perangkat yang mengaktifkannya; keluar dulu untuk melepasnya.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LiquidButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = AccentRed,
            ),
        ) {
            Text(
                text = stringResource(R.string.membership_logout_button),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    if (showLogoutConfirm) {
        PopupDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = Icons.Outlined.Logout,
            iconTint = AccentRed,
            title = stringResource(R.string.membership_logout_confirm_title),
            message = stringResource(R.string.membership_logout_confirm_message),
            confirmLabel = stringResource(R.string.membership_logout_confirm_action),
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            confirmIsDestructive = true,
            dismissLabel = stringResource(R.string.membership_logout_cancel),
        )
    }
}

/**
 * Pola visual simetris 5x5 yang diturunkan dari kode fingerprint (seperti identicon), plus garis
 * pindai yang bolak-balik. Murni dekoratif: membantu mengenali "ini perangkat yang sama".
 */
@Composable
private fun FingerprintGlyph(seed: String, color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotionEnabled.current
    val bits = remember(seed) {
        BooleanArray(15) { i ->
            val c = seed.getOrNull(i) ?: '0'
            Character.digit(c, 16).coerceAtLeast(0) % 2 == 1
        }
    }
    val scan = rememberInfiniteTransition(label = "fp-scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fp-scan-value",
    )

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f)),
    ) {
        val pad = size.width * 0.14f
        val cell = (size.width - pad * 2f) / 5f
        val gap = cell * 0.14f
        for (row in 0 until 5) {
            for (col in 0 until 3) {
                if (!bits[row * 3 + col]) continue
                val radius = CornerRadius(cell * 0.28f, cell * 0.28f)
                val y = pad + row * cell + gap / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad + col * cell + gap / 2f, y),
                    size = Size(cell - gap, cell - gap),
                    cornerRadius = radius,
                )
                if (col != 2) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(pad + (4 - col) * cell + gap / 2f, y),
                        size = Size(cell - gap, cell - gap),
                        cornerRadius = radius,
                    )
                }
            }
        }
        if (!reduceMotion) {
            val y = pad + (size.height - pad * 2f) * scan.value
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(pad * 0.6f, y),
                end = Offset(size.width - pad * 0.6f, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
