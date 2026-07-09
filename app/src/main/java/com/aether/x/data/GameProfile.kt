package com.aether.x.data

import org.json.JSONObject

/**
 * Preset Game Mode (FITUR BARU — lihat perintah rework: "tambahkan fitur
 * baru Game Mode : Low, Mid, Boost") — HANYA berfungsi sebagai TITIK AWAL
 * yang otomatis mengisi kombinasi ke-6 toggle tweak di [GameProfile], BUKAN
 * kunci permanen: pengguna tetap bisa mengubah tiap toggle secara manual
 * SETELAH memilih mode, dan itu tidak akan "membatalkan" pilihan mode-nya
 * (mode disimpan apa adanya sebagai preferensi terakhir yang dipilih,
 * murni informasi UI, bukan validasi ulang dari toggle yang aktif).
 *
 * - [LOW]: semua tweak performa OFF — prioritaskan hemat baterai/panas,
 *   untuk sesi main santai/battery saver.
 * - [MID]: default seimbang — CPU & GPU Performance Mode ON, sisanya OFF.
 * - [BOOST]: semua 6 tweak ON — performa maksimal untuk game berat/kompetitif.
 */
enum class GameMode {
    LOW,
    MID,
    BOOST,
}

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
 *
 * [gameMode] (FITUR BARU): preset Low/Mid/Boost yang TERAKHIR dipilih
 * pengguna untuk profil ini — lihat KDoc [GameMode] untuk kombinasi
 * masing-masing preset. Default [GameMode.MID] konsisten dengan nilai
 * default ke-6 toggle di bawah (semua false, sama seperti kombinasi MID
 * MINUS cpu/gpu performance — lihat [GameMode.applyTo] untuk kombinasi
 * pasti tiap preset).
 *
 * [gpuRenderingPriority] (FITUR BARU — tweak ke-7, lihat perintah rework:
 * "Untuk tweak baru di Game Profile — GPU Rendering Priority
 * (SurfaceFlinger)"): menaikkan prioritas real-time proses `surfaceflinger`
 * (compositor sistem yang menggabungkan seluruh layer render jadi satu
 * frame ke layar) DAN thread render game itu sendiri (`RenderThread`),
 * lewat `chrt` — mengurangi jitter/frame drop akibat proses lain merebut
 * jatah CPU tepat saat SurfaceFlinger/RenderThread perlu jalan, TERUTAMA
 * terasa di device dengan CPU governor non-performance atau saat banyak
 * app background aktif. Lihat [com.aether.x.data.TweakRepository.applyGpuRenderingPriority]
 * untuk implementasi shell-nya.
 */
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
    /** Apakah profil ini punya minimal satu tweak yang diaktifkan. */
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

        /**
         * Terapkan preset [mode] ke [base] — mengganti ke-6 toggle sesuai
         * kombinasi mode, packageName dipertahankan dari [base]. Dipanggil
         * dari GameProfileViewModel saat pengguna memilih chip Low/Mid/Boost.
         */
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
