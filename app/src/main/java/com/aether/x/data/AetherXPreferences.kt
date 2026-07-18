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

// CpuGovernor didefinisikan di TweakRepository.kt tapi satu package (com.aether.x.data),
// jadi tidak perlu import eksplisit.
private val Context.dataStore by preferencesDataStore(name = "aetherx_prefs")

enum class DarkModePref { SYSTEM, LIGHT, DARK }

// FITUR BARU: DIAMOND (belah ketupat terbuka) & SQUARE (kotak bracket
// 4 sudut), dan CHEVRON (panah "V" ganda dari 4 sisi mengarah ke pusat,
// gaya populer di crosshair custom game FPS/battle-royale mobile) &
// DOUBLE_RING (dua lingkaran konsentris, gaya sniper-scope) — lihat
// rendering di StyleIconButton (CrosshairSettingsSection.kt) dan
// CrosshairPreview.kt. Overlay sungguhan (CrosshairView.onDraw, modul
// core/overlay) juga perlu ditambah case yang sama supaya WYSIWYG.
enum class CrosshairStyle { CROSS, DOT, CIRCLE, CIRCLE_DOT, PLUS_GAP, X_SHAPE, CROSS_DOT, T_SHAPE, DIAMOND, SQUARE, CHEVRON, DOUBLE_RING }

enum class FpsMonitorStyle { ROG, CLASSIC }

// TemperatureUnit DIHAPUS dari modul ini (permintaan "hapus opsi suhu") —
// enum ini sebelumnya cuma dipakai untuk opsi "Satuan Suhu" di Settings.
// TemperatureUnit itu sendiri MASIH ADA, dipindah jadi detail implementasi
// internal FpsMonitorView (lihat core/overlay/FpsMonitorView.kt) karena
// overlay Monitor FPS tetap menampilkan suhu, hanya saja sekarang selalu
// Celsius (tidak lagi jadi preferensi user yang bisa diganti).

