package com.aether.x.core.adb

/**
 * Tahap koneksi ADB tertanam AetherX — dipakai UI (PermissionSetupScreen)
 * untuk menampilkan progres yang JELAS di setiap langkah, menggantikan
 * boolean granted/tidak-granted polos ala Shizuku lama yang bikin
 * pengguna tidak tahu prosesnya berhenti di tahap mana kalau gagal.
 */
sealed interface AdbConnectionState {

    /** Belum pernah di-pair sama sekali di perangkat ini. */
    data object NotPaired : AdbConnectionState

    /** Sudah pernah pairing, tapi belum ada koneksi shell aktif saat ini
     * (mis. baru buka app, atau Wireless debugging baru saja dimatikan). */
    data object PairedNotConnected : AdbConnectionState

    /** Sedang mencoba pairing dengan kode 6-digit dari dialog Wireless debugging. */
    data object Pairing : AdbConnectionState

    /** Sedang mencoba membentuk koneksi shell (setelah pairing sukses, atau
     * saat auto-reconnect ke sesi yang sudah pernah di-pair). */
    data object Connecting : AdbConnectionState

    /** Shell aktif dan siap dipakai untuk menjalankan tweak. */
    data object Connected : AdbConnectionState

    data class Failed(val reason: AdbFailureReason, val detail: String? = null) : AdbConnectionState
}

enum class AdbFailureReason {
    /** Wireless debugging tidak aktif di Pengaturan > Opsi developer. */
    WIRELESS_DEBUGGING_OFF,

    /** Kode pairing 6-digit salah, atau dialog pairing sudah kedaluwarsa (biasanya ~60 detik). */
    PAIRING_CODE_INVALID_OR_EXPIRED,

    /** Host/port yang dimasukkan tidak bisa dihubungi (typo, beda jaringan Wi-Fi, dsb). */
    HOST_UNREACHABLE,

    /** Sudah pernah pairing, tapi adbd menolak koneksi shell — biasanya
     * karena pengguna mencabut akses USB debugging secara manual di
     * Pengaturan ("Cabut semua izin debugging"), sehingga key lama tidak
     * dikenali lagi dan wajib pairing ulang dari nol. */
    SHELL_REJECTED_NEEDS_REPAIR,

    /** Kesalahan tak terduga lain (io error, dsb). */
    UNKNOWN,
}
