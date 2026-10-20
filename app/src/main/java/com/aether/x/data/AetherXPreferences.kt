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

/**
 * Daftar ID card Dashboard yang valid. Kartu baru otomatis disisipkan pada
 * posisi default-nya untuk tetap mempertahankan urutan custom milik pengguna.
 */
private val DEFAULT_DASHBOARD_CARD_ORDER = listOf("status", "recent_games", "game_monitor", "ram", "bonus")

enum class DarkModePref { SYSTEM, LIGHT, DARK }

/**
 * v3.6 — preset warna aksen tema. Nilai disimpan sebagai ARGB Long (bukan
 * Compose Color) supaya layer data tidak bergantung pada Compose. Tiap
 * preset punya varian terang (kontras aman di atas putih) dan gelap.
 * Default GRAPHITE: aksen abu-abu netral dengan kontras yang sesuai untuk tema terang dan gelap.
 */
enum class AccentPreset(val label: String, val lightArgb: Long, val darkArgb: Long) {
    ORCHID("Orchid", 0xFF7048E8, 0xFFA78BFA),
    ROSE("Rose", 0xFFD6336C, 0xFFF783AC),
    CORAL("Coral", 0xFFE8590C, 0xFFFF9A5C),
    AMBER("Amber", 0xFFC77700, 0xFFF2B84E),
    OCEAN("Ocean", 0xFF1C7ED6, 0xFF5AA9FF),
    MINT("Mint", 0xFF0CA678, 0xFF4ADE9F),
    GRAPHITE("Graphite", 0xFF343A40, 0xFFCED4DA),
}

/** v3.6 — pilihan font UI. POPPINS = font bawaan app (res/font), sisanya font sistem. */
enum class AppFont(val label: String) {
    POPPINS("Poppins"),
    SYSTEM("Sistem"),
    SERIF("Serif"),
    MONO("Mono"),
    CASUAL("Casual"),
}

/** v3.6 — gaya card: kaca transparan, solid, hanya garis tepi, atau tonal berwarna aksen. */
enum class CardStyle(val label: String) {
    GLASS("Kaca"),
    SOLID("Solid"),
    OUTLINE("Garis"),
    SOFT("Lembut"),
}

enum class CrosshairStyle { CROSS, DOT, CIRCLE, CIRCLE_DOT, PLUS_GAP, X_SHAPE, CROSS_DOT, T_SHAPE, DIAMOND, SQUARE, CHEVRON, DOUBLE_RING, PLUS, BULLET, CIRCLE_PLUS, TICK_CROSS, CIRCLE_DOT_TICKS, CIRCLE_CROSS_TICKS }

enum class FpsMonitorStyle { ROG, CLASSIC }

data class AppPreferences(
    val onboardingCompleted: Boolean = false,

    val darkModePref: DarkModePref = DarkModePref.LIGHT,

    val accentPreset: AccentPreset = AccentPreset.GRAPHITE,
    /** Optional user-selected ARGB accent; null means use the selected preset. */
    val customAccentArgb: Long? = 0xFFB4DE9FL,
    val appFont: AppFont = AppFont.POPPINS,
    val cardStyle: CardStyle = CardStyle.GLASS,
    /** 0..100 — kekuatan blur/kaca (navbar, dialog, transparansi card). */
    val blurStrength: Int = 25,
    val hapticsEnabled: Boolean = true,
    val reduceMotionEnabled: Boolean = false,


    val dpiValue: Int = -1,
    val widthValue: Int = -1,
    val pointerSpeed: Int = 0,
    val touchBoostEnabled: Boolean = false,
    val forceMaxRefreshRate: Boolean = false,
    val rotationLockEnabled: Boolean = false,
    val gameModeEnabled: Boolean = false,
    val cpuGovernor: CpuGovernor = CpuGovernor.UNIVERSAL,
    val ramPriorityMode: Boolean = false,
    val thermalThrottleOverride: Boolean = false,
    val gpuPerformanceMode: Boolean = false,
    val ioSchedulerBoost: Boolean = false,
    val killBackgroundApps: Boolean = false,
    val vmHeapBoost: Boolean = false,
    val dozeDisabled: Boolean = false,
    val crosshairEnabled: Boolean = false,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.CROSS,
    val crosshairColor: Long = 0xFF00FF66,
    val crosshairSize: Int = 32,
    val crosshairRotationDegrees: Int = 0,
    val crosshairOffsetX: Int = 0,
    val crosshairOffsetY: Int = 0,

    val crosshairPositionLocked: Boolean = false,
    val fpsMonitorEnabled: Boolean = false,
    val fpsMonitorStyle: FpsMonitorStyle = FpsMonitorStyle.CLASSIC,

    val fpsMonitorOffsetX: Int = 0,
    val fpsMonitorOffsetY: Int = 0,
    val licenseKey: String? = null,

    val licenseExpiresAtMillis: Long? = null,

    val gameProfiles: Map<String, GameProfile> = emptyMap(),

    val activeGameProfilePackage: String? = null,

    val lastPlayedGamePackage: String? = null,
    val lastPlayedGameAtMillis: Long? = null,
    /** Daftar paket game terbaru yang dibuka lewat AetherX (paling baru lebih dulu). */
    val recentGamePackages: List<String> = emptyList(),

    val dashboardCardOrder: List<String> = DEFAULT_DASHBOARD_CARD_ORDER,

    val lastSeenChangelogVersionCode: Int = 0,

    /** yyyyMMdd klaim bonus harian terakhir (zona waktu device), null = belum pernah. */
    val dailyBonusLastDate: String? = null,
    /** Jumlah hari beruntun klaim bonus harian. */
    val dailyBonusStreak: Int = 0,
) {

    val isMembershipActive: Boolean
        get() = licenseKey != null && (licenseExpiresAtMillis ?: 0L) > System.currentTimeMillis()
}

class AetherXPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_MODE = stringPreferencesKey("dark_mode_pref")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val CUSTOM_ACCENT_ARGB = longPreferencesKey("custom_accent_argb")
        val APP_FONT = stringPreferencesKey("app_font")
        val CARD_STYLE = stringPreferencesKey("card_style")
        val BLUR_STRENGTH = intPreferencesKey("blur_strength")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val DPI_VALUE = intPreferencesKey("dpi_value")
        val WIDTH_VALUE = intPreferencesKey("width_value")
        val POINTER_SPEED = intPreferencesKey("pointer_speed")
        val TOUCH_BOOST = booleanPreferencesKey("touch_boost_enabled")
        val FORCE_REFRESH = booleanPreferencesKey("force_max_refresh_rate")
        val ROTATION_LOCK = booleanPreferencesKey("rotation_lock_enabled")
        val GAME_MODE = booleanPreferencesKey("game_mode_enabled")

        val CPU_GOVERNOR = stringPreferencesKey("cpu_governor")
        val RAM_PRIORITY_MODE = booleanPreferencesKey("ram_priority_mode")

        val THERMAL_THROTTLE_OVERRIDE = booleanPreferencesKey("thermal_throttle_override")
        val GPU_PERFORMANCE_MODE = booleanPreferencesKey("gpu_performance_mode")

        val IO_SCHEDULER_BOOST = booleanPreferencesKey("io_scheduler_boost")
        val KILL_BACKGROUND_APPS = booleanPreferencesKey("kill_background_apps")
        val VM_HEAP_BOOST = booleanPreferencesKey("vm_heap_boost")
        val DOZE_DISABLED = booleanPreferencesKey("doze_disabled")

        val REFRESH_TARGET = floatPreferencesKey("refresh_target_hz")

        val CROSSHAIR_ENABLED = booleanPreferencesKey("crosshair_enabled")
        val CROSSHAIR_STYLE = stringPreferencesKey("crosshair_style")
        val CROSSHAIR_COLOR = longPreferencesKey("crosshair_color")
        val CROSSHAIR_SIZE = intPreferencesKey("crosshair_size")
        val CROSSHAIR_ROTATION = intPreferencesKey("crosshair_rotation_degrees")
        val CROSSHAIR_OFFSET_X = intPreferencesKey("crosshair_offset_x")
        val CROSSHAIR_OFFSET_Y = intPreferencesKey("crosshair_offset_y")
        val CROSSHAIR_POSITION_LOCKED = booleanPreferencesKey("crosshair_position_locked")

        val FPS_MONITOR_ENABLED = booleanPreferencesKey("fps_monitor_enabled")
        val FPS_MONITOR_STYLE = stringPreferencesKey("fps_monitor_style")
        val FPS_MONITOR_OFFSET_X = intPreferencesKey("fps_monitor_offset_x")
        val FPS_MONITOR_OFFSET_Y = intPreferencesKey("fps_monitor_offset_y")

        val USER_ID = stringPreferencesKey("user_id")

        val USER_ID_SYNCED = booleanPreferencesKey("user_id_synced")

        val LICENSE_KEY = stringPreferencesKey("license_key")
        val LICENSE_EXPIRES_AT_MILLIS = longPreferencesKey("license_expires_at_millis")

        val LICENSE_FAILED_ATTEMPT_COUNT = intPreferencesKey("license_failed_attempt_count")

        val LICENSE_ATTEMPT_WINDOW_START_MILLIS = longPreferencesKey("license_attempt_window_start_millis")

        val LICENSE_LOCKOUT_UNTIL_MILLIS = longPreferencesKey("license_lockout_until_millis")

        val GAME_PROFILES_JSON = stringPreferencesKey("game_profiles_json")
        val ACTIVE_GAME_PROFILE_PACKAGE = stringPreferencesKey("active_game_profile_package")

        val REWARD_QUOTA_JSON = stringPreferencesKey("reward_quota_json")

        val LAST_PLAYED_GAME_PACKAGE = stringPreferencesKey("last_played_game_package")
        val LAST_PLAYED_GAME_AT_MILLIS = longPreferencesKey("last_played_game_at_millis")
        val RECENT_GAME_PACKAGES = stringPreferencesKey("recent_game_packages")

        val DASHBOARD_CARD_ORDER = stringPreferencesKey("dashboard_card_order")

        val LAST_SEEN_CHANGELOG_VERSION_CODE = intPreferencesKey("last_seen_changelog_version_code")

        val DAILY_BONUS_LAST_DATE = stringPreferencesKey("daily_bonus_last_date")
        val DAILY_BONUS_STREAK = intPreferencesKey("daily_bonus_streak")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,

            darkModePref = runCatching { DarkModePref.valueOf(prefs[Keys.DARK_MODE] ?: "") }
                .getOrDefault(DarkModePref.LIGHT),
            accentPreset = runCatching { AccentPreset.valueOf(prefs[Keys.ACCENT_PRESET] ?: "") }
                .getOrDefault(AccentPreset.GRAPHITE),
            customAccentArgb = prefs[Keys.CUSTOM_ACCENT_ARGB] ?: if (prefs[Keys.ACCENT_PRESET] == null) 0xFFB4DE9FL else null,
            appFont = runCatching { AppFont.valueOf(prefs[Keys.APP_FONT] ?: "") }
                .getOrDefault(AppFont.POPPINS),
            cardStyle = runCatching { CardStyle.valueOf(prefs[Keys.CARD_STYLE] ?: "") }
                .getOrDefault(CardStyle.GLASS),
            blurStrength = (prefs[Keys.BLUR_STRENGTH] ?: 25).coerceIn(0, 100),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            reduceMotionEnabled = prefs[Keys.REDUCE_MOTION_ENABLED] ?: false,
            dpiValue = prefs[Keys.DPI_VALUE] ?: -1,
            widthValue = prefs[Keys.WIDTH_VALUE] ?: -1,
            pointerSpeed = prefs[Keys.POINTER_SPEED] ?: 0,
            touchBoostEnabled = prefs[Keys.TOUCH_BOOST] ?: false,
            forceMaxRefreshRate = prefs[Keys.FORCE_REFRESH] ?: false,
            rotationLockEnabled = prefs[Keys.ROTATION_LOCK] ?: false,
            gameModeEnabled = prefs[Keys.GAME_MODE] ?: false,
            cpuGovernor = prefs[Keys.CPU_GOVERNOR]
                ?.let { runCatching { CpuGovernor.valueOf(it) }.getOrNull() }
                ?: CpuGovernor.UNIVERSAL,
            ramPriorityMode = prefs[Keys.RAM_PRIORITY_MODE] ?: false,
            thermalThrottleOverride = prefs[Keys.THERMAL_THROTTLE_OVERRIDE] ?: false,
            gpuPerformanceMode = prefs[Keys.GPU_PERFORMANCE_MODE] ?: false,
            ioSchedulerBoost = prefs[Keys.IO_SCHEDULER_BOOST] ?: false,
            killBackgroundApps = prefs[Keys.KILL_BACKGROUND_APPS] ?: false,
            vmHeapBoost = prefs[Keys.VM_HEAP_BOOST] ?: false,
            dozeDisabled = prefs[Keys.DOZE_DISABLED] ?: false,
            crosshairEnabled = prefs[Keys.CROSSHAIR_ENABLED] ?: false,
            crosshairStyle = prefs[Keys.CROSSHAIR_STYLE]
                ?.let { runCatching { CrosshairStyle.valueOf(it) }.getOrNull() }
                ?: CrosshairStyle.CROSS,
            crosshairColor = prefs[Keys.CROSSHAIR_COLOR] ?: 0xFF00FF66,
            crosshairSize = prefs[Keys.CROSSHAIR_SIZE] ?: 32,
            crosshairRotationDegrees = prefs[Keys.CROSSHAIR_ROTATION] ?: 0,
            crosshairOffsetX = prefs[Keys.CROSSHAIR_OFFSET_X] ?: 0,
            crosshairOffsetY = prefs[Keys.CROSSHAIR_OFFSET_Y] ?: 0,
            crosshairPositionLocked = prefs[Keys.CROSSHAIR_POSITION_LOCKED] ?: false,
            fpsMonitorEnabled = prefs[Keys.FPS_MONITOR_ENABLED] ?: false,
            fpsMonitorStyle = prefs[Keys.FPS_MONITOR_STYLE]
                ?.let { runCatching { FpsMonitorStyle.valueOf(it) }.getOrNull() }
                ?: FpsMonitorStyle.CLASSIC,
            fpsMonitorOffsetX = prefs[Keys.FPS_MONITOR_OFFSET_X] ?: 0,
            fpsMonitorOffsetY = prefs[Keys.FPS_MONITOR_OFFSET_Y] ?: 0,
            licenseKey = prefs[Keys.LICENSE_KEY],
            licenseExpiresAtMillis = prefs[Keys.LICENSE_EXPIRES_AT_MILLIS],
            gameProfiles = GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON]),
            activeGameProfilePackage = prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE],
            lastPlayedGamePackage = prefs[Keys.LAST_PLAYED_GAME_PACKAGE],
            lastPlayedGameAtMillis = prefs[Keys.LAST_PLAYED_GAME_AT_MILLIS],
            recentGamePackages = prefs[Keys.RECENT_GAME_PACKAGES]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.take(8)
                ?: emptyList(),
            dashboardCardOrder = prefs[Keys.DASHBOARD_CARD_ORDER]
                ?.split(",")
                ?.filter { it.isNotBlank() && it in DEFAULT_DASHBOARD_CARD_ORDER }
                ?.distinct()
                ?.let { stored ->
                    val merged = stored.toMutableList()
                    DEFAULT_DASHBOARD_CARD_ORDER.forEachIndexed { i, id ->
                        if (id !in merged) merged.add(i.coerceAtMost(merged.size), id)
                    }
                    merged.toList()
                }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_DASHBOARD_CARD_ORDER,
            lastSeenChangelogVersionCode = prefs[Keys.LAST_SEEN_CHANGELOG_VERSION_CODE] ?: 0,
            dailyBonusLastDate = prefs[Keys.DAILY_BONUS_LAST_DATE],
            dailyBonusStreak = prefs[Keys.DAILY_BONUS_STREAK] ?: 0,
        )
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setDarkModePref(value: DarkModePref) {
        context.dataStore.edit { it[Keys.DARK_MODE] = value.name }
    }

    suspend fun setAccentPreset(value: AccentPreset) {
        context.dataStore.edit {
            it[Keys.ACCENT_PRESET] = value.name
            it.remove(Keys.CUSTOM_ACCENT_ARGB)
        }
    }

    suspend fun setCustomAccentArgb(value: Long?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.CUSTOM_ACCENT_ARGB)
            else prefs[Keys.CUSTOM_ACCENT_ARGB] = value and 0xFFFFFFFFL
        }
    }

    suspend fun setAppFont(value: AppFont) {
        context.dataStore.edit { it[Keys.APP_FONT] = value.name }
    }

    suspend fun setCardStyle(value: CardStyle) {
        context.dataStore.edit { it[Keys.CARD_STYLE] = value.name }
    }

    suspend fun setBlurStrength(value: Int) {
        context.dataStore.edit { it[Keys.BLUR_STRENGTH] = value.coerceIn(0, 100) }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCE_MOTION_ENABLED] = enabled }
    }

    suspend fun saveTweakState(
        pointerSpeed: Int,
        touchBoostEnabled: Boolean,
        forceMaxRefreshRate: Boolean,
        gameModeEnabled: Boolean,
        rotationLockEnabled: Boolean,
        cpuGovernor: CpuGovernor = CpuGovernor.UNIVERSAL,
        ramPriorityMode: Boolean = false,
        thermalThrottleOverride: Boolean = false,
        gpuPerformanceMode: Boolean = false,
        ioSchedulerBoost: Boolean = false,
        killBackgroundApps: Boolean = false,
        vmHeapBoost: Boolean = false,
        dozeDisabled: Boolean = false,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.POINTER_SPEED] = pointerSpeed
            prefs[Keys.TOUCH_BOOST] = touchBoostEnabled
            prefs[Keys.FORCE_REFRESH] = forceMaxRefreshRate
            prefs[Keys.ROTATION_LOCK] = rotationLockEnabled
            prefs[Keys.GAME_MODE] = gameModeEnabled
            prefs[Keys.CPU_GOVERNOR] = cpuGovernor.name
            prefs[Keys.RAM_PRIORITY_MODE] = ramPriorityMode
            prefs[Keys.THERMAL_THROTTLE_OVERRIDE] = thermalThrottleOverride
            prefs[Keys.GPU_PERFORMANCE_MODE] = gpuPerformanceMode
            prefs[Keys.IO_SCHEDULER_BOOST] = ioSchedulerBoost
            prefs[Keys.KILL_BACKGROUND_APPS] = killBackgroundApps
            prefs[Keys.VM_HEAP_BOOST] = vmHeapBoost
            prefs[Keys.DOZE_DISABLED] = dozeDisabled
        }
    }

    suspend fun clearTweakState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.DPI_VALUE)
            prefs.remove(Keys.WIDTH_VALUE)
            prefs[Keys.POINTER_SPEED] = 0
            prefs[Keys.TOUCH_BOOST] = false
            prefs[Keys.FORCE_REFRESH] = false
            prefs[Keys.ROTATION_LOCK] = false
            prefs[Keys.GAME_MODE] = false
            prefs[Keys.CPU_GOVERNOR] = CpuGovernor.UNIVERSAL.name
            prefs[Keys.RAM_PRIORITY_MODE] = false
            prefs[Keys.THERMAL_THROTTLE_OVERRIDE] = false
            prefs[Keys.GPU_PERFORMANCE_MODE] = false
            prefs[Keys.IO_SCHEDULER_BOOST] = false
            prefs[Keys.KILL_BACKGROUND_APPS] = false
            prefs[Keys.VM_HEAP_BOOST] = false
            prefs[Keys.DOZE_DISABLED] = false
        }
    }

    suspend fun setCrosshairEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CROSSHAIR_ENABLED] = value }
    }

    suspend fun saveCrosshairConfig(
        style: CrosshairStyle,
        color: Long,
        size: Int,
        rotationDegrees: Int,
        offsetX: Int,
        offsetY: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CROSSHAIR_STYLE] = style.name
            prefs[Keys.CROSSHAIR_COLOR] = color
            prefs[Keys.CROSSHAIR_SIZE] = size
            prefs[Keys.CROSSHAIR_ROTATION] = rotationDegrees
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

    suspend fun setCrosshairPositionLocked(locked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CROSSHAIR_POSITION_LOCKED] = locked
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

    suspend fun getSyncedUserId(): String? {
        val prefs = context.dataStore.data.first()
        return if (prefs[Keys.USER_ID_SYNCED] == true) prefs[Keys.USER_ID] else null
    }

    suspend fun setSyncedUserId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = id
            prefs[Keys.USER_ID_SYNCED] = true
        }
    }

    suspend fun setLicenseCache(key: String, expiresAtMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_KEY] = key
            prefs[Keys.LICENSE_EXPIRES_AT_MILLIS] = expiresAtMillis
        }
    }

    suspend fun clearLicenseCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LICENSE_KEY)
            prefs.remove(Keys.LICENSE_EXPIRES_AT_MILLIS)
        }
    }

    data class LicenseAttemptState(
        val failedAttemptCount: Int,
        val windowStartMillis: Long?,
        val lockoutUntilMillis: Long?,
    )

    suspend fun getLicenseAttemptState(): LicenseAttemptState {
        val prefs = context.dataStore.data.first()
        return LicenseAttemptState(
            failedAttemptCount = prefs[Keys.LICENSE_FAILED_ATTEMPT_COUNT] ?: 0,
            windowStartMillis = prefs[Keys.LICENSE_ATTEMPT_WINDOW_START_MILLIS],
            lockoutUntilMillis = prefs[Keys.LICENSE_LOCKOUT_UNTIL_MILLIS],
        )
    }

    suspend fun recordFailedLicenseAttempt(
        failedAttemptCount: Int,
        windowStartMillis: Long,
        lockoutUntilMillis: Long?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_FAILED_ATTEMPT_COUNT] = failedAttemptCount
            prefs[Keys.LICENSE_ATTEMPT_WINDOW_START_MILLIS] = windowStartMillis
            if (lockoutUntilMillis != null) {
                prefs[Keys.LICENSE_LOCKOUT_UNTIL_MILLIS] = lockoutUntilMillis
            } else {
                prefs.remove(Keys.LICENSE_LOCKOUT_UNTIL_MILLIS)
            }
        }
    }

    suspend fun clearLicenseAttemptState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LICENSE_FAILED_ATTEMPT_COUNT)
            prefs.remove(Keys.LICENSE_ATTEMPT_WINDOW_START_MILLIS)
            prefs.remove(Keys.LICENSE_LOCKOUT_UNTIL_MILLIS)
        }
    }

    suspend fun saveGameProfile(profile: GameProfile) {
        context.dataStore.edit { prefs ->
            val current = GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON]).toMutableMap()
            if (profile.hasAnyTweakEnabled) {
                current[profile.packageName] = profile
            } else {
                current.remove(profile.packageName)
            }
            prefs[Keys.GAME_PROFILES_JSON] = GameProfileSerializer.serialize(current)
        }
    }

    suspend fun deleteGameProfile(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON]).toMutableMap()
            current.remove(packageName)
            prefs[Keys.GAME_PROFILES_JSON] = GameProfileSerializer.serialize(current)
        }
    }

    suspend fun setActiveGameProfilePackage(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(Keys.ACTIVE_GAME_PROFILE_PACKAGE)
            } else {
                prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE] = packageName
            }
        }
    }

    suspend fun getActiveGameProfilePackage(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE]
    }

    suspend fun getGameProfiles(): Map<String, GameProfile> {
        val prefs = context.dataStore.data.first()
        return GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON])
    }

    suspend fun recordGameLaunched(packageName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYED_GAME_PACKAGE] = packageName
            prefs[Keys.LAST_PLAYED_GAME_AT_MILLIS] = System.currentTimeMillis()
            val previous = prefs[Keys.RECENT_GAME_PACKAGES]
                ?.split(",")
                ?.map { it.trim() }
                .orEmpty()
            prefs[Keys.RECENT_GAME_PACKAGES] = (listOf(packageName) + previous)
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
                .joinToString(",")
        }
    }

    suspend fun setDashboardCardOrder(order: List<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.DASHBOARD_CARD_ORDER] = order.joinToString(",") }
    }

    suspend fun setLastSeenChangelogVersionCode(versionCode: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_SEEN_CHANGELOG_VERSION_CODE] = versionCode }
    }

    suspend fun getRewardQuota(featureKey: String, dateKey: String): RewardQuotaState {
        val prefs = context.dataStore.data.first()
        val all = RewardQuotaSerializer.deserialize(prefs[Keys.REWARD_QUOTA_JSON])
        val stored = all[featureKey] ?: return RewardQuotaState.empty(featureKey, dateKey)
        return if (stored.dateKey == dateKey) stored else RewardQuotaState.empty(featureKey, dateKey)
    }

    suspend fun setRewardQuota(state: RewardQuotaState) {
        context.dataStore.edit { prefs ->
            val current = RewardQuotaSerializer.deserialize(prefs[Keys.REWARD_QUOTA_JSON]).toMutableMap()
            current[state.featureKey] = state
            prefs[Keys.REWARD_QUOTA_JSON] = RewardQuotaSerializer.serialize(current)
        }
    }

    suspend fun setDailyBonusClaimed(dateKey: String, streak: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_BONUS_LAST_DATE] = dateKey
            prefs[Keys.DAILY_BONUS_STREAK] = streak
        }
    }
}
