package com.aether.x.ui.dashboard

import android.os.Build
import android.os.SystemClock
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.x.R
import com.aether.x.core.device.DeviceInfoProvider
import com.aether.x.core.device.DeviceInfoSnapshot
import com.aether.x.core.device.toGbLabel
import com.aether.x.core.locale.str
import com.aether.x.ui.components.aetherCardSurface
import com.aether.x.ui.theme.LocalAetherPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val MetricGreen = Color(0xFF29B765)
private val MetricAmber = Color(0xFFFFB74D)
private val MetricRed = Color(0xFFFF6868)

/** Live device cards. Sensor and usage values are refreshed only while this section is composed. */
@Composable
fun DashboardOverviewSection(
    info: DeviceInfoSnapshot?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val liveInfo by produceState(
        initialValue = info,
        key1 = info,
        key2 = context,
    ) {
        while (true) {
            value = withContext(Dispatchers.IO) { DeviceInfoProvider.read(context) }
            delay(12_000L)
        }
    }
    val clockNow by produceState(initialValue = SystemClock.elapsedRealtime(), key1 = context) {
        while (true) {
            delay(5_000L)
            value = SystemClock.elapsedRealtime()
        }
    }
    val transition = rememberInfiniteTransition(label = "dashboard-glass-effects")
    val glow by transition.animateFloat(
        initialValue = 0.035f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dashboard-glow",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dashboard-wave",
    )

    val battery = liveInfo?.batteryPercent?.takeIf { it in 0..100 }
    val ramFraction = liveInfo?.ramUsedFraction?.coerceIn(0f, 1f)
    val systemTemperature = liveInfo?.systemTempC
    val batteryTemperature = liveInfo?.batteryTempC
    val temperatureAccent = when {
        systemTemperature == null -> LocalAetherPalette.current.accent
        systemTemperature >= 60f -> MetricRed
        systemTemperature >= 45f -> MetricAmber
        else -> MetricGreen
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveSystemMonitorCard(
            info = liveInfo,
            temperature = systemTemperature,
            batteryPercent = battery,
            ramPercent = ramFraction?.let { (it * 100f).toInt() },
            glow = glow,
            phase = phase,
        )

        // Mixed dashboard rhythm: wide telemetry cards are interleaved with
        // uneven two-column rows instead of repeating identical 2x2 tiles.
        MetricCard(
            title = str(R.string.dashboard_overview_temperature),
            value = systemTemperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) }
                ?: str(R.string.dashboard_overview_unavailable),
            detail = if (liveInfo?.systemTempC != null) str(R.string.dashboard_overview_soc_temperature_detail)
                else str(R.string.dashboard_overview_temperature_fallback_detail),
            icon = Icons.Outlined.DeviceThermostat,
            accent = temperatureAccent,
            progress = systemTemperature?.div(85f)?.coerceIn(0f, 1f),
            glow = glow,
            wide = true,
            cardHeight = 92.dp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MetricCard(
                title = str(R.string.dashboard_overview_battery_temperature),
                value = batteryTemperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) }
                    ?: str(R.string.dashboard_overview_unavailable),
                detail = str(R.string.dashboard_overview_battery_temperature_detail),
                icon = Icons.Outlined.BatteryChargingFull,
                accent = if (batteryTemperature != null && batteryTemperature >= 42f) MetricAmber else LocalAetherPalette.current.accent,
                progress = batteryTemperature?.div(60f)?.coerceIn(0f, 1f),
                glow = glow,
                modifier = Modifier.weight(1.12f),
            )
            MetricCard(
                title = str(R.string.dashboard_overview_charging),
                value = str(if (liveInfo?.isCharging == true) R.string.dashboard_overview_charging_active else R.string.dashboard_overview_charging_inactive),
                detail = str(R.string.dashboard_overview_charging_detail),
                icon = Icons.Outlined.BatteryChargingFull,
                accent = if (liveInfo?.isCharging == true) MetricGreen else LocalAetherPalette.current.accent,
                glow = glow,
                modifier = Modifier.weight(0.88f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MetricCard(
                title = str(R.string.dashboard_overview_battery),
                value = battery?.let { "$it%" } ?: str(R.string.dashboard_overview_unavailable),
                detail = battery?.let { str(R.string.dashboard_overview_battery_remaining_format, it) }
                    ?: str(R.string.dashboard_overview_battery_unknown_detail),
                icon = Icons.Outlined.BatteryFull,
                accent = when {
                    battery == null -> LocalAetherPalette.current.accent
                    battery <= 15 -> MetricRed
                    battery <= 35 -> MetricAmber
                    else -> MetricGreen
                },
                progress = battery?.div(100f),
                invertProgress = true,
                glow = glow,
                modifier = Modifier.weight(0.9f),
            )
            val ramCardFraction = ramFraction
            MetricCard(
                title = str(R.string.dashboard_overview_ram),
                value = ramCardFraction?.let { "${(it * 100f).toInt()}%" } ?: str(R.string.dashboard_overview_unavailable),
                detail = liveInfo?.let { "${it.usedRamBytes.toGbLabel()} / ${it.totalRamBytes.toGbLabel()}" }
                    ?: str(R.string.dashboard_overview_usage_unavailable),
                icon = Icons.Outlined.Memory,
                accent = when {
                    ramCardFraction == null -> LocalAetherPalette.current.accent
                    ramCardFraction >= 0.88f -> MetricRed
                    ramCardFraction >= 0.72f -> MetricAmber
                    else -> LocalAetherPalette.current.accent
                },
                progress = ramCardFraction,
                glow = glow,
                modifier = Modifier.weight(1.1f),
            )
        }

        val storageFraction = liveInfo?.storageUsedFraction?.coerceIn(0f, 1f)
        MetricCard(
            title = str(R.string.dashboard_overview_storage),
            value = storageFraction?.let { "${(it * 100f).toInt()}%" } ?: str(R.string.dashboard_overview_unavailable),
            detail = liveInfo?.let { "${it.usedStorageBytes.toGbLabel()} / ${it.totalStorageBytes.toGbLabel()}" }
                ?: str(R.string.dashboard_overview_usage_unavailable),
            icon = Icons.Outlined.SdStorage,
            accent = when {
                storageFraction == null -> LocalAetherPalette.current.accent
                storageFraction >= 0.90f -> MetricRed
                storageFraction >= 0.75f -> MetricAmber
                else -> LocalAetherPalette.current.accent
            },
            progress = storageFraction,
            glow = glow,
            wide = true,
            cardHeight = 92.dp,
        )

        SystemDetailsCard(
            nowElapsed = maxOf(clockNow, SystemClock.elapsedRealtime()),
            glow = glow,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AndroidVersionCard(
                info = liveInfo,
                glow = glow,
                phase = phase,
                modifier = Modifier.weight(1f),
            )
            DeviceHardwareCard(
                info = liveInfo,
                glow = glow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LiveSystemMonitorCard(
    info: DeviceInfoSnapshot?,
    temperature: Float?,
    batteryPercent: Int?,
    ramPercent: Int?,
    glow: Float,
    phase: Float,
) {
    val palette = LocalAetherPalette.current
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .aetherCardSurface(shape = shape, accent = palette.accent)
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.07f + glow * 0.35f),
                            palette.accentSoft.copy(alpha = 0.035f + glow * 0.12f),
                            Color.Transparent,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                drawCircle(
                    color = palette.accent.copy(alpha = glow * 0.7f),
                    radius = size.minDimension * 0.56f,
                    center = Offset(size.width * 0.98f, size.height * 0.05f),
                )
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.accent.copy(alpha = 0.16f + glow * 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = str(R.string.dashboard_overview_live_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = str(R.string.dashboard_overview_live_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MetricGreen.copy(alpha = 0.68f + glow.coerceAtMost(0.28f))),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniTelemetryValue(
                label = str(R.string.dashboard_overview_temperature_short),
                value = temperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) } ?: "—",
            )
            MiniTelemetryValue(
                label = str(R.string.dashboard_overview_battery),
                value = batteryPercent?.let { "$it%" } ?: "—",
            )
            MiniTelemetryValue(
                label = str(R.string.dashboard_overview_ram),
                value = ramPercent?.let { "$it%" } ?: "—",
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(15.dp)) {
            val count = 34
            val step = size.width / count
            val barWidth = step * 0.38f
            repeat(count) { index ->
                val wave = ((sin((index * 0.56f + phase).toDouble()).toFloat() + 1f) / 2f)
                val barHeight = size.height * (0.18f + wave * 0.76f)
                drawRoundRect(
                    color = palette.accent.copy(alpha = 0.16f + glow * (0.9f - wave * 0.25f)),
                    topLeft = Offset(index * step + step * 0.26f, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}

@Composable
private fun MiniTelemetryValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    progress: Float? = null,
    invertProgress: Boolean = false,
    glow: Float,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    cardHeight: androidx.compose.ui.unit.Dp = 132.dp,
) {
    val palette = LocalAetherPalette.current
    val shape = RoundedCornerShape(if (wide) 24.dp else 22.dp)
    val clampedProgress = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress ?: 0f,
        animationSpec = tween(durationMillis = 650),
        label = "dashboardMetricProgress",
    )
    val barColor = if (invertProgress && (clampedProgress ?: 1f) <= 0.15f) MetricRed else accent
    val surfaceModifier = modifier
        .height(cardHeight)
        .aetherCardSurface(shape = shape, accent = accent)
        .drawBehind {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.045f + glow * 0.20f),
                        palette.accentSoft.copy(alpha = 0.025f),
                        Color.Transparent,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                color = accent.copy(alpha = glow * 0.45f),
                radius = size.minDimension * 0.46f,
                center = Offset(size.width * 1.02f, size.height * 0.03f),
            )
        }

    if (wide) {
        Row(
            modifier = surfaceModifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.14f + glow * 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (clampedProgress != null) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = barColor,
                        trackColor = accent.copy(alpha = 0.13f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = surfaceModifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(29.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f + glow * 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (clampedProgress != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = barColor,
                    trackColor = accent.copy(alpha = 0.13f),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SystemDetailsCard(
    nowElapsed: Long,
    glow: Float,
) {
    val awakeUptime = SystemClock.uptimeMillis().coerceAtLeast(0L)
    val elapsed = maxOf(nowElapsed, SystemClock.elapsedRealtime())
    val deepSleep = (elapsed - awakeUptime).coerceAtLeast(0L)
    val shape = RoundedCornerShape(24.dp)
    val palette = LocalAetherPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .aetherCardSurface(shape = shape)
            .drawBehind {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            palette.accent.copy(alpha = 0.045f + glow * 0.20f),
                            Color.Transparent,
                            palette.accentSoft.copy(alpha = 0.035f),
                        ),
                    ),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SystemDetailValue(
            modifier = Modifier.weight(1f),
            title = str(R.string.dashboard_overview_uptime),
            value = formatDuration(awakeUptime),
            detail = str(R.string.dashboard_overview_uptime_detail),
            icon = Icons.Outlined.Schedule,
            accent = palette.accent,
        )
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 52.dp)
                .background(palette.stroke.copy(alpha = 0.6f)),
        )
        SystemDetailValue(
            modifier = Modifier.weight(1f),
            title = str(R.string.dashboard_overview_deep_sleep),
            value = formatDuration(deepSleep),
            detail = str(R.string.dashboard_overview_deep_sleep_detail),
            icon = Icons.Outlined.Bedtime,
            accent = Color(0xFF8C9EFF),
        )
    }
}

@Composable
private fun SystemDetailValue(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(33.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AndroidVersionCard(
    info: DeviceInfoSnapshot?,
    glow: Float,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAetherPalette.current
    val api = info?.sdkInt
    val versionAccent = when (api) {
        37 -> Color(0xFFE546A7) // Android 17 — cinnamon-bun planet / star field
        36 -> Color(0xFF78A6FF) // Android 16 — blue space badge
        35 -> Color(0xFF43B76C) // Android 15 — green triangular launch badge
        34 -> Color(0xFFFF794E) // Android 14 — orange circular mission patch
        33 -> Color(0xFFE15A62) // Android 13 — red and teal clock hands
        31, 32 -> Color(0xFF8C83F6) // Android 12 — Material You clock
        30 -> Color(0xFFFFB74D) // Android 11 — cat easter egg
        else -> palette.accent
    }
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .aetherCardSurface(shape = shape, accent = versionAccent)
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            versionAccent.copy(alpha = 0.085f + glow * 0.28f),
                            palette.accentSoft.copy(alpha = 0.045f),
                            Color.Transparent,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                drawCircle(
                    color = versionAccent.copy(alpha = glow * 0.62f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 1.02f, size.height * 0.08f),
                )
            }
            .padding(horizontal = 11.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = str(R.string.dashboard_overview_android_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = versionAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AndroidVersionArtwork(
            sdkInt = api ?: Build.VERSION.SDK_INT,
            phase = phase,
            accent = versionAccent,
            dark = palette.isDark,
            modifier = Modifier.size(width = 112.dp, height = 104.dp),
        )
        Text(
            text = info?.let { str(R.string.dashboard_overview_android_version_format, it.androidVersion) }
                ?: str(R.string.dashboard_overview_android_unknown),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = info?.let { "${it.manufacturer} ${it.model}".trim() }
                ?.takeIf { it.isNotBlank() }
                ?: str(R.string.dashboard_overview_device_name_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = if (api != null) str(R.string.dashboard_overview_api_core_format, api, info?.cpuCores ?: 0)
                else str(R.string.dashboard_overview_android_api_unknown),
            style = MaterialTheme.typography.labelMedium,
            color = versionAccent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceHardwareCard(
    info: DeviceInfoSnapshot?,
    glow: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAetherPalette.current
    val accent = palette.accent
    val shape = RoundedCornerShape(25.dp)
    val socName = info?.let { device -> device.socModel.ifBlank { device.hardware } }
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?: str(R.string.dashboard_overview_unavailable)
    val cpuCores = info?.cpuCores?.takeIf { it > 0 }?.toString()
        ?: str(R.string.dashboard_overview_unavailable)
    val refreshRate = info?.refreshRateHz?.takeIf { it > 0f }
        ?.let { String.format(Locale.getDefault(), "%.0f Hz", it) }
        ?: str(R.string.dashboard_overview_unavailable)
    val abi = info?.cpuAbi?.takeIf { it.isNotBlank() }
        ?: str(R.string.dashboard_overview_unavailable)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .aetherCardSurface(shape = shape, accent = accent)
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.055f + glow * 0.22f),
                            palette.accentSoft.copy(alpha = 0.035f),
                            Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
            .padding(horizontal = 11.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.15f + glow * 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(
                text = str(R.string.dashboard_overview_hardware_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HardwareInfoValue(
            icon = Icons.Outlined.DeveloperBoard,
            label = str(R.string.dashboard_overview_hardware_soc),
            value = socName,
            accent = accent,
        )
        HardwareInfoValue(
            icon = Icons.Outlined.Memory,
            label = str(R.string.dashboard_overview_hardware_cores),
            value = cpuCores,
            accent = accent,
        )
        HardwareInfoValue(
            icon = Icons.Outlined.Speed,
            label = str(R.string.dashboard_overview_hardware_refresh_rate),
            value = refreshRate,
            accent = accent,
        )
        HardwareInfoValue(
            icon = Icons.Outlined.PhoneAndroid,
            label = str(R.string.dashboard_overview_hardware_abi),
            value = abi,
            accent = accent,
        )
        HardwareInfoValue(
            icon = Icons.Outlined.Settings,
            label = str(R.string.dashboard_overview_hardware_kernel),
            value = info?.kernelVersion?.takeIf { it.isNotBlank() }
                ?: str(R.string.dashboard_overview_unavailable),
            accent = accent,
        )
    }
}

@Composable
private fun HardwareInfoValue(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.92f), modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Version-specific Android Settings Easter-egg artwork. These are compact vector
 * illustrations of the actual per-release motifs, not a generic Bugdroid logo.
 * The artwork switches from the Android 12/13 clock to the space badges for 14–17.
 */
@Composable
private fun AndroidVersionArtwork(
    sdkInt: Int,
    phase: Float,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(17.dp))) {
        drawVersionEggArtwork(
            sdkInt = sdkInt,
            phase = phase,
            accent = accent,
            dark = dark,
        )
    }
}

private fun DrawScope.drawVersionEggArtwork(
    sdkInt: Int,
    phase: Float,
    accent: Color,
    dark: Boolean,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.51f
    val short = minOf(w, h)
    val progress = phase.coerceIn(0f, 1f)
    val space = Color(0xFF070B17)

    fun text(value: String, x: Float, y: Float, px: Float, color: Color, bold: Boolean = false) {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = px
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)
    }

    fun drawStars(count: Int, color: Color = Color.White) {
        // Deterministic star locations; avoids random allocations during every animation frame.
        val points = arrayOf(
            0.12f to 0.20f, 0.24f to 0.11f, 0.38f to 0.19f, 0.67f to 0.13f,
            0.84f to 0.22f, 0.16f to 0.41f, 0.79f to 0.42f, 0.29f to 0.56f,
            0.72f to 0.58f, 0.13f to 0.73f, 0.35f to 0.83f, 0.79f to 0.78f,
            0.62f to 0.88f, 0.91f to 0.59f, 0.52f to 0.27f, 0.91f to 0.35f,
        )
        repeat(count.coerceAtMost(points.size)) { i ->
            val (xf, yf) = points[i]
            val twinkle = 0.48f + 0.45f * (kotlin.math.sin((progress * 6.28318f + i * 1.31f).toDouble()).toFloat() + 1f) * 0.5f
            val radius = if (i % 4 == 0) 1.35f else 0.80f
            drawCircle(
                color = color.copy(alpha = twinkle * (if (dark) 0.86f else 0.72f)),
                radius = radius.dp.toPx(),
                center = Offset(w * xf, h * yf),
            )
        }
    }

    when (sdkInt) {
        37 -> {
            // Android 17: magenta rosette, green cinnamon-bun planet and star points.
            drawRect(color = space)
            drawStars(16)
            val center = Offset(cx, h * 0.52f)
            val outerR = short * 0.395f
            val rosette = Path().apply {
                val points = 40
                for (i in 0 until points) {
                    val a = ((i.toDouble() / points.toDouble() * 360.0) - 90.0) * (Math.PI / 180.0)
                    val r = outerR * if (i % 2 == 0) 1f else 0.87f
                    val x = center.x + kotlin.math.cos(a).toFloat() * r
                    val y = center.y + kotlin.math.sin(a).toFloat() * r
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(rosette, Color(0xFFB8208E))
            drawCircle(Color(0xFF253C49), radius = outerR * 0.70f, center = center)
            drawCircle(Color(0xFF49D09B), radius = outerR * 0.48f, center = center)
            // Cinnamon-bun / planet swirl, animated very subtly to echo the live system.
            val swirl = Path().apply {
                moveTo(center.x - outerR * 0.34f, center.y + outerR * 0.16f)
                cubicTo(center.x - outerR * 0.51f, center.y - outerR * 0.25f,
                    center.x + outerR * 0.05f, center.y - outerR * 0.42f,
                    center.x + outerR * 0.27f, center.y - outerR * 0.13f)
                cubicTo(center.x + outerR * 0.48f, center.y + outerR * 0.17f,
                    center.x + outerR * 0.01f, center.y + outerR * 0.31f,
                    center.x - outerR * 0.12f, center.y + outerR * 0.08f)
                cubicTo(center.x - outerR * 0.23f, center.y - outerR * 0.12f,
                    center.x + outerR * 0.17f, center.y - outerR * 0.24f,
                    center.x + outerR * 0.18f, center.y + outerR * 0.01f)
            }
            drawPath(swirl, Color(0xFF123D36), style = Stroke(width = short * 0.055f, cap = StrokeCap.Round))
            text("A N D R O I D", cx, h * 0.105f, short * 0.087f, Color.White, true)
            text("17", cx, h * 0.965f, short * 0.18f, Color.White, true)
        }
        36 -> {
            // Android 16: blue rounded badge, emerald faceted planet and lime orbit.
            drawRect(color = space)
            drawStars(15)
            drawRoundRect(
                color = Color(0xFF4285F4),
                topLeft = Offset(w * 0.055f, h * 0.035f),
                size = Size(w * 0.89f, h * 0.93f),
                cornerRadius = CornerRadius(short * 0.14f),
            )
            drawRoundRect(
                color = Color(0xFF141C28),
                topLeft = Offset(w * 0.13f, h * 0.20f),
                size = Size(w * 0.74f, h * 0.64f),
                cornerRadius = CornerRadius(short * 0.08f),
            )
            val diamond = Path().apply {
                moveTo(cx, h * 0.31f)
                lineTo(w * 0.76f, h * 0.52f)
                lineTo(cx, h * 0.73f)
                lineTo(w * 0.24f, h * 0.52f)
                close()
            }
            drawPath(diamond, Color(0xFF239A55))
            drawPath(Path().apply { moveTo(cx, h * 0.31f); lineTo(w * 0.76f, h * 0.52f); lineTo(cx, h * 0.52f); close() }, Color(0xFF52D273))
            drawPath(Path().apply { moveTo(cx, h * 0.31f); lineTo(cx, h * 0.52f); lineTo(w * 0.24f, h * 0.52f); close() }, Color(0xFF35B964))
            drawPath(Path().apply { moveTo(w * 0.24f, h * 0.52f); lineTo(cx, h * 0.52f); lineTo(cx, h * 0.73f); close() }, Color(0xFF168348))
            val orbit = Path().apply {
                moveTo(w * 0.21f, h * 0.72f)
                cubicTo(w * 0.32f, h * 0.88f, w * 0.60f, h * 0.85f, w * 0.79f, h * 0.33f)
            }
            drawPath(orbit, Color(0xFFB4F52B), style = Stroke(width = short * 0.045f, cap = StrokeCap.Round))
            text("ANDROID", cx, h * 0.145f, short * 0.105f, Color.White, true)
            text("16", w * 0.75f, h * 0.80f, short * 0.18f, Color.White, true)
        }
        35 -> {
            // Android 15: green, rounded triangle mission patch and descending launch trail.
            drawRect(color = space)
            drawStars(15)
            val outer = Path().apply {
                moveTo(w * 0.15f, h * 0.17f)
                quadraticTo(w * 0.50f, h * 0.105f, w * 0.85f, h * 0.17f)
                lineTo(w * 0.58f, h * 0.88f)
                quadraticTo(w * 0.50f, h * 0.98f, w * 0.42f, h * 0.88f)
                close()
            }
            drawPath(outer, Color(0xFF34A853))
            val inner = Path().apply {
                moveTo(w * 0.22f, h * 0.23f)
                lineTo(w * 0.78f, h * 0.23f)
                lineTo(w * 0.56f, h * 0.79f)
                quadraticTo(w * 0.50f, h * 0.86f, w * 0.44f, h * 0.79f)
                close()
            }
            drawPath(inner, Color(0xFF07121B))
            drawCircle(Color.White.copy(alpha = 0.94f), short * 0.11f, Offset(cx, h * 0.34f))
            drawPath(Path().apply { moveTo(cx, h * 0.27f); lineTo(w * 0.58f, h * 0.65f); lineTo(cx, h * 0.75f); lineTo(w * 0.42f, h * 0.65f); close() }, Color(0xFF9EF52D))
            drawRoundRect(Color(0xFF747B86), Offset(w * 0.37f, h * 0.62f), Size(w * 0.26f, h * 0.19f), CornerRadius(short * 0.05f))
            text("ANDROID", cx, h * 0.205f, short * 0.087f, Color.White, true)
            text("15", cx, h * 0.755f, short * 0.16f, Color.White, true)
        }
        34 -> {
            // Android 14: orange Apollo-style circular mission patch.
            drawRect(color = space)
            drawStars(15)
            drawCircle(Color(0xFFFF7547), radius = short * 0.445f, center = Offset(cx, cy), style = Stroke(width = short * 0.12f))
            drawCircle(Color(0xFF071321), radius = short * 0.385f, center = Offset(cx, cy))
            drawArc(Color.White, 180f, 180f, true, Offset(w * 0.31f, h * 0.24f), Size(w * 0.38f, h * 0.22f))
            drawLine(Color.White, Offset(w * 0.39f, h * 0.36f), Offset(w * 0.45f, h * 0.59f), short * 0.025f, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.61f, h * 0.36f), Offset(w * 0.55f, h * 0.59f), short * 0.025f, StrokeCap.Round)
            val rocket = Path().apply {
                moveTo(cx, h * 0.30f)
                cubicTo(w * 0.56f, h * 0.40f, w * 0.55f, h * 0.54f, cx, h * 0.70f)
                cubicTo(w * 0.45f, h * 0.54f, w * 0.44f, h * 0.40f, cx, h * 0.30f)
                close()
            }
            drawPath(rocket, Color(0xFF52D273))
            drawCircle(Color(0xFFB4F52B), short * 0.035f, Offset(cx, h * 0.48f))
            text("ANDROID 14", cx, h * 0.155f, short * 0.078f, Color.White, true)
        }
        33 -> {
            // Android 13's Settings Easter egg is a clock, with a scalloped pale dial and red/teal hands.
            drawRect(color = if (dark) Color(0xFF151923) else Color(0xFFEBEDF2))
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF424957).copy(alpha = 0.34f), Color.Transparent)),
                radius = short * 0.62f,
                center = Offset(cx, cy),
            )
            val dial = Path().apply {
                val points = 48
                val r = short * 0.365f
                for (i in 0 until points) {
                    val a = ((i.toDouble() / points.toDouble() * 360.0) - 90.0) * (Math.PI / 180.0)
                    val factor = if (i % 2 == 0) 1f else 0.94f
                    val x = cx + kotlin.math.cos(a).toFloat() * r * factor
                    val y = cy + kotlin.math.sin(a).toFloat() * r * factor
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(dial, Color(0xFFD2D5DC))
            drawCircle(Color(0xFFE6E8ED), short * 0.305f, Offset(cx, cy))
            drawLine(Color(0xFFE54850), Offset(cx, cy), Offset(cx, h * 0.225f), short * 0.045f, StrokeCap.Round)
            drawLine(Color(0xFF547C91), Offset(cx, cy), Offset(w * 0.69f, h * 0.40f), short * 0.040f, StrokeCap.Round)
            drawArc(Color(0xFFE54850), -90f, 48f, false, Offset(w * 0.31f, h * 0.30f), Size(w * 0.38f, h * 0.38f), style = Stroke(width = short * 0.024f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFE54850), short * 0.04f, Offset(cx, cy))
        }
        31, 32 -> {
            // Android 12 / 12L: Material You clock surrounded by a lively ring of colored circles.
            drawRect(color = Color(0xFF17152A))
            val colors = listOf(Color(0xFF8E80E8), Color(0xFFFFC857), Color(0xFF68D7D0), Color(0xFFF58EAE), Color(0xFFB4E66E), Color(0xFF7EA9FF))
            val orbitR = short * 0.38f
            repeat(12) { i ->
                val a = ((i.toDouble() / 12.0 * 360.0) - 90.0) * (Math.PI / 180.0)
                val pos = Offset(cx + kotlin.math.cos(a).toFloat() * orbitR, cy + kotlin.math.sin(a).toFloat() * orbitR)
                drawCircle(colors[i % colors.size].copy(alpha = 0.92f), short * if (i % 3 == 0) 0.075f else 0.060f, pos)
            }
            drawCircle(Color(0xFFB8B4D4), short * 0.265f, Offset(cx, cy))
            drawLine(Color(0xFF5D5B73), Offset(cx, cy), Offset(cx, h * 0.28f), short * 0.045f, StrokeCap.Round)
            drawLine(Color(0xFF5D5B73), Offset(cx, cy), Offset(w * 0.68f, h * 0.51f), short * 0.045f, StrokeCap.Round)
            drawCircle(Color(0xFF5D5B73), short * 0.036f, Offset(cx, cy))
        }
        30 -> {
            // Android 11: minimal cat-themed dish / head motif from the Neko easter egg.
            drawRect(color = Color(0xFF181923))
            drawCircle(Color(0xFF343748), short * 0.39f, Offset(cx, cy))
            drawArc(Color(0xFFFFB74D), 180f, 180f, false, Offset(w * 0.25f, h * 0.27f), Size(w * 0.50f, h * 0.50f), style = Stroke(width = short * 0.035f, cap = StrokeCap.Round))
            val cat = Path().apply {
                moveTo(w * 0.32f, h * 0.43f); lineTo(w * 0.34f, h * 0.26f); lineTo(w * 0.46f, h * 0.36f)
                quadraticTo(cx, h * 0.31f, w * 0.56f, h * 0.36f); lineTo(w * 0.68f, h * 0.26f); lineTo(w * 0.70f, h * 0.43f)
                cubicTo(w * 0.78f, h * 0.70f, w * 0.22f, h * 0.70f, w * 0.32f, h * 0.43f); close()
            }
            drawPath(cat, Color(0xFFE9C59D))
            drawCircle(ink, short * 0.022f, Offset(w * 0.43f, h * 0.48f))
            drawCircle(ink, short * 0.022f, Offset(w * 0.57f, h * 0.48f))
            drawLine(ink, Offset(w * 0.47f, h * 0.56f), Offset(cx, h * 0.59f), short * 0.012f, StrokeCap.Round)
            drawLine(ink, Offset(cx, h * 0.59f), Offset(w * 0.53f, h * 0.56f), short * 0.012f, StrokeCap.Round)
            text("11", cx, h * 0.94f, short * 0.13f, Color.White, true)
        }
        else -> {
            // Older Android releases get a restrained version badge rather than a generic robot.
            val hue = when (sdkInt) {
                in 26..29 -> Color(0xFF5B8DEF)
                in 23..25 -> Color(0xFF73C9B8)
                else -> accent
            }
            drawRect(color = if (dark) Color(0xFF111520) else Color(0xFFE9EDF5))
            drawCircle(hue.copy(alpha = 0.18f), short * 0.47f, Offset(cx, cy))
            drawCircle(hue, short * 0.36f, Offset(cx, cy), style = Stroke(width = short * 0.06f))
            text(sdkInt.toString(), cx, cy + short * 0.11f, short * 0.30f, if (dark) Color.White else Color(0xFF202434), true)
            text("ANDROID", cx, h * 0.91f, short * 0.09f, hue, true)
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes / 60L) % 24L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L -> str(R.string.dashboard_overview_duration_days_hours, days.toInt(), hours.toInt())
        hours > 0L -> str(R.string.dashboard_overview_duration_hours_minutes, hours.toInt(), minutes.toInt())
        else -> str(R.string.dashboard_overview_duration_minutes, minutes.toInt())
    }
}
