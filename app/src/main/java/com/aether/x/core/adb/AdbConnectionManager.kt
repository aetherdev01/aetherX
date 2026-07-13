package com.aether.x.core.adb

import android.content.Context
import dadb.Dadb
import dadb.AdbPairingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * REWORK TOTAL PERMISSION — ADB tertanam (lihat perintah rework "buatkan
 * sistem seperti shizuku langsung tertanam dalam aplikasinya... cara
 * pairingnya juga sama seperti shizuku pakai wireless adb").
 *
 * CATATAN JARINGAN: koneksi [dadb] (baik pairing maupun shell) memakai
 * `java.net.Socket`/`SSLSocket` mentah ke IP lokal (jaringan Wi-Fi yang
 * sama, bukan internet), BUKAN `HttpURLConnection`/OkHttp berbasis skema
 * URL — sehingga `android:usesCleartextTraffic="false"` di
 * AndroidManifest TIDAK memblokir koneksi ini (flag itu hanya berlaku
 * untuk API jaringan berbasis skema http/https). Kalau di masa depan
 * ternyata ada masalah koneksi terkait kebijakan jaringan, submit
 * `network_security_config.xml` dengan `<domain-config
 * cleartextTrafficPermitted="true">` khusus rentang IP lokal — TAPI ini
 * belum diperlukan berdasarkan cara [dadb] bekerja saat ini.
 *
 * Sumber tunggal kebenaran untuk koneksi ADB tertanam AetherX. Menggantikan
 * peran Shizuku Manager SEPENUHNYA:
 * - [pair] = tahap "Mulai Penyandingan" di referensi AxManager: pengguna
 *   membuka Wireless debugging > "Pasangkan perangkat dengan kode pairing",
 *   memasukkan host:port dan kode 6-digit yang muncul di dialog itu ke
 *   AetherX.
 * - Setelah pairing sukses SEKALI, [connect] bisa dipanggil ulang
 *   berkali-kali (termasuk otomatis oleh [autoReconnect]) TANPA perlu kode
 *   pairing lagi — hanya perlu host:port dari layar utama Wireless
 *   debugging (bukan dialog pairing), sama seperti alur Shizuku/AxManager
 *   asli. Host:port ini disimpan (lihat [AdbPreferences]) supaya AetherX
 *   bisa auto-reconnect setiap dibuka tanpa pengguna mengetik ulang.
 *
 * Kenapa ini "tidak gampang ter-reset" dibanding sekadar bergantung app
 * Shizuku eksternal: seluruh proses (keypair, koneksi socket, shell
 * client) berjalan di dalam proses AetherX sendiri, memakai
 * [kotlinx.coroutines] biasa — tidak ada dependensi ke proses/app lain
 * yang bisa di-force-stop terpisah oleh sistem atau pengguna tanpa
 * sengaja (yang selama ini jadi penyebab paling umum Shizuku "tiba-tiba
 * kepencet mati" tanpa AetherX sadar).
 */
object AdbConnectionManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val connectMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var keyManager: AdbKeyManager
    private lateinit var preferences: AdbPreferences

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.NotPaired)
    val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

    /** Instance shell aktif saat ini, atau null kalau belum/tidak connected.
     * [EmbeddedShellExecutor] membaca ini setiap kali `exec()` dipanggil. */
    @Volatile
    private var dadb: Dadb? = null

    private var initialized = false

    /** Panggil sekali dari Application.onCreate — memuat host:port
     * tersimpan (jika ada) dan langsung mencoba auto-reconnect diam-diam
     * di background, TANPA memunculkan apa pun ke pengguna kalau gagal
     * (mis. Wireless debugging memang belum dinyalakan pagi itu). */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        keyManager = AdbKeyManager(appContext)
        preferences = AdbPreferences(appContext)

        scope.launch {
            val saved = preferences.getSavedHostPort()
            _state.value = if (saved != null) {
                AdbConnectionState.PairedNotConnected
            } else {
                AdbConnectionState.NotPaired
            }
            if (saved != null) {
                autoReconnect()
            }
        }
    }

    /**
     * Tahap 1 — "Mulai Penyandingan": kirim host, port pairing (BEDA dari
     * port koneksi biasa — ini port yang ditampilkan di dialog "Pasangkan
     * perangkat dengan kode pairing" di Wireless debugging), dan kode
     * 6-digit dari dialog yang sama.
     *
     * Kalau sukses, langsung lanjut ke [connect] pakai host + PORT KONEKSI
     * (yang beda dari port pairing tadi — diambil dari layar utama
     * Wireless debugging) yang pengguna masukkan terpisah di form yang
     * sama (lihat PermissionSetupScreen), lalu host:port itu DISIMPAN
     * permanen supaya sesi berikutnya tidak perlu pairing ulang.
     */
    suspend fun pair(
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
        connectHost: String,
        connectPort: Int,
    ): AdbConnectionState = connectMutex.withLock {
        _state.value = AdbConnectionState.Pairing
        val keyPair = keyManager.getOrCreateKeyPair()

        val paired = withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(15_000) {
                    AdbPairingClient(pairingHost, pairingPort, pairingCode, keyPair).execute()
                    true
                }
            }.getOrNull()
        }

        if (paired != true) {
            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED,
                detail = "Pairing ke $pairingHost:$pairingPort gagal atau kode kedaluwarsa.",
            )
            _state.value = failure
            return failure
        }

        // Pairing sukses — adbd sekarang mengenal public key AetherX.
        // Lanjut langsung connect pakai port KONEKSI (bukan port pairing).
        preferences.saveHostPort(connectHost, connectPort)
        return connectLocked(connectHost, connectPort)
    }

    /**
     * Tahap 2 — connect memakai host:port koneksi (bukan pairing). Dipanggil
     * langsung setelah [pair] sukses, dan juga dipanggil ulang oleh
     * [autoReconnect] setiap AetherX dibuka, TANPA memerlukan kode pairing
     * lagi selama key sudah dikenal adbd (lihat AdbKeyManager).
     */
    suspend fun connect(host: String, port: Int): AdbConnectionState = connectMutex.withLock {
        connectLocked(host, port)
    }

    private suspend fun connectLocked(host: String, port: Int): AdbConnectionState {
        _state.value = AdbConnectionState.Connecting
        val keyPair = keyManager.getOrCreateKeyPair()

        val result = withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(10_000) {
                    Dadb.create(host, port, keyPair)
                }
            }.getOrNull()?.getOrNull()
        }

        return if (result != null) {
            dadb = result
            preferences.saveHostPort(host, port)
            val connected = AdbConnectionState.Connected
            _state.value = connected
            connected
        } else {
            dadb = null
            // BUG FIX pola sama seperti rework requestShizukuPermission
            // sebelumnya: SELALU beri tahu alasan kegagalan yang spesifik,
            // jangan pernah diam. Kalau sudah pernah pairing tapi sekarang
            // ditolak, kemungkinan besar pengguna mencabut izin debugging
            // secara manual — arahkan untuk pairing ulang, bukan sekadar
            // "gagal" generik.
            val hadPreviousPairing = preferences.getSavedHostPort() != null
            val failure = AdbConnectionState.Failed(
                reason = if (hadPreviousPairing) {
                    AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR
                } else {
                    AdbFailureReason.HOST_UNREACHABLE
                },
                detail = "Tidak bisa terhubung ke $host:$port.",
            )
            _state.value = failure
            failure
        }
    }

    /**
     * Dipanggil dari [init] dan dari MainActivity.onResume — kalau sudah
     * ada host:port tersimpan dari pairing sebelumnya, coba sambungkan
     * ulang diam-diam. INI yang membuat koneksi "tidak gampang ter-reset":
     * pengguna tidak perlu membuka form pairing lagi setiap kali membuka
     * AetherX atau kembali dari background, selama Wireless debugging
     * masih aktif di perangkat.
     */
    fun autoReconnect() {
        scope.launch {
            val saved = preferences.getSavedHostPort() ?: return@launch
            if (_state.value == AdbConnectionState.Connected) return@launch
            connect(saved.first, saved.second)
        }
    }

    /** Executor yang dipakai [com.aether.x.core.permission.PrivilegeManager]
     * untuk menjalankan tweak. Null kalau belum ada koneksi shell aktif. */
    fun currentDadb(): Dadb? = dadb

    fun isConnected(): Boolean = _state.value == AdbConnectionState.Connected

    /**
     * "Lupakan perangkat ini" — dipanggil HANYA dari aksi eksplisit
     * pengguna di layar Izin Akses / Pengaturan. Menutup koneksi aktif,
     * menghapus host:port tersimpan, DAN menghapus keypair (supaya
     * pairing berikutnya benar-benar dari nol, bukan cuma lupa alamat
     * tapi masih dikenali key lama oleh adbd).
     */
    fun forgetPairing() {
        runCatching { dadb?.close() }
        dadb = null
        preferences.clearHostPort()
        keyManager.forgetKeyPair()
        _state.value = AdbConnectionState.NotPaired
    }

    fun disconnectOnly() {
        runCatching { dadb?.close() }
        dadb = null
        if (_state.value == AdbConnectionState.Connected) {
            _state.value = AdbConnectionState.PairedNotConnected
        }
    }
}
