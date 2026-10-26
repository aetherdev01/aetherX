package com.aether.x.ui.dashboard

import android.os.SystemClock
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

private val AndroidRobotGreen = Color(0xFF3DDC84)
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

        // The six live metric tiles use a stable two-column 2x2 card rhythm.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = str(R.string.dashboard_overview_battery_temperature),
                value = batteryTemperature?.let { String.format(Locale.getDefault(), "%.1f°C", it) }
                    ?: str(R.string.dashboard_overview_unavailable),
                detail = str(R.string.dashboard_overview_battery_temperature_detail),
                icon = Icons.Outlined.BatteryChargingFull,
                accent = if (batteryTemperature != null && batteryTemperature >= 42f) MetricAmber else LocalAetherPalette.current.accent,
                progress = batteryTemperature?.div(60f)?.coerceIn(0f, 1f),
                glow = glow,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                title = str(R.string.dashboard_overview_charging),
                value = str(if (liveInfo?.isCharging == true) R.string.dashboard_overview_charging_active else R.string.dashboard_overview_charging_inactive),
                detail = str(R.string.dashboard_overview_charging_detail),
                icon = Icons.Outlined.BatteryChargingFull,
                accent = if (liveInfo?.isCharging == true) MetricGreen else LocalAetherPalette.current.accent,
                glow = glow,
                modifier = Modifier.weight(1f),
            )
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
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                title = str(R.string.dashboard_overview_ram),
                value = ramFraction?.let { "${(it * 100f).toInt()}%" } ?: str(R.string.dashboard_overview_unavailable),
                detail = liveInfo?.let { "${it.usedRamBytes.toGbLabel()} / ${it.totalRamBytes.toGbLabel()}" }
                    ?: str(R.string.dashboard_overview_usage_unavailable),
                icon = Icons.Outlined.Memory,
                accent = when {
                    ramFraction == null -> LocalAetherPalette.current.accent
                    ramFraction >= 0.88f -> MetricRed
                    ramFraction >= 0.72f -> MetricAmber
                    else -> LocalAetherPalette.current.accent
                },
                progress = ramFraction,
                glow = glow,
                modifier = Modifier.weight(1f),
            )
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
                modifier = Modifier.weight(1f),
            )
        }

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
) {
    val palette = LocalAetherPalette.current
    val shape = RoundedCornerShape(22.dp)
    val clampedProgress = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress ?: 0f,
        animationSpec = tween(durationMillis = 650),
        label = "dashboardMetricProgress",
    )
    Column(
        modifier = modifier
            .height(132.dp)
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
            .padding(horizontal = 12.dp, vertical = 11.dp),
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
            val barColor = if (invertProgress && clampedProgress <= 0.15f) MetricRed else accent
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
    val versionAccent = when (info?.sdkInt) {
        37 -> Color(0xFF3DDC84) // Android 17
        36 -> Color(0xFF8C9EFF) // Android 16
        35 -> Color(0xFF64B5F6) // Android 15
        34 -> Color(0xFFFFB74D) // Android 14
        else -> palette.accent
    }
    val api = info?.sdkInt
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(246.dp)
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
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            versionAccent.copy(alpha = 0.16f + glow * 0.4f),
                            versionAccent.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.40f
                drawCircle(
                    color = versionAccent.copy(alpha = 0.16f + glow * 0.6f),
                    radius = radius,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
                drawArc(
                    color = versionAccent.copy(alpha = 0.8f),
                    startAngle = phase * 57.2958f,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
                    size = Size(size.width * 0.80f, size.height * 0.80f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            AndroidRobot(modifier = Modifier.size(62.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
                    .clip(CircleShape)
                    .background(versionAccent)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = info?.let { device -> device.androidVersion.ifBlank { device.sdkInt.toString() } } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111820),
                    maxLines = 1,
                )
            }
        }
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

/** Android's recognizable green robot mark, drawn locally so the dashboard needs no image download. */
@Composable
private fun AndroidRobot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val green = AndroidRobotGreen
        val stroke = unit * 0.055f
        val centerX = size.width / 2f
        // Antennae
        drawLine(green, Offset(centerX - unit * 0.18f, unit * 0.19f), Offset(centerX - unit * 0.30f, unit * 0.035f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(green, Offset(centerX + unit * 0.18f, unit * 0.19f), Offset(centerX + unit * 0.30f, unit * 0.035f), strokeWidth = stroke, cap = StrokeCap.Round)
        // Dome head, flattened at the bottom like the official robot wordmark.
        val headLeft = centerX - unit * 0.34f
        val headTop = unit * 0.13f
        val headWidth = unit * 0.68f
        val headHeight = unit * 0.34f
        drawArc(
            color = green,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
        )
        drawRect(
            color = green,
            topLeft = Offset(headLeft, headTop + headHeight * 0.49f),
            size = Size(headWidth, headHeight * 0.51f),
        )
        // Eyes
        drawCircle(Color.White, radius = unit * 0.035f, center = Offset(centerX - unit * 0.16f, unit * 0.31f))
        drawCircle(Color.White, radius = unit * 0.035f, center = Offset(centerX + unit * 0.16f, unit * 0.31f))
        // Body, arms and legs
        drawRoundRect(
            color = green,
            topLeft = Offset(centerX - unit * 0.29f, unit * 0.49f),
            size = Size(unit * 0.58f, unit * 0.27f),
            cornerRadius = CornerRadius(unit * 0.055f, unit * 0.055f),
        )
        drawLine(green, Offset(centerX - unit * 0.38f, unit * 0.53f), Offset(centerX - unit * 0.38f, unit * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(green, Offset(centerX + unit * 0.38f, unit * 0.53f), Offset(centerX + unit * 0.38f, unit * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(green, Offset(centerX - unit * 0.15f, unit * 0.77f), Offset(centerX - unit * 0.15f, unit * 0.94f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(green, Offset(centerX + unit * 0.15f, unit * 0.77f), Offset(centerX + unit * 0.15f, unit * 0.94f), strokeWidth = stroke, cap = StrokeCap.Round)
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
