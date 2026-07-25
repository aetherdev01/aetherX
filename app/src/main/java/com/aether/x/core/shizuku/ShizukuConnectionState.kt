package com.aether.x.core.shizuku

/**
 * ROLLBACK dari ADB tertanam ke Shizuku murni — lihat KDoc [ShizukuManager]
 * untuk konteks lengkap kenapa rollback ini dilakukan.
 *
 * Jauh lebih sederhana dari `AdbConnectionState` (dihapus) karena AetherX
 * TIDAK PERNAH mengurus proses pairing/koneksi jaringan sendiri — semua itu
 * murni tanggung jawab app Shizuku Manager yang diinstal terpisah oleh
 * pengguna. AetherX hanya perlu tahu: apakah service Shizuku SEDANG hidup
 * (binder tersambung), dan kalau iya, apakah AetherX SUDAH diberi izin
 * memakainya.
 */
sealed interface ShizukuConnectionState {

    /** Shizuku Manager belum terinstal SAMA SEKALI di perangkat ini. */
    data object NotInstalled : ShizukuConnectionState

    /** Shizuku Manager terinstal, tapi service-nya belum/tidak sedang
     *  berjalan (binder mati) — pengguna perlu start dulu lewat app
     *  Shizuku Manager (ADB wireless/USB sekali jalan, atau root, atau
     *  Sui module tergantung metode yang mereka pakai). */
    data object ServiceNotRunning : ShizukuConnectionState

    /** Binder Shizuku hidup, tapi AetherX belum diberi izin memakainya —
     *  perlu panggil [ShizukuManager.requestPermission]. */
    data object PermissionNotGranted : ShizukuConnectionState

    /** Binder hidup DAN AetherX sudah diizinkan — siap dipakai. */
    data object Connected : ShizukuConnectionState

    /** Pengguna menolak dialog permission Shizuku. Beda dari
     *  [PermissionNotGranted] murni supaya UI bisa menampilkan pesan yang
     *  lebih spesifik ("kamu menolak izinnya") alih-alih "belum diminta". */
    data object PermissionDenied : ShizukuConnectionState
}