data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    // Default tema APLIKASI (bukan cuma nilai fallback saat DataStore belum
    // pernah ditulis) sengaja DARK, bukan SYSTEM — supaya pengguna baru yang
    // belum pernah mengubah pengaturan tema langsung melihat aplikasi dalam
    // mode gelap, terlepas dari pengaturan tema sistem HP-nya. Pengguna tetap
    // bisa mengganti ke LIGHT/SYSTEM kapan saja lewat menu Pengaturan.
    val darkModePref: DarkModePref = DarkModePref.DARK,
    // appLanguage DIHAPUS TOTAL (fitur "pemilih Bahasa" di-rollback atas
    // permintaan pengguna — "kurang cocok"). Lihat juga strings.xml
    // (settings_language_* dihapus) dan SettingsScreen.kt/SettingsViewModel.kt
    // yang sudah tidak lagi mereferensikan field ini.
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
    val crosshairThickness: Int = 3,
    val crosshairOpacity: Int = 100,
    val crosshairOffsetX: Int = 0,
    val crosshairOffsetY: Int = 0,
    // OPSI BARU: kunci posisi crosshair — mencegah joystick posisi tergeser
    // tidak sengaja di CrosshairSettingsSection. Lihat setCrosshairPositionLocked.
    val crosshairPositionLocked: Boolean = false,
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
    // Backend privilese yang SENGAJA dipilih pengguna di layar Izin Akses:
    // "ADB", "ROOT", atau null (belum memilih/direset). Nilai lama "SHIZUKU"
    // (tersimpan sebelum rework ADB tertanam) dimigrasikan otomatis jadi
    // "ADB" oleh PrivilegeManager.init() — lihat komentar di sana. Dipakai
    // supaya ADB Tertanam dan Root tidak pernah aktif bersamaan.
    val preferredPrivilegeBackend: String? = null,
    // Game Profile (khusus Root) — lihat data/GameProfile.kt & GameProfileMonitorService.
    // Map package name -> tweak root yang tersimpan untuk game itu.
    val gameProfiles: Map<String, GameProfile> = emptyMap(),
    // Package name game yang SEDANG diterapkan profil-nya oleh
    // GameProfileMonitorService (null kalau tidak ada game profile yang
    // sedang aktif dipantau). Disimpan persist supaya kalau service sempat
    // mati (mis. proses AetherX di-kill sistem) sementara game masih
    // terbuka, saat service hidup lagi ia tahu tweak profil mana yang
    // seharusnya sedang aktif dan perlu direset saat game itu ditutup.
    val activeGameProfilePackage: String? = null,
    // FITUR BARU (section "Aktivitas Game" di Dashboard): package name game
    // TERAKHIR yang dibuka lewat AetherX (Dashboard atau Game Booster),
    // beserta kapan (epoch millis) — dipakai untuk urutkan & tandai chip
    // "Terakhir dipakai" di daftar game horizontal Dashboard. Ini BERBEDA
    // dari activeGameProfilePackage di atas (yang khusus root/tweak-aktif);
    // ini murni riwayat pemakaian, tercatat terlepas dari backend privilese
    // atau ada/tidaknya Game Profile untuk game itu.
    val lastPlayedGamePackage: String? = null,
    val lastPlayedGameAtMillis: Long? = null,

    // FITUR BARU — Game Booster (lihat perintah rework: "buatkan game
    // booster screen ... ada pilihan menu banyak, dari tampilkan fps, mode
    // game, jangan ganggu, screenshot, mode boost, mode hemat"):
    //
    // [gameBoosterMode]: preset performa TERAKHIR dipilih pengguna di Game
    // Booster — reuse GameMode (LOW/MID/BOOST) yang sama dipakai Game
    // Profile per-game, supaya "Mode Hemat"=LOW, "Mode Normal"=MID, "Mode
    // Boost"=BOOST konsisten satu sistem preset di seluruh app (bukan enum
    // terpisah yang searti tapi nama beda).
    val gameBoosterMode: GameMode = GameMode.MID,
    // [gameBoosterDndEnabled]: toggle "Jangan Ganggu" KHUSUS di dalam Game
    // Booster — TERPISAH dari gameModeEnabled (toggle DND global di layar
    // Tweak) karena keduanya punya UMUR HIDUP berbeda: gameModeEnabled
    // persisten sampai pengguna matikan manual di Tweak, sedangkan
    // gameBoosterDndEnabled BERLAKU HANYA SELAMA sesi Game Booster aktif
    // (floating sidebar terbuka) dan otomatis kembali OFF saat sidebar
    // ditutup/game keluar dari foreground — lihat GameBoosterOverlayService.
    val gameBoosterDndEnabled: Boolean = false,
    // [gameBoosterFpsOverlayEnabled]: toggle FPS overlay KHUSUS di dalam
    // Game Booster (ditampilkan sebagai bagian floating sidebar) —
    // TERPISAH dari fpsMonitorEnabled (fitur FPS Monitor mandiri di
    // Settings, overlay sendiri) supaya pengguna bisa mengaktifkan salah
    // satu tanpa memengaruhi yang lain (mis. FPS Monitor mandiri tetap
    // nyala terus-menerus di semua app, sementara FPS overlay Game Booster
    // hanya relevan selama sidebar terbuka).
    val gameBoosterFpsOverlayEnabled: Boolean = true,
    // FITUR BARU (Game Booster — lihat perintah rework foto referensi 1,
    // menu "Kunci Rotasi" & "Akselerasi Sentuhan"): dua toggle baru dengan
    // pola umur-hidup SAMA seperti gameBoosterDndEnabled di atas — berlaku
    // hanya selama sesi Game Booster aktif, kembali OFF otomatis saat sesi
    // berakhir (lihat GameBoosterOverlayService.stop).
    val gameBoosterRotationLocked: Boolean = false,
    val gameBoosterTouchBoostEnabled: Boolean = false,
) {
    /**
     * Status membership dari CACHE LOKAL saja (tanpa network call) — sumber
     * kebenaran sesungguhnya tetap di Firestore lewat [com.aether.x.data.LicenseRepository],
     * ini murni untuk keputusan cepat/non-kritis seperti "boleh tampilkan
     * iklan atau tidak" di titik-titik yang tidak sepadan menunggu round-trip
     * jaringan (mis. [com.aether.x.core.ads.InterstitialAdGate] dipanggil
     * dari [com.aether.x.ui.tweak.TweakViewModel]). Logika ini SENGAJA sama
     * dengan yang dipakai [com.aether.x.ui.membership.MembershipViewModel]
     * untuk menentukan [com.aether.x.ui.membership.MembershipUiStatus.ACTIVE]
     * (licenseKey ada DAN belum lewat expiresAtMillis).
     */
    val isMembershipActive: Boolean
        get() = licenseKey != null && (licenseExpiresAtMillis ?: 0L) > System.currentTimeMillis()
}

