package com.aether.x.core.adb

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FITUR BARU — perbaikan alur "server Wireless debugging mati" (lihat
 * perintah: "ketika server wireless debugging mati, di AXKM ga perlu
 * setup isi kode 6 digit lagi biar ga ribet / buatkan opsi aktifkan
 * Wireless Debugging di tab Settings/Dashboard khusus no root & muncul
 * ketika Wireless Debugging belum aktif dan hilangkan saat aktif").
 *
 * Android menyimpan status ON/OFF toggle "Wireless debugging" di
 * `Settings.Global` dengan key `adb_wifi_enabled` (0/1) — key ini publik
 * dan bisa dibaca APLIKASI MANA PUN tanpa izin khusus (beda dari
 * `Settings.Secure`/`Settings.System` yang beberapa key-nya dibatasi),
 * PERSIS mekanisme yang dipakai launcher/quick-settings pihak ketiga untuk
 * menampilkan status toggle ini. Dibaca via reflection key string (bukan
 * konstanta `Settings.Global.ADB_WIFI_ENABLED` yang memang tidak
 * diekspos publik oleh Android SDK) sehingga tidak butuh dependensi
 * hidden-API apa pun.
 *
 * Object ini SENGAJA terpisah dari [AdbConnectionManager] — tujuannya
 * murni membaca status TOGGLE Wireless debugging sistem (ON/OFF), BUKAN
 * status koneksi shell AetherX ([AdbConnectionState]). Keduanya bisa
 * berbeda: contoh, Wireless debugging bisa ON tapi AetherX belum/tidak
 * terhubung (baru buka app), atau sebaliknya Wireless debugging baru saja
 * dimatikan pengguna padahal AetherX masih menganggap statusnya Connected
 * sesaat sebelum percobaan shell berikutnya gagal.
 *
 * [state] dipakai kartu "Aktifkan Wireless Debugging" (khusus mode
 * No Root/ADB) di Dashboard — kartu itu HANYA muncul selama
 * `state.value == false`, dan otomatis hilang begitu pengguna
 * menyalakannya kembali dari Pengaturan, TANPA perlu pairing ulang / isi
 * kode 6-digit lagi (selama sudah pernah pairing sebelumnya, koneksi
 * lama otomatis disambungkan ulang lewat [AdbConnectionManager.autoReconnect],
 * dipicu [ContentObserver] di bawah).
 */
object WirelessDebuggingMonitor {

    // Key resmi: Settings.Global.ADB_WIFI_ENABLED (@hide di source AOSP,
    // tidak diekspos SDK publik) — nilai string-nya stabil sejak Android 11
    // dan didokumentasikan di banyak referensi AOSP/OEM sebagai "adb_wifi_enabled".
    private const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"

    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    private var registered = false
    private var observer: ContentObserver? = null

    /** Baca status saat ini secara langsung (sinkron, tanpa observer) —
     * dipakai [refresh] dan tiap kali layar kembali ke foreground. */
    fun isEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, KEY_ADB_WIFI_ENABLED, 0) == 1
    }.getOrDefault(false)

    /** Cek ulang status Wireless debugging & perbarui [state]. Aman
     * dipanggil berkali-kali (mis. tiap ON_RESUME, sama pola dengan
     * [com.aether.x.core.permission.PrivilegeManager.refreshSupportingPermissions]). */
    fun refresh(context: Context) {
        _state.value = isEnabled(context.applicationContext)
    }

    /**
     * Mulai memantau perubahan toggle secara reaktif lewat
     * [ContentObserver] pada URI `Settings.Global` — supaya kartu
     * "Aktifkan Wireless Debugging" langsung hilang begitu pengguna
     * menyalakannya dari Pengaturan, tanpa harus menunggu pengguna kembali
     * ke AetherX dulu (ON_RESUME). Aman dipanggil berkali-kali (idempotent).
     *
     * Begitu toggle terdeteksi berubah dari OFF -> ON, [AdbConnectionManager.autoReconnect]
     * DIPANGGIL OTOMATIS di sini — inilah bagian yang menghapus kebutuhan
     * "isi kode 6 digit lagi": selama pernah pairing sebelumnya, host:port
     * tersimpan langsung dicoba disambungkan ulang begitu server Wireless
     * debugging hidup lagi, tanpa aksi apa pun dari pengguna selain
     * menyalakan toggle-nya.
     */
    fun startObserving(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        refresh(appContext)
        val handler = Handler(Looper.getMainLooper())
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val wasEnabled = _state.value
                val nowEnabled = isEnabled(appContext)
                _state.value = nowEnabled
                if (!wasEnabled && nowEnabled) {
                    // Server baru saja menyala lagi -> coba sambungkan ulang
                    // diam-diam dari pairing tersimpan, TANPA memunculkan
                    // dialog/notifikasi kode 6-digit apa pun kalau memang
                    // tidak diperlukan.
                    AdbConnectionManager.autoReconnect()
                }
            }
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ADB_WIFI_ENABLED),
                false,
                contentObserver,
            )
            observer = contentObserver
            registered = true
        }
    }

    fun stopObserving(context: Context) {
        val current = observer ?: return
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(current) }
        observer = null
        registered = false
    }
}
