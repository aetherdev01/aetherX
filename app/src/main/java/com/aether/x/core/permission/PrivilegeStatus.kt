package com.aether.x.core.permission

enum class PrivilegeBackend { SHIZUKU, ROOT, NONE }

/**
 * REWORK TOTAL PERMISSION (lihat perintah rework — "terkadang bug tidak
 * bisa di pencet dan kadang permission magisk & shizuku tidak trigger"):
 * status per-request eksplisit untuk kartu Shizuku/Root, supaya UI selalu
 * tahu PERSIS kartu mana yang sedang memproses aksi (spinner) alih-alih
 * hanya mengandalkan boolean granted/checkingRoot global yang gampang
 * "diam" saat request gagal di tengah jalan tanpa mengubah apa pun yang
 * terlihat pengguna.
 */
enum class RequestState { IDLE, REQUESTING }

/**
 * Alasan sebuah aksi permintaan izin tidak bisa/tidak berhasil dipicu —
 * dipakai untuk menampilkan pesan yang JELAS ke pengguna, menggantikan
 * silent-return/silent-catch yang ada sebelumnya (mis. requestShizukuPermission
 * yang langsung `return` diam-diam kalau server belum hidup, atau
 * requestRoot yang catch Throwable jadi false tanpa penjelasan apa pun).
 */
enum class RequestFailureReason {
    SHIZUKU_SERVER_NOT_RUNNING,
    SHIZUKU_TOO_OLD,
    SHIZUKU_ALREADY_IN_PROGRESS,
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
 * - [shizukuAvailable] = server Shizuku/Sui sedang berjalan (binder hidup).
 * - [shizukuGranted]   = izin Shizuku untuk AetherX sudah disetujui.
 * - [rootAvailable]    = null berarti belum pernah dicek, true/false setelah dicek.
 * - [rootGranted]      = akses root untuk AetherX sudah disetujui.
 * - [preferredBackend] = backend yang SENGAJA dipilih pengguna di layar Izin
 *   Akses (lihat PrivilegeManager.selectBackend). NONE berarti belum memilih
 *   apa pun (mis. baru pertama kali buka app / setelah "Ganti metode").
 *   Ini yang membuat Shizuku dan Root tidak pernah aktif bersamaan: begitu
 *   satu backend dipilih, backend lain dianggap tidak aktif oleh app
 *   walaupun secara sistem izinnya masih granted, supaya tweak (mis. governor
 *   CPU, DND, dsb) hanya pernah dieksekusi lewat SATU jalur dan tidak saling
 *   tabrakan.
 */
data class PrivilegeStatus(
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val rootAvailable: Boolean? = null,
    val rootGranted: Boolean = false,
    val checkingRoot: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.NONE,
    // FITUR BARU (rework permission): status request eksplisit per kartu,
    // supaya PermissionMethodCard bisa menampilkan spinner/label "Meminta…"
    // SELAMA proses berlangsung, bukan cuma diam sampai granted berubah
    // (yang kalau gagal/timeout, tombol akan terlihat "tidak merespon").
    val shizukuRequestState: RequestState = RequestState.IDLE,
    val rootRequestState: RequestState = RequestState.IDLE,
) {
    /**
     * Backend yang benar-benar aktif dipakai app untuk menjalankan tweak.
     *
     * Kalau pengguna sudah memilih salah satu ([preferredBackend] != NONE),
     * backend itulah yang dipakai — SELAMA backend itu memang masih granted.
     * Backend yang tidak dipilih tidak pernah dipakai walau granted, supaya
     * tidak ada dua sumber privilese aktif berbarengan.
     *
     * Kalau belum ada preferensi (mis. pengguna baru), fallback ke perilaku
     * lama: Shizuku diutamakan kalau granted, baru root.
     */
    val activeBackend: PrivilegeBackend
        get() = when (preferredBackend) {
            PrivilegeBackend.SHIZUKU -> if (shizukuAvailable && shizukuGranted) {
                PrivilegeBackend.SHIZUKU
            } else {
                PrivilegeBackend.NONE
            }
            PrivilegeBackend.ROOT -> if (rootGranted) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeBackend.NONE -> when {
                shizukuAvailable && shizukuGranted -> PrivilegeBackend.SHIZUKU
                rootGranted -> PrivilegeBackend.ROOT
                else -> PrivilegeBackend.NONE
            }
        }

    val hasAccess: Boolean get() = activeBackend != PrivilegeBackend.NONE

    /** Semua izin pendukung (di luar Shizuku/root) sudah aktif. */
    val hasAllSupportingPermissions: Boolean
        get() = writeSettingsGranted && overlayGranted && notificationsGranted
}
