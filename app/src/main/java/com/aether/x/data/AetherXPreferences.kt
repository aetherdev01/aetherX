package com.aether.x.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aetherx_prefs")

enum class DarkModePref { SYSTEM, LIGHT, DARK }

enum class CrosshairStyle { CROSS, DOT, CIRCLE, CIRCLE_DOT, PLUS_GAP, X_SHAPE }

enum class FpsMonitorStyle { ROG, CLASSIC }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    // Default tema APLIKASI (bukan cuma nilai fallback saat DataStore belum
    // pernah ditulis) sengaja DARK, bukan SYSTEM — supaya pengguna baru yang
    // belum pernah mengubah pengaturan tema langsung melihat aplikasi dalam
    // mode gelap, terlepas dari pengaturan tema sistem HP-nya. Pengguna tetap
    // bisa mengganti ke LIGHT/SYSTEM kapan saja lewat menu Pengaturan.
    val darkModePref: DarkModePref = DarkModePref.DARK,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val dpiValue: Int = -1,
    val widthValue: Int = -1,
    val pointerSpeed: Int = 0,
    val touchBoostEnabled: Boolean = false,
    val forceMaxRefreshRate: Boolean = false,
    val gameModeEnabled: Boolean = false,
    val cpuPerformanceMode: Boolean = false,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val killBackgroundApps: Boolean = false,
    val vmHeapBoost: Boolean = false,
    val crosshairEnabled: Boolean = false,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.CROSS,
    val crosshairColor: Long = 0xFF00FF66,
    val crosshairSize: Int = 32,
    val crosshairThickness: Int = 3,
    val crosshairOpacity: Int = 100,
    val crosshairOffsetX: Int = 0,
    val crosshairOffsetY: Int = 0,
    val fpsMonitorEnabled: Boolean = false,
    val fpsMonitorStyle: FpsMonitorStyle = FpsMonitorStyle.CLASSIC,
    // Offset hanya dipakai oleh gaya ROG (bisa digeser). Gaya Classic selalu
    // terkunci di pojok kiri bawah layar, tidak memakai offset ini.
    val fpsMonitorOffsetX: Int = 0,
    val fpsMonitorOffsetY: Int = 0,
    val licenseKey: String? = null,
    // Epoch millis kapan lisensi ini kadaluarsa, hasil cache dari validasi
    // Firestore terakhir yang berhasil. null = belum pernah tervalidasi.
    val licenseExpiresAtMillis: Long? = null,
)

/**
 * Sumber kebenaran untuk preferensi pengguna: status onboarding, preferensi
 * tampilan (tema), dan nilai tweak terakhir yang diterapkan/disimpan.
 */
class AetherXPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_MODE = stringPreferencesKey("dark_mode_pref")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val DPI_VALUE = intPreferencesKey("dpi_value")
        val WIDTH_VALUE = intPreferencesKey("width_value")
        val POINTER_SPEED = intPreferencesKey("pointer_speed")
        val TOUCH_BOOST = booleanPreferencesKey("touch_boost_enabled")
        val FORCE_REFRESH = booleanPreferencesKey("force_max_refresh_rate")
        val GAME_MODE = booleanPreferencesKey("game_mode_enabled")
        // Khusus root: mode performa CPU (governor) & prioritas RAM (swappiness).
        val CPU_PERFORMANCE_MODE = booleanPreferencesKey("cpu_performance_mode")
        val RAM_PRIORITY_MODE = booleanPreferencesKey("ram_priority_mode")
        // Khusus root: override batas thermal throttling & governor performa GPU.
        val THERMAL_THROTTLE_OVERRIDE = booleanPreferencesKey("thermal_throttle_override")
        val GPU_PERFORMANCE_MODE = booleanPreferencesKey("gpu_performance_mode")
        // Khusus root: scheduler I/O storage, bersihkan proses background, dan
        // heap Dalvik/ART — tiga tweak root tambahan, dikelompokkan bersama
        // tweak root lain di section yang sama pada tab Tweak.
        val IO_SCHEDULER_BOOST = booleanPreferencesKey("io_scheduler_boost")
        val KILL_BACKGROUND_APPS = booleanPreferencesKey("kill_background_apps")
        val VM_HEAP_BOOST = booleanPreferencesKey("vm_heap_boost")
        // Disimpan agar bisa dipulihkan walau aplikasi sempat ditutup,
        // meski nilainya berupa Float (refresh rate target dalam Hz).
        val REFRESH_TARGET = floatPreferencesKey("refresh_target_hz")

        val CROSSHAIR_ENABLED = booleanPreferencesKey("crosshair_enabled")
        val CROSSHAIR_STYLE = stringPreferencesKey("crosshair_style")
        val CROSSHAIR_COLOR = longPreferencesKey("crosshair_color")
        val CROSSHAIR_SIZE = intPreferencesKey("crosshair_size")
        val CROSSHAIR_THICKNESS = intPreferencesKey("crosshair_thickness")
        val CROSSHAIR_OPACITY = intPreferencesKey("crosshair_opacity")
        val CROSSHAIR_OFFSET_X = intPreferencesKey("crosshair_offset_x")
        val CROSSHAIR_OFFSET_Y = intPreferencesKey("crosshair_offset_y")

        val FPS_MONITOR_ENABLED = booleanPreferencesKey("fps_monitor_enabled")
        val FPS_MONITOR_STYLE = stringPreferencesKey("fps_monitor_style")
        val FPS_MONITOR_OFFSET_X = intPreferencesKey("fps_monitor_offset_x")
        val FPS_MONITOR_OFFSET_Y = intPreferencesKey("fps_monitor_offset_y")

        // ID pengguna lokal (mis. "ID-67128") yang ditampilkan sebagai pengganti
        // status Shizuku/Root di tab Tweak. Dibuat sekali secara acak lalu
        // disimpan permanen di perangkat supaya nilainya konsisten setiap dibuka.
        val USER_ID = intPreferencesKey("user_id")

        // true kalau USER_ID di atas adalah nomor urut ASLI hasil alokasi dari
        // counter Firestore (lihat UserIdRepository) — bukan sekadar angka acak
        // fallback lokal yang dibuat saat offline.
        val USER_ID_SYNCED = booleanPreferencesKey("user_id_synced")

        // Cache lokal hasil validasi lisensi Firestore terakhir (lihat
        // LicenseRepository). Dipakai supaya app tidak wajib online setiap
        // kali dibuka — selama cache ini masih menunjukkan lisensi valid DAN
        // belum lewat waktunya, app boleh langsung lanjut ke MainScreen tanpa
        // menunggu round-trip ke Firestore.
        val LICENSE_KEY = stringPreferencesKey("license_key")
        val LICENSE_EXPIRES_AT_MILLIS = longPreferencesKey("license_expires_at_millis")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            darkModePref = prefs[Keys.DARK_MODE]?.let { runCatching { DarkModePref.valueOf(it) }.getOrNull() }
                ?: DarkModePref.DARK,
            temperatureUnit = prefs[Keys.TEMPERATURE_UNIT]
                ?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() }
                ?: TemperatureUnit.CELSIUS,
            dpiValue = prefs[Keys.DPI_VALUE] ?: -1,
            widthValue = prefs[Keys.WIDTH_VALUE] ?: -1,
            pointerSpeed = prefs[Keys.POINTER_SPEED] ?: 0,
            touchBoostEnabled = prefs[Keys.TOUCH_BOOST] ?: false,
            forceMaxRefreshRate = prefs[Keys.FORCE_REFRESH] ?: false,
            gameModeEnabled = prefs[Keys.GAME_MODE] ?: false,
            cpuPerformanceMode = prefs[Keys.CPU_PERFORMANCE_MODE] ?: false,
            ramPriorityMode = prefs[Keys.RAM_PRIORITY_MODE] ?: false,
            thermalThrottleOverride = prefs[Keys.THERMAL_THROTTLE_OVERRIDE] ?: false,
            gpuPerformanceMode = prefs[Keys.GPU_PERFORMANCE_MODE] ?: false,
            ioSchedulerBoost = prefs[Keys.IO_SCHEDULER_BOOST] ?: false,
            killBackgroundApps = prefs[Keys.KILL_BACKGROUND_APPS] ?: false,
            vmHeapBoost = prefs[Keys.VM_HEAP_BOOST] ?: false,
            crosshairEnabled = prefs[Keys.CROSSHAIR_ENABLED] ?: false,
            crosshairStyle = prefs[Keys.CROSSHAIR_STYLE]
                ?.let { runCatching { CrosshairStyle.valueOf(it) }.getOrNull() }
                ?: CrosshairStyle.CROSS,
            crosshairColor = prefs[Keys.CROSSHAIR_COLOR] ?: 0xFF00FF66,
            crosshairSize = prefs[Keys.CROSSHAIR_SIZE] ?: 32,
            crosshairThickness = prefs[Keys.CROSSHAIR_THICKNESS] ?: 3,
            crosshairOpacity = prefs[Keys.CROSSHAIR_OPACITY] ?: 100,
            crosshairOffsetX = prefs[Keys.CROSSHAIR_OFFSET_X] ?: 0,
            crosshairOffsetY = prefs[Keys.CROSSHAIR_OFFSET_Y] ?: 0,
            fpsMonitorEnabled = prefs[Keys.FPS_MONITOR_ENABLED] ?: false,
            fpsMonitorStyle = prefs[Keys.FPS_MONITOR_STYLE]
                ?.let { runCatching { FpsMonitorStyle.valueOf(it) }.getOrNull() }
                ?: FpsMonitorStyle.CLASSIC,
            fpsMonitorOffsetX = prefs[Keys.FPS_MONITOR_OFFSET_X] ?: 0,
            fpsMonitorOffsetY = prefs[Keys.FPS_MONITOR_OFFSET_Y] ?: 0,
            licenseKey = prefs[Keys.LICENSE_KEY],
            licenseExpiresAtMillis = prefs[Keys.LICENSE_EXPIRES_AT_MILLIS],
        )
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setDarkModePref(value: DarkModePref) {
        context.dataStore.edit { it[Keys.DARK_MODE] = value.name }
    }

    suspend fun setTemperatureUnit(value: TemperatureUnit) {
        context.dataStore.edit { it[Keys.TEMPERATURE_UNIT] = value.name }
    }

    suspend fun saveTweakState(
        pointerSpeed: Int,
        touchBoostEnabled: Boolean,
        forceMaxRefreshRate: Boolean,
        gameModeEnabled: Boolean,
        cpuPerformanceMode: Boolean = false,
        ramPriorityMode: Boolean = false,
        thermalThrottleOverride: Boolean = false,
        gpuPerformanceMode: Boolean = false,
        ioSchedulerBoost: Boolean = false,
        killBackgroundApps: Boolean = false,
        vmHeapBoost: Boolean = false,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.POINTER_SPEED] = pointerSpeed
            prefs[Keys.TOUCH_BOOST] = touchBoostEnabled
            prefs[Keys.FORCE_REFRESH] = forceMaxRefreshRate
            prefs[Keys.GAME_MODE] = gameModeEnabled
            prefs[Keys.CPU_PERFORMANCE_MODE] = cpuPerformanceMode
            prefs[Keys.RAM_PRIORITY_MODE] = ramPriorityMode
            prefs[Keys.THERMAL_THROTTLE_OVERRIDE] = thermalThrottleOverride
            prefs[Keys.GPU_PERFORMANCE_MODE] = gpuPerformanceMode
            prefs[Keys.IO_SCHEDULER_BOOST] = ioSchedulerBoost
            prefs[Keys.KILL_BACKGROUND_APPS] = killBackgroundApps
            prefs[Keys.VM_HEAP_BOOST] = vmHeapBoost
        }
    }

    suspend fun clearTweakState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.DPI_VALUE)
            prefs.remove(Keys.WIDTH_VALUE)
            prefs[Keys.POINTER_SPEED] = 0
            prefs[Keys.TOUCH_BOOST] = false
            prefs[Keys.FORCE_REFRESH] = false
            prefs[Keys.GAME_MODE] = false
            prefs[Keys.CPU_PERFORMANCE_MODE] = false
            prefs[Keys.RAM_PRIORITY_MODE] = false
            prefs[Keys.THERMAL_THROTTLE_OVERRIDE] = false
            prefs[Keys.GPU_PERFORMANCE_MODE] = false
            prefs[Keys.IO_SCHEDULER_BOOST] = false
            prefs[Keys.KILL_BACKGROUND_APPS] = false
            prefs[Keys.VM_HEAP_BOOST] = false
        }
    }

    suspend fun setCrosshairEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CROSSHAIR_ENABLED] = value }
    }

    suspend fun saveCrosshairConfig(
        style: CrosshairStyle,
        color: Long,
        size: Int,
        thickness: Int,
        opacity: Int,
        offsetX: Int,
        offsetY: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CROSSHAIR_STYLE] = style.name
            prefs[Keys.CROSSHAIR_COLOR] = color
            prefs[Keys.CROSSHAIR_SIZE] = size
            prefs[Keys.CROSSHAIR_THICKNESS] = thickness
            prefs[Keys.CROSSHAIR_OPACITY] = opacity
            prefs[Keys.CROSSHAIR_OFFSET_X] = offsetX
            prefs[Keys.CROSSHAIR_OFFSET_Y] = offsetY
        }
    }

    suspend fun setCrosshairOffset(offsetX: Int, offsetY: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CROSSHAIR_OFFSET_X] = offsetX
            prefs[Keys.CROSSHAIR_OFFSET_Y] = offsetY
        }
    }

    suspend fun setFpsMonitorEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.FPS_MONITOR_ENABLED] = value }
    }

    suspend fun setFpsMonitorStyle(style: FpsMonitorStyle) {
        context.dataStore.edit { it[Keys.FPS_MONITOR_STYLE] = style.name }
    }

    suspend fun setFpsMonitorOffset(offsetX: Int, offsetY: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FPS_MONITOR_OFFSET_X] = offsetX
            prefs[Keys.FPS_MONITOR_OFFSET_Y] = offsetY
        }
    }

    /** ID urut asli hasil alokasi Firestore, kalau sudah pernah berhasil disinkronkan. */
    suspend fun getSyncedUserId(): Int? {
        val prefs = context.dataStore.data.first()
        return if (prefs[Keys.USER_ID_SYNCED] == true) prefs[Keys.USER_ID] else null
    }

    /** Menyimpan ID urut asli dari Firestore sebagai nilai permanen ID pengguna. */
    suspend fun setSyncedUserId(id: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = id
            prefs[Keys.USER_ID_SYNCED] = true
        }
    }

    /** Menyimpan cache lokal hasil validasi lisensi Firestore yang berhasil. */
    suspend fun setLicenseCache(key: String, expiresAtMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_KEY] = key
            prefs[Keys.LICENSE_EXPIRES_AT_MILLIS] = expiresAtMillis
        }
    }

    /** Menghapus cache lisensi lokal — dipanggil kalau lisensi terbukti expired/revoked. */
    suspend fun clearLicenseCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LICENSE_KEY)
            prefs.remove(Keys.LICENSE_EXPIRES_AT_MILLIS)
        }
    }
}
