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

    /**
     * FITUR BARU — Auto-Pairing: tombol "Start" ditekan, AetherX sedang
     * mendengarkan mDNS ("_adb-tls-pairing._tcp") menunggu pengguna
     * membuka Opsi Developer > Wireless debugging. Ini tahap yang
     * ditampilkan sebagai notifikasi mengambang "Searching for Pairing…"
     * di UI — belum ada host/port/kode apa pun, murni menunggu Android
     * mem-broadcast service pairing-nya sendiri.
     */
    data object SearchingForPairing : AdbConnectionState

    /**
     * Service pairing ("Pasangkan perangkat dengan kode pairing")
     * TERDETEKSI di jaringan lokal — host & port sudah otomatis didapat
     * lewat NSD ([AdbAutoPairingDiscovery]), tinggal menunggu pengguna
     * mengetik kode 6-digit yang tampil di dialog Android. Menggantikan
     * notifikasi "Searching for Pairing…" dengan "Pairing found" + dialog
     * input kode di UI.
     */
    data class PairingFound(val host: String, val port: Int) : AdbConnectionState

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

    /** FITUR BARU — Auto-Pairing: sudah menunggu cukup lama tapi service
     * pairing ("_adb-tls-pairing._tcp") tidak pernah muncul di jaringan
     * lokal — biasanya karena pengguna tidak sempat membuka dialog
     * "Pasangkan perangkat dengan kode pairing" di Wireless debugging
     * dalam batas waktu, atau perangkat berada di jaringan Wi-Fi berbeda. */
    AUTO_DISCOVERY_TIMEOUT,

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
