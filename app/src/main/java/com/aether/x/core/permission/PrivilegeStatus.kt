package com.aether.x.core.permission

import com.aether.x.core.adb.AdbConnectionState

// REWORK TOTAL PERMISSION (lihat perintah rework — "buatkan sistem
// seperti shizuku langsung tertanam dalam aplikasinya... hapus semua
// yang bersangkutan dengan shizuku"): SHIZUKU diganti ADB — backend ini
// sekarang ADB tertanam milik AetherX sendiri (lihat core/adb/), BUKAN
// lagi bergantung pada aplikasi Shizuku eksternal.
enum class PrivilegeBackend { ADB, ROOT, NONE }

/**
 * Status per-request eksplisit untuk kartu ADB/Root, supaya UI selalu
 * tahu PERSIS kartu mana yang sedang memproses aksi (spinner) alih-alih
 * hanya mengandalkan boolean granted/checkingRoot global yang gampang
 * "diam" saat request gagal di tengah jalan tanpa mengubah apa pun yang
 * terlihat pengguna.
 */
enum class RequestState { IDLE, REQUESTING }

/**
 * Alasan sebuah aksi permintaan izin tidak bisa/tidak berhasil dipicu —
 * dipakai untuk menampilkan pesan yang JELAS ke pengguna, menggantikan
 * silent-return/silent-catch. ADB_* menggantikan SHIZUKU_* sepenuhnya dan
 * memetakan 1:1 dari [com.aether.x.core.adb.AdbFailureReason].
 */
enum class RequestFailureReason {
    ADB_WIRELESS_DEBUGGING_OFF,
    ADB_PAIRING_CODE_INVALID_OR_EXPIRED,
    ADB_HOST_UNREACHABLE,
    ADB_SHELL_REJECTED_NEEDS_REPAIR,
    ADB_UNKNOWN,
    ADB_ALREADY_IN_PROGRESS,
    ROOT_DENIED_OR_UNAVAILABLE,
    ROOT_ALREADY_IN_PROGRESS,
}

/**
 * Event sekali-jalan (bukan state yang menempel) hasil dari sebuah aksi
 * permintaan izin. Dikirim lewat SharedFlow (lihat PrivilegeManager.events)
 * supaya SATU kegagalan hanya pernah ditampilkan SATU KALI ke pengguna
 * (mis. Snackbar), tidak muncul lagi berulang setiap kali screen recompose
 * atau rotate seperti yang akan terjadi kalau ini disimpan di StateFlow biasa.
 */
sealed interface RequestFeedback {
    data class Failed(val backend: PrivilegeBackend, val reason: RequestFailureReason) : RequestFeedback
    data class Granted(val backend: PrivilegeBackend) : RequestFeedback
}

/**
 * Snapshot kondisi akses privilese AetherX saat ini.
 *
 * - [adbState]       = tahap koneksi ADB tertanam saat ini (lihat
 *   [AdbConnectionState]) — menggantikan shizukuAvailable/shizukuGranted
 *   boolean lama dengan state machine yang lebih deskriptif.
 * - [rootAvailable]  = null berarti belum pernah dicek, true/false setelah dicek.
 * - [rootGranted]    = akses root untuk AetherX sudah disetujui.
 * - [preferredBackend] = backend yang SENGAJA dipilih pengguna di layar Izin
 *   Akses (lihat PrivilegeManager.selectBackend). NONE berarti belum memilih
 *   apa pun (mis. baru pertama kali buka app / setelah "Ganti metode").
 *   Ini yang membuat ADB dan Root tidak pernah aktif bersamaan: begitu
 *   satu backend dipilih, backend lain dianggap tidak aktif oleh app
 *   walaupun secara sistem izinnya masih granted, supaya tweak (mis. governor
 *   CPU, DND, dsb) hanya pernah dieksekusi lewat SATU jalur dan tidak saling
 *   tabrakan.
 */
data class PrivilegeStatus(
    val adbState: AdbConnectionState = AdbConnectionState.NotPaired,
    val rootAvailable: Boolean? = null,
    val rootGranted: Boolean = false,
    val checkingRoot: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.NONE,
    // Status request eksplisit per kartu, supaya PermissionMethodCard bisa
    // menampilkan spinner/label "Meminta…" SELAMA proses berlangsung, bukan
    // cuma diam sampai granted berubah (yang kalau gagal/timeout, tombol
    // akan terlihat "tidak merespon").
    val adbRequestState: RequestState = RequestState.IDLE,
    val rootRequestState: RequestState = RequestState.IDLE,
) {
    val adbGranted: Boolean get() = adbState == AdbConnectionState.Connected

    /**
     * Backend yang benar-benar aktif dipakai app untuk menjalankan tweak.
     *
     * Kalau pengguna sudah memilih salah satu ([preferredBackend] != NONE),
     * backend itulah yang dipakai — SELAMA backend itu memang masih granted.
     * Backend yang tidak dipilih tidak pernah dipakai walau granted, supaya
     * tidak ada dua sumber privilese aktif berbarengan.
     *
     * Kalau belum ada preferensi (mis. pengguna baru), fallback: ADB
     * diutamakan kalau connected, baru root.
     */
    val activeBackend: PrivilegeBackend
        get() = when (preferredBackend) {
            PrivilegeBackend.ADB -> if (adbGranted) PrivilegeBackend.ADB else PrivilegeBackend.NONE
            PrivilegeBackend.ROOT -> if (rootGranted) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeBackend.NONE -> when {
                adbGranted -> PrivilegeBackend.ADB
                rootGranted -> PrivilegeBackend.ROOT
                else -> PrivilegeBackend.NONE
            }
        }

    val hasAccess: Boolean get() = activeBackend != PrivilegeBackend.NONE

    /** Semua izin pendukung (di luar ADB/root) sudah aktif. */
    val hasAllSupportingPermissions: Boolean
        get() = writeSettingsGranted && overlayGranted && notificationsGranted
}
