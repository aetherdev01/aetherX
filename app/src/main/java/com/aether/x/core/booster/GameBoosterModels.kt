package com.aether.x.core.booster

import com.aether.x.data.GameMode

data class GameBoosterMetrics(
    val fps: Int? = null,
    val cpuLoadPercent: Int? = null,
    val gpuLoadPercent: Int? = null,
    val temperatureCelsius: Float? = null,

    val ramLoadPercent: Int? = null,

    val fpsHistory: List<Int> = emptyList(),
)

data class RecentAppEntry(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
)

data class GameBoosterSession(
    val packageName: String,
    val gameLabel: String,
    val mode: GameMode,
    val dndEnabled: Boolean,
    val fpsOverlayEnabled: Boolean,
    val metrics: GameBoosterMetrics = GameBoosterMetrics(),

    val icon: androidx.compose.ui.graphics.ImageBitmap? = null,

    val showingSplash: Boolean = false,

    val rotationLocked: Boolean = false,
    val touchBoostEnabled: Boolean = false,

    val recentApps: List<RecentAppEntry> = emptyList(),
)
