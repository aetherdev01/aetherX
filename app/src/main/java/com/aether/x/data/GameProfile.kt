package com.aether.x.data

import org.json.JSONObject

/**
 * Kumpulan tweak KHUSUS ROOT untuk satu game tertentu — field-nya sengaja
 * identik dengan section "Root" global di layar Tweak (lihat
 * [TweakRepository]/`TweakScreen.kt`), tapi nilainya disimpan TERPISAH per
 * package name. Ini yang memungkinkan mis. Genshin Impact punya CPU
 * Performance Mode ON sementara game lain OFF, tanpa saling menimpa.
 *
 * Field ini SENGAJA tidak menyertakan tweak Input Driver (pointer speed,
 * touch boost) — section itu khusus non-root dan tidak relevan untuk profil
 * game yang khusus Root.
 */
data class GameProfile(
    val packageName: String,
    val cpuPerformanceMode: Boolean = false,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val vmHeapBoost: Boolean = false,
) {
    /** Apakah profil ini punya minimal satu tweak yang diaktifkan. */
    val hasAnyTweakEnabled: Boolean
        get() = cpuPerformanceMode || ramPriorityMode || thermalThrottleOverride ||
            gpuPerformanceMode || ioSchedulerBoost || vmHeapBoost

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PACKAGE, packageName)
        put(KEY_CPU, cpuPerformanceMode)
        put(KEY_RAM, ramPriorityMode)
        put(KEY_THERMAL, thermalThrottleOverride)
        put(KEY_GPU, gpuPerformanceMode)
        put(KEY_IO, ioSchedulerBoost)
        put(KEY_VM_HEAP, vmHeapBoost)
    }

    companion object {
        private const val KEY_PACKAGE = "packageName"
        private const val KEY_CPU = "cpuPerformanceMode"
        private const val KEY_RAM = "ramPriorityMode"
        private const val KEY_THERMAL = "thermalThrottleOverride"
        private const val KEY_GPU = "gpuPerformanceMode"
        private const val KEY_IO = "ioSchedulerBoost"
        private const val KEY_VM_HEAP = "vmHeapBoost"

        fun default(packageName: String) = GameProfile(packageName = packageName)

        fun fromJson(json: JSONObject): GameProfile = GameProfile(
            packageName = json.optString(KEY_PACKAGE),
            cpuPerformanceMode = json.optBoolean(KEY_CPU, false),
            ramPriorityMode = json.optBoolean(KEY_RAM, false),
            thermalThrottleOverride = json.optBoolean(KEY_THERMAL, false),
            gpuPerformanceMode = json.optBoolean(KEY_GPU, false),
            ioSchedulerBoost = json.optBoolean(KEY_IO, false),
            vmHeapBoost = json.optBoolean(KEY_VM_HEAP, false),
        )
    }
}

/**
 * Serialisasi/deserialisasi map `packageName -> GameProfile` ke satu string
 * JSON tunggal, supaya bisa disimpan sebagai satu key DataStore ([AetherXPreferences])
 * alih-alih satu key per package (yang tidak praktis untuk data dinamis
 * seperti ini — jumlah game bisa berapa saja).
 */
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
