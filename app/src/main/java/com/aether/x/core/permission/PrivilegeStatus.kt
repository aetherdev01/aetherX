package com.aether.x.core.permission

enum class PrivilegeBackend { SHIZUKU, ROOT, NONE }

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
