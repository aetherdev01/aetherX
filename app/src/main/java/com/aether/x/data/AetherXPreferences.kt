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

enum class CrosshairStyle { CROSS, DOT, CIRCLE, CIRCLE_DOT, PLUS_GAP, X_SHAPE, CROSS_DOT, T_SHAPE, DIAMOND, SQUARE, CHEVRON, DOUBLE_RING, PLUS, BULLET, CIRCLE_PLUS, TICK_CROSS, CIRCLE_DOT_TICKS, CIRCLE_CROSS_TICKS }

enum class FpsMonitorStyle { ROG, CLASSIC }

data class AppPreferences(
    val onboardingCompleted: Boolean = false,

    val darkModePref: DarkModePref = DarkModePref.DARK,

    val dpiValue: Int = -1,
    val widthValue: Int = -1,
    val pointerSpeed: Int = 0,
    val touchBoostEnabled: Boolean = false,
    val forceMaxRefreshRate: Boolean = false,
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

    val preferredPrivilegeBackend: String? = null,

    val gameProfiles: Map<String, GameProfile> = emptyMap(),

    val activeGameProfilePackage: String? = null,

    val lastPlayedGamePackage: String? = null,
    val lastPlayedGameAtMillis: Long? = null,

    val gameBoosterMode: GameMode = GameMode.MID,

    val gameBoosterDndEnabled: Boolean = false,

    val gameBoosterFpsOverlayEnabled: Boolean = true,

    val gameBoosterRotationLocked: Boolean = false,
    val gameBoosterTouchBoostEnabled: Boolean = false,
) {

    val isMembershipActive: Boolean
        get() = licenseKey != null && (licenseExpiresAtMillis ?: 0L) > System.currentTimeMillis()
}

class AetherXPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_MODE = stringPreferencesKey("dark_mode_pref")
        val DPI_VALUE = intPreferencesKey("dpi_value")
        val WIDTH_VALUE = intPreferencesKey("width_value")
        val POINTER_SPEED = intPreferencesKey("pointer_speed")
        val TOUCH_BOOST = booleanPreferencesKey("touch_boost_enabled")
        val FORCE_REFRESH = booleanPreferencesKey("force_max_refresh_rate")
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

        val ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL = stringPreferencesKey("adblock_last_acknowledged_signal")

        val LICENSE_KEY = stringPreferencesKey("license_key")
        val LICENSE_EXPIRES_AT_MILLIS = longPreferencesKey("license_expires_at_millis")

        val LICENSE_FAILED_ATTEMPT_COUNT = intPreferencesKey("license_failed_attempt_count")

        val LICENSE_ATTEMPT_WINDOW_START_MILLIS = longPreferencesKey("license_attempt_window_start_millis")

        val LICENSE_LOCKOUT_UNTIL_MILLIS = longPreferencesKey("license_lockout_until_millis")

        val PREFERRED_PRIVILEGE_BACKEND = stringPreferencesKey("preferred_privilege_backend")

        val GAME_PROFILES_JSON = stringPreferencesKey("game_profiles_json")
        val ACTIVE_GAME_PROFILE_PACKAGE = stringPreferencesKey("active_game_profile_package")

        val REWARD_QUOTA_JSON = stringPreferencesKey("reward_quota_json")

        val LAST_PLAYED_GAME_PACKAGE = stringPreferencesKey("last_played_game_package")
        val LAST_PLAYED_GAME_AT_MILLIS = longPreferencesKey("last_played_game_at_millis")

        val GAME_BOOSTER_MODE = stringPreferencesKey("game_booster_mode")
        val GAME_BOOSTER_DND_ENABLED = booleanPreferencesKey("game_booster_dnd_enabled")
        val GAME_BOOSTER_FPS_OVERLAY_ENABLED = booleanPreferencesKey("game_booster_fps_overlay_enabled")
        val GAME_BOOSTER_ROTATION_LOCKED = booleanPreferencesKey("game_booster_rotation_locked")
        val GAME_BOOSTER_TOUCH_BOOST_ENABLED = booleanPreferencesKey("game_booster_touch_boost_enabled")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,

            darkModePref = DarkModePref.DARK,
            dpiValue = prefs[Keys.DPI_VALUE] ?: -1,
            widthValue = prefs[Keys.WIDTH_VALUE] ?: -1,
            pointerSpeed = prefs[Keys.POINTER_SPEED] ?: 0,
            touchBoostEnabled = prefs[Keys.TOUCH_BOOST] ?: false,
            forceMaxRefreshRate = prefs[Keys.FORCE_REFRESH] ?: false,
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
            preferredPrivilegeBackend = prefs[Keys.PREFERRED_PRIVILEGE_BACKEND],
            gameProfiles = GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON]),
            activeGameProfilePackage = prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE],
            lastPlayedGamePackage = prefs[Keys.LAST_PLAYED_GAME_PACKAGE],
            lastPlayedGameAtMillis = prefs[Keys.LAST_PLAYED_GAME_AT_MILLIS],
            gameBoosterMode = runCatching { GameMode.valueOf(prefs[Keys.GAME_BOOSTER_MODE] ?: "") }.getOrDefault(GameMode.MID),
            gameBoosterDndEnabled = prefs[Keys.GAME_BOOSTER_DND_ENABLED] ?: false,
            gameBoosterFpsOverlayEnabled = prefs[Keys.GAME_BOOSTER_FPS_OVERLAY_ENABLED] ?: true,
            gameBoosterRotationLocked = prefs[Keys.GAME_BOOSTER_ROTATION_LOCKED] ?: false,
            gameBoosterTouchBoostEnabled = prefs[Keys.GAME_BOOSTER_TOUCH_BOOST_ENABLED] ?: false,
        )
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun saveTweakState(
        pointerSpeed: Int,
        touchBoostEnabled: Boolean,
        forceMaxRefreshRate: Boolean,
        gameModeEnabled: Boolean,
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

    suspend fun getAdBlockAcknowledgedSignal(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL]
    }

    suspend fun setAdBlockAcknowledgedSignal(signalKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL] = signalKey
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

    suspend fun getPreferredPrivilegeBackend(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.PREFERRED_PRIVILEGE_BACKEND]
    }

    suspend fun setPreferredPrivilegeBackend(value: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PREFERRED_PRIVILEGE_BACKEND] = value }
    }

    suspend fun clearPreferredPrivilegeBackend() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.PREFERRED_PRIVILEGE_BACKEND) }
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
        }
    }

    suspend fun setGameBoosterMode(mode: GameMode) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_MODE] = mode.name }
    }

    suspend fun setGameBoosterDndEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_DND_ENABLED] = enabled }
    }

    suspend fun setGameBoosterFpsOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_FPS_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setGameBoosterRotationLocked(locked: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_ROTATION_LOCKED] = locked }
    }

    suspend fun setGameBoosterTouchBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_TOUCH_BOOST_ENABLED] = enabled }
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
}