/**
 * Sumber kebenaran untuk preferensi pengguna: status onboarding, preferensi
 * tampilan (tema), dan nilai tweak terakhir yang diterapkan/disimpan.
 */
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
        // Khusus root: governor CPU pilihan (Schedutil/Performance/Ondemand/
        // Battery/Universal) & prioritas RAM (swappiness).
        val CPU_GOVERNOR = stringPreferencesKey("cpu_governor")
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
        val DOZE_DISABLED = booleanPreferencesKey("doze_disabled")
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
        val CROSSHAIR_POSITION_LOCKED = booleanPreferencesKey("crosshair_position_locked")

        val FPS_MONITOR_ENABLED = booleanPreferencesKey("fps_monitor_enabled")
        val FPS_MONITOR_STYLE = stringPreferencesKey("fps_monitor_style")
        val FPS_MONITOR_OFFSET_X = intPreferencesKey("fps_monitor_offset_x")
        val FPS_MONITOR_OFFSET_Y = intPreferencesKey("fps_monitor_offset_y")

        // ID pengguna lokal (mis. "ID-67128") yang ditampilkan sebagai pengganti
        // status ADB/Root di tab Tweak. Dibuat sekali secara acak lalu
        // disimpan permanen di perangkat supaya nilainya konsisten setiap dibuka.
        val USER_ID = intPreferencesKey("user_id")

        // true kalau USER_ID di atas adalah nomor urut ASLI hasil alokasi dari
        // counter Firestore (lihat UserIdRepository) — bukan sekadar angka acak
        // fallback lokal yang dibuat saat offline.
        val USER_ID_SYNCED = booleanPreferencesKey("user_id_synced")

        // ── Dialog AdBlock (lihat AdBlockDialogState) ──
        // Dipersist (bukan cuma in-memory/shownThisSession) supaya "Tutup"
        // benar-benar berarti tutup — tidak nongol lagi setiap kali app
        // dibuka ulang selama sinyal adblock masih SAMA seperti terakhir
        // kali pengguna menutupnya. Menyimpan SNAPSHOT sinyal (bukan cuma
        // boolean "pernah ditutup") supaya kalau pengguna GANTI metode
        // adblock (mis. lepas DNS lalu pasang modul Magisk baru), dialog
        // tetap muncul lagi untuk sinyal BARU itu — bukan dianggap "sudah
        // pernah ditutup" secara keliru hanya karena pernah menutup untuk
        // kombinasi sinyal yang berbeda.
        val ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL = stringPreferencesKey("adblock_last_acknowledged_signal")

        // Cache lokal hasil validasi lisensi Firestore terakhir (lihat
        // LicenseRepository). Dipakai supaya app tidak wajib online setiap
        // kali dibuka — selama cache ini masih menunjukkan lisensi valid DAN
        // belum lewat waktunya, app boleh langsung lanjut ke MainScreen tanpa
        // menunggu round-trip ke Firestore.
        val LICENSE_KEY = stringPreferencesKey("license_key")
        val LICENSE_EXPIRES_AT_MILLIS = longPreferencesKey("license_expires_at_millis")

        // ── Guard anti brute-force aktivasi lisensi (lihat LicenseAttemptGuard) ──
        // Dipersist (bukan cuma in-memory) supaya lockout TETAP berlaku walau
        // pengguna/penyerang menutup-buka ulang aplikasi untuk mereset state.
        // FAILED_ATTEMPT_COUNT: jumlah percobaan gagal berturut-turut sejak
        // window saat ini dimulai. Direset ke 0 begitu satu aktivasi berhasil.
        val LICENSE_FAILED_ATTEMPT_COUNT = intPreferencesKey("license_failed_attempt_count")
        // Epoch millis kapan window percobaan saat ini mulai dihitung.
        val LICENSE_ATTEMPT_WINDOW_START_MILLIS = longPreferencesKey("license_attempt_window_start_millis")
        // Epoch millis sampai kapan tombol aktivasi dikunci (lockout aktif).
        // null/tidak ada = tidak sedang lockout.
        val LICENSE_LOCKOUT_UNTIL_MILLIS = longPreferencesKey("license_lockout_until_millis")

        // Backend privilese pilihan pengguna ("ADB"/"ROOT"), lihat
        // AppPreferences.preferredPrivilegeBackend di atas.
        val PREFERRED_PRIVILEGE_BACKEND = stringPreferencesKey("preferred_privilege_backend")

        // Game Profile: satu string JSON berisi seluruh map packageName -> GameProfile
        // (lihat GameProfileSerializer) dan package yang sedang aktif dipantau.
        val GAME_PROFILES_JSON = stringPreferencesKey("game_profiles_json")
        val ACTIVE_GAME_PROFILE_PACKAGE = stringPreferencesKey("active_game_profile_package")

        // Reward-gate (rewarded ads): satu string JSON berisi map
        // featureKey -> RewardQuotaState (lihat RewardQuotaSerializer di
        // data/RewardQuota.kt dan core/ads/RewardGate.kt). Satu key ini
        // menampung kuota SEMUA fitur yang nanti dipasangi reward-gate,
        // supaya menambah fitur baru tidak perlu key DataStore baru.
        val REWARD_QUOTA_JSON = stringPreferencesKey("reward_quota_json")

        // Riwayat game terakhir dibuka lewat AetherX — lihat
        // AppPreferences.lastPlayedGamePackage di atas.
        val LAST_PLAYED_GAME_PACKAGE = stringPreferencesKey("last_played_game_package")
        val LAST_PLAYED_GAME_AT_MILLIS = longPreferencesKey("last_played_game_at_millis")

        // Game Booster — lihat AppPreferences.gameBoosterMode/gameBoosterDndEnabled/
        // gameBoosterFpsOverlayEnabled/gameBoosterRotationLocked/
        // gameBoosterTouchBoostEnabled di atas.
        val GAME_BOOSTER_MODE = stringPreferencesKey("game_booster_mode")
        val GAME_BOOSTER_DND_ENABLED = booleanPreferencesKey("game_booster_dnd_enabled")
        val GAME_BOOSTER_FPS_OVERLAY_ENABLED = booleanPreferencesKey("game_booster_fps_overlay_enabled")
        val GAME_BOOSTER_ROTATION_LOCKED = booleanPreferencesKey("game_booster_rotation_locked")
        val GAME_BOOSTER_TOUCH_BOOST_ENABLED = booleanPreferencesKey("game_booster_touch_boost_enabled")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            // Pilihan tema Ikuti Sistem/Terang dikunci — aplikasi hanya
            // mendukung Mode Gelap. Nilai lama yang mungkin masih tersimpan
            // di DataStore dari versi sebelumnya (SYSTEM/LIGHT) SENGAJA
            // diabaikan di sini alih-alih dibaca apa adanya, supaya
            // pengguna yang pernah memilih tema lain sebelum update ini
            // tetap otomatis kembali ke Gelap tanpa perlu reset manual.
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
            crosshairThickness = prefs[Keys.CROSSHAIR_THICKNESS] ?: 3,
            crosshairOpacity = prefs[Keys.CROSSHAIR_OPACITY] ?: 100,
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

    // setAppLanguage() dan resetAll() DIHAPUS TOTAL (fitur "pemilih Bahasa"
    // + "Reset Semua Pengaturan" di-rollback atas permintaan pengguna —
    // "kurang cocok"). Lihat juga SettingsScreen.kt/SettingsViewModel.kt
    // yang sudah tidak lagi memanggil kedua fungsi ini.

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

    /** OPSI BARU: kunci/buka kunci posisi crosshair (lihat AppPreferences.crosshairPositionLocked). */
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

    /**
     * Snapshot sinyal adblock terakhir yang SUDAH ditutup pengguna lewat
     * tombol "Tutup"/"Lihat Membership" di [com.aether.x.core.ads.AdBlockDialog],
     * atau null kalau belum pernah ditutup sama sekali. Dipakai
     * [com.aether.x.core.ads.AdBlockDialogState] untuk memutuskan apakah
     * dialog perlu ditampilkan lagi (hanya kalau sinyal SEKARANG berbeda
     * dari snapshot ini) atau tidak.
     */
    suspend fun getAdBlockAcknowledgedSignal(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL]
    }

    /** Menyimpan sinyal adblock yang baru saja ditutup/diakui pengguna. */
    suspend fun setAdBlockAcknowledgedSignal(signalKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADBLOCK_LAST_ACKNOWLEDGED_SIGNAL] = signalKey
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

    /**
     * Snapshot state guard percobaan aktivasi lisensi saat ini — dibaca oleh
     * [LicenseAttemptGuard] tiap kali pengguna menekan tombol aktivasi.
     */
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

    /** Mencatat satu percobaan aktivasi gagal beserta window & lockout terbarunya. */
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

    /** Reset guard percobaan lisensi — dipanggil begitu satu aktivasi berhasil. */
    suspend fun clearLicenseAttemptState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LICENSE_FAILED_ATTEMPT_COUNT)
            prefs.remove(Keys.LICENSE_ATTEMPT_WINDOW_START_MILLIS)
            prefs.remove(Keys.LICENSE_LOCKOUT_UNTIL_MILLIS)
        }
    }

    /** Baca preferensi backend privilese saat ini ("ADB"/"ROOT"/null, atau "SHIZUKU" lama yang akan dimigrasikan oleh PrivilegeManager) sekali (bukan Flow). */
    suspend fun getPreferredPrivilegeBackend(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.PREFERRED_PRIVILEGE_BACKEND]
    }

    /** Menyimpan backend privilese yang sengaja dipilih pengguna ("ADB" atau "ROOT"). */
    suspend fun setPreferredPrivilegeBackend(value: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PREFERRED_PRIVILEGE_BACKEND] = value }
    }

    /** Menghapus pilihan backend privilese — dipakai saat pengguna memilih "Ganti metode". */
    suspend fun clearPreferredPrivilegeBackend() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.PREFERRED_PRIVILEGE_BACKEND) }
    }

    /**
     * Menyimpan/memperbarui satu [GameProfile]. Kalau profil ini tidak punya
     * satupun tweak yang aktif ([GameProfile.hasAnyTweakEnabled] false),
     * entrinya dihapus sepenuhnya dari map alih-alih disimpan sebagai profil
     * kosong — supaya daftar tersimpan tidak menumpuk entri "semua OFF" yang
     * tidak berguna.
     */
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

    /** Menghapus profil satu game sepenuhnya (dipakai tombol "Reset profil ini"). */
    suspend fun deleteGameProfile(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON]).toMutableMap()
            current.remove(packageName)
            prefs[Keys.GAME_PROFILES_JSON] = GameProfileSerializer.serialize(current)
        }
    }

    /**
     * Menandai package name game yang SEDANG diterapkan profilnya oleh
     * [com.aether.x.core.monitor.GameProfileMonitorService] (null untuk
     * menandai "tidak ada game profile aktif" — dipanggil setelah reset).
     */
    suspend fun setActiveGameProfilePackage(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(Keys.ACTIVE_GAME_PROFILE_PACKAGE)
            } else {
                prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE] = packageName
            }
        }
    }

    /** Baca sekali (bukan Flow) package game profile yang sedang aktif, kalau ada. */
    suspend fun getActiveGameProfilePackage(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.ACTIVE_GAME_PROFILE_PACKAGE]
    }

    /** Baca sekali (bukan Flow) seluruh map profil game tersimpan. */
    suspend fun getGameProfiles(): Map<String, GameProfile> {
        val prefs = context.dataStore.data.first()
        return GameProfileSerializer.deserialize(prefs[Keys.GAME_PROFILES_JSON])
    }

    /**
     * Catat [packageName] sebagai game TERAKHIR yang dibuka lewat AetherX
     * (Dashboard "Aktivitas Game" atau Game Booster) beserta waktu sekarang
     * — dipakai untuk urutan & chip "Terakhir dipakai" di Dashboard. Dipanggil
     * setiap kali [com.aether.x.core.apps.GameLaunchTracker.launchAndTrack]
     * berhasil membuka sebuah game.
     */
    suspend fun recordGameLaunched(packageName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYED_GAME_PACKAGE] = packageName
            prefs[Keys.LAST_PLAYED_GAME_AT_MILLIS] = System.currentTimeMillis()
        }
    }

    /** Simpan preset performa TERAKHIR dipilih pengguna di Game Booster. */
    suspend fun setGameBoosterMode(mode: GameMode) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_MODE] = mode.name }
    }

    /** Toggle "Jangan Ganggu" khusus di dalam Game Booster — lihat KDoc AppPreferences.gameBoosterDndEnabled. */
    suspend fun setGameBoosterDndEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_DND_ENABLED] = enabled }
    }

    /** Toggle FPS overlay khusus di dalam Game Booster — lihat KDoc AppPreferences.gameBoosterFpsOverlayEnabled. */
    suspend fun setGameBoosterFpsOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_FPS_OVERLAY_ENABLED] = enabled }
    }

    /** Toggle Kunci Rotasi khusus di dalam Game Booster — lihat KDoc AppPreferences.gameBoosterRotationLocked. */
    suspend fun setGameBoosterRotationLocked(locked: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_ROTATION_LOCKED] = locked }
    }

    /** Toggle Akselerasi Sentuhan khusus di dalam Game Booster — lihat KDoc AppPreferences.gameBoosterTouchBoostEnabled. */
    suspend fun setGameBoosterTouchBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.GAME_BOOSTER_TOUCH_BOOST_ENABLED] = enabled }
    }

    /**
     * Baca state kuota reward-gate untuk SATU [featureKey] saja (bukan Flow
     * — dipanggil sesaat sebelum aksi dijalankan oleh [com.aether.x.core.ads.RewardGate],
     * bukan diamati terus-menerus oleh UI). Kalau belum pernah ada entri
     * untuk featureKey ini, atau [dateKey] tersimpan sudah beda dari hari
     * ini (hari sudah berganti), kembalikan state kosong untuk [dateKey]
     * yang diminta — ini yang membuat kuota gratis otomatis reset tiap hari
     * tanpa job/scheduler terpisah.
     */
    suspend fun getRewardQuota(featureKey: String, dateKey: String): RewardQuotaState {
        val prefs = context.dataStore.data.first()
        val all = RewardQuotaSerializer.deserialize(prefs[Keys.REWARD_QUOTA_JSON])
        val stored = all[featureKey] ?: return RewardQuotaState.empty(featureKey, dateKey)
        return if (stored.dateKey == dateKey) stored else RewardQuotaState.empty(featureKey, dateKey)
    }

    /** Menyimpan/memperbarui state kuota reward-gate satu [featureKey]. */
    suspend fun setRewardQuota(state: RewardQuotaState) {
        context.dataStore.edit { prefs ->
            val current = RewardQuotaSerializer.deserialize(prefs[Keys.REWARD_QUOTA_JSON]).toMutableMap()
            current[state.featureKey] = state
            prefs[Keys.REWARD_QUOTA_JSON] = RewardQuotaSerializer.serialize(current)
        }
    }
}
