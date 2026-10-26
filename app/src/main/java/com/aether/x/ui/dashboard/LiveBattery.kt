package com.aether.x.ui.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Battery state pushed by the system (no polling delay) — used by the Dashboard battery cards. */
data class LiveBattery(
    val percent: Int,
    val tempC: Float?,
    val charging: Boolean,
    val currentUa: Long?,
    val voltageMv: Int?,
) {
    val powerW: Float?
        get() = if (currentUa != null && voltageMv != null) {
            (currentUa.toDouble() * voltageMv.toDouble() / 1_000_000_000.0).toFloat()
                .takeIf { it.isFinite() && it > 0f }
        } else null
}

private fun readLiveBattery(context: Context, forceCharging: Boolean? = null): LiveBattery? {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
    val tenths = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    // "Plugged" flips instantly on connect, while STATUS_CHARGING can lag behind it.
    val charging = forceCharging ?: (plugged != 0 &&
        status != BatteryManager.BATTERY_STATUS_FULL &&
        status != BatteryManager.BATTERY_STATUS_NOT_CHARGING)
    val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
    val current = if (charging) {
        runCatching {
            (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
            ?.let { abs(it.toLong()) }
    } else null
    return LiveBattery(
        percent = percent,
        tempC = if (tenths != Int.MIN_VALUE) tenths / 10f else null,
        charging = charging,
        currentUa = current,
        voltageMv = voltage,
    )
}

@Composable
fun rememberLiveBattery(): State<LiveBattery?> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(readLiveBattery(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val forced = when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> true
                    Intent.ACTION_POWER_DISCONNECTED -> false
                    else -> null
                }
                state.value = readLiveBattery(context, forced) ?: state.value
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    // Current / power change continuously while charging, so refresh them every 1.5 s.
    val charging = state.value?.charging == true
    LaunchedEffect(charging) {
        while (charging) {
            delay(1_500L)
            state.value = readLiveBattery(context) ?: state.value
        }
    }
    return state
}
