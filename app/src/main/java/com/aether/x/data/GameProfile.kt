package com.aether.x.data

import org.json.JSONObject

enum class GameMode {
    LOW,
    MID,
    BOOST,
}

data class GameProfile(
    val packageName: String,
    val gameMode: GameMode = GameMode.MID,
    val cpuPerformanceMode: Boolean = false,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val vmHeapBoost: Boolean = false,
    val gpuRenderingPriority: Boolean = false,
) {

    val hasAnyTweakEnabled: Boolean
        get() = cpuPerformanceMode || ramPriorityMode || thermalThrottleOverride ||
            gpuPerformanceMode || ioSchedulerBoost || vmHeapBoost || gpuRenderingPriority

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PACKAGE, packageName)
        put(KEY_GAME_MODE, gameMode.name)
        put(KEY_CPU, cpuPerformanceMode)
        put(KEY_RAM, ramPriorityMode)
        put(KEY_THERMAL, thermalThrottleOverride)
        put(KEY_GPU, gpuPerformanceMode)
        put(KEY_IO, ioSchedulerBoost)
        put(KEY_VM_HEAP, vmHeapBoost)
        put(KEY_GPU_RENDER_PRIORITY, gpuRenderingPriority)
    }

    companion object {
        private const val KEY_PACKAGE = "packageName"
        private const val KEY_GAME_MODE = "gameMode"
        private const val KEY_CPU = "cpuPerformanceMode"
        private const val KEY_RAM = "ramPriorityMode"
        private const val KEY_THERMAL = "thermalThrottleOverride"
        private const val KEY_GPU = "gpuPerformanceMode"
        private const val KEY_IO = "ioSchedulerBoost"
        private const val KEY_VM_HEAP = "vmHeapBoost"
        private const val KEY_GPU_RENDER_PRIORITY = "gpuRenderingPriority"

        fun default(packageName: String) = GameProfile(packageName = packageName)

        fun withGameMode(base: GameProfile, mode: GameMode): GameProfile = when (mode) {
            GameMode.LOW -> base.copy(
                gameMode = mode,
                cpuPerformanceMode = false,
                ramPriorityMode = false,
                thermalThrottleOverride = false,
                gpuPerformanceMode = false,
                ioSchedulerBoost = false,
                vmHeapBoost = false,
                gpuRenderingPriority = false,
            )
            GameMode.MID -> base.copy(
                gameMode = mode,
                cpuPerformanceMode = true,
                ramPriorityMode = true,
                thermalThrottleOverride = false,
                gpuPerformanceMode = true,
                ioSchedulerBoost = false,
                vmHeapBoost = false,
                gpuRenderingPriority = false,
            )
            GameMode.BOOST -> base.copy(
                gameMode = mode,
                cpuPerformanceMode = true,
                ramPriorityMode = true,
                thermalThrottleOverride = true,
                gpuPerformanceMode = true,
                ioSchedulerBoost = true,
                vmHeapBoost = true,
                gpuRenderingPriority = true,
            )
        }

        fun fromJson(json: JSONObject): GameProfile = GameProfile(
            packageName = json.optString(KEY_PACKAGE),
            gameMode = runCatching { GameMode.valueOf(json.optString(KEY_GAME_MODE)) }.getOrDefault(GameMode.MID),
            cpuPerformanceMode = json.optBoolean(KEY_CPU, false),
            ramPriorityMode = json.optBoolean(KEY_RAM, false),
            thermalThrottleOverride = json.optBoolean(KEY_THERMAL, false),
            gpuPerformanceMode = json.optBoolean(KEY_GPU, false),
            ioSchedulerBoost = json.optBoolean(KEY_IO, false),
            vmHeapBoost = json.optBoolean(KEY_VM_HEAP, false),
            gpuRenderingPriority = json.optBoolean(KEY_GPU_RENDER_PRIORITY, false),
        )
    }
}

object GameProfileSerializer {

    fun serialize(profiles: Map<String, GameProfile>): String {
        val root = JSONObject()
        profiles.forEach { (pkg, profile) -> root.put(pkg, profile.toJson()) }
        return root.toString()
    }

    fun deserialize(raw: String?): Map<String, GameProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { pkg ->
                GameProfile.fromJson(root.getJSONObject(pkg))
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }
}
