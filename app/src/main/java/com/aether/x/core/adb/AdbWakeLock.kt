package com.aether.x.core.adb

import android.content.Context
import android.os.PowerManager

/**
 * FIX — "Pairing Gagal" / "Tidak bisa terhubung" (subtitle
 * [AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED]) yang HAMPIR SELALU
 * terjadi setelah pengguna membalas kode pairing lewat notifikasi
 * mengambang (RemoteInput) di ROM agresif membekukan proses background
 * (MIUI, ColorOS, dll — dilaporkan pertama kali di Redmi Note 11,
 * Android 13, tapi berlaku untuk ROM sejenis lainnya).
 *
 * Akar masalah: [AdbConnectionManager.pairAndAutoConnect] (dan
 * [AdbConnectionManager.autoReconnectSuspend]) berjalan di
 * `CoroutineScope(Dispatchers.Main.immediate)` milik object singleton —
 * TIDAK ada apa pun yang memberi tahu sistem Android bahwa proses AetherX
 * masih punya kerjaan CPU+radio aktif yang berjalan. Ketika kode pairing
 * dibalas dari notifikasi (lewat [AdbPairingReplyReceiver], sering
 * terjadi tepat sebelum layar mati/idle otomatis), [BroadcastReceiver]
 * dianggap "selesai tugasnya" oleh sistem begitu `onReceive()` return —
 * padahal proses discovery mDNS + connect TLS di baliknya (yang bisa
 * makan waktu beberapa detik sampai puluhan detik kalau perlu beberapa
 * kali percobaan) masih berjalan. MIUI (dan ROM sejenis) sangat agresif
 * membekukan CPU proses background PERSIS pada momen itu, sehingga
 * pairing certificate-nya sendiri SUDAH berhasil dikirim ke adbd (makanya
 * bukan [AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED]), tapi tahap
 * connect sesudahnya gagal karena CPU/radio proses dibekukan di tengah
 * jalan — persis gejala "kode benar tapi tetap gagal terhubung".
 *
 * Fix: pegang PARTIAL_WAKE_LOCK singkat (CPU tetap menyala walau layar
 * mati, TIDAK menyalakan layar) selama seluruh proses pairing+connect
 * berjalan, dilepas otomatis begitu selesai (sukses maupun gagal) atau
 * setelah [MAX_HOLD_MS] sebagai jaring pengaman kalau ada exception tak
 * terduga yang melewati blok try/finally pemanggil. Dipasang di
 * [AdbConnectionManager.pairAndAutoConnect] (skenario utama — balasan
 * notifikasi), [AdbConnectionManager.pair] (form manual, jaga-jaga sama),
 * dan [AdbConnectionManager.autoReconnectSuspend] (reconnect diam-diam —
 * biasanya app sedang foreground jadi risikonya lebih kecil, tapi bisa
 * juga terpicu dari [com.aether.x.core.adb.WirelessDebuggingMonitor] atau
 * `markStreamFailureAndReconnect` saat app di background, jadi tetap
 * diberi proteksi yang sama).
 */
internal object AdbWakeLock {

    private const val TAG = "AetherX:AdbPairing"
    private const val MAX_HOLD_MS = 60_000L

    private var wakeLock: PowerManager.WakeLock? = null
    private var holders = 0

    @Synchronized
    fun acquire(context: Context) {
        holders++
        if (wakeLock?.isHeld == true) return
        val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        runCatching {
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_HOLD_MS)
            }
        }.onSuccess { wakeLock = it }
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    /** Jalankan [block] dengan wake lock dipegang sepanjang eksekusinya,
     * dilepas otomatis lewat `finally` apa pun hasilnya (sukses, gagal,
     * atau exception). Aman dipanggil bertumpuk (reference counted secara
     * manual lewat [holders]) kalau suatu saat ada pemanggil bersarang. */
    suspend fun <T> withWakeLock(context: Context, block: suspend () -> T): T {
        acquire(context)
        try {
            return block()
        } finally {
            release()
        }
    }
}
