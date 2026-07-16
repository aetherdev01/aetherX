package com.aether.x.core.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
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
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * REWORK TOTAL PERMISSION — ADB tertanam (lihat perintah rework "buatkan
 * sistem seperti shizuku langsung tertanam dalam aplikasinya... cara
 * pairingnya juga sama seperti shizuku pakai wireless adb").
 *
 * KOREKSI ARSITEKTUR (setelah dua percobaan sebelumnya gagal — lihat
 * riwayat: "dadb" ternyata tidak punya wireless pairing sama sekali, dan
 * menulis protokol pairing sendiri dari nol terlalu berisiko tanpa bisa
 * dites): sekarang memakai **libadb-android**
 * (`com.github.MuntashirAkon:libadb-android`, dipakai production oleh App
 * Manager) via subclass [AetherXAdbConnectionManager] dari
 * `AbsAdbConnectionManager` — pairing wireless Android 11+ (SPAKE2 + TLS
 * 1.3) SUDAH diimplementasikan lengkap di library itu, AetherX tinggal
 * memanggil `pair(host, port, code)` dan `connect(host, port)`.
 *
 * Sumber tunggal kebenaran untuk koneksi ADB tertanam AetherX. Menggantikan
 * peran Shizuku Manager SEPENUHNYA — pairing (form host, port pairing,
 * kode 6-digit) dan koneksi shell (host + port koneksi, TANPA kode
 * pairing lagi setelah pairing pertama sukses) berjalan seperti alur
 * Shizuku/AxManager asli, tapi seluruhnya berjalan di dalam proses
 * AetherX sendiri.
 */
object AdbConnectionManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val connectMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var keyManager: AdbKeyManager
    private lateinit var preferences: AdbPreferences
    private lateinit var connection: AetherXAdbConnectionManager

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.NotPaired)
    val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

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
        connection = AetherXAdbConnectionManager(keyManager)

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
        connectPort: Int,
    ): AdbConnectionState = connectMutex.withLock {
        _state.value = AdbConnectionState.Pairing

        val paired = withContext(Dispatchers.IO) {
            runCatching {
                connection.pair(pairingHost, pairingPort, pairingCode)
            }
        }

        if (paired.isFailure) {
            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED,
                detail = paired.exceptionOrNull()?.message ?: "Pairing ke $pairingHost:$pairingPort gagal atau kode kedaluwarsa.",
            )
            _state.value = failure
            return failure
        }

        // Pairing sukses — adbd sekarang mengenal certificate AetherX.
        // Lanjut langsung connect pakai port KONEKSI (bukan port pairing).
        preferences.saveHostPort(pairingHost, connectPort)
        return connectLocked(pairingHost, connectPort)
    }

    /**
     * Tahap 2 — connect memakai host:port koneksi (bukan pairing). Dipanggil
     * langsung setelah [pair] sukses, dan juga dipanggil ulang oleh
     * [autoReconnect] setiap AetherX dibuka, TANPA memerlukan kode pairing
     * lagi selama certificate sudah dikenal adbd (lihat AdbKeyManager).
     */
    suspend fun connect(host: String, port: Int): AdbConnectionState = connectMutex.withLock {
        connectLocked(host, port)
    }

    private suspend fun connectLocked(host: String, port: Int): AdbConnectionState {
        _state.value = AdbConnectionState.Connecting

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (connection.isConnected) {
                    connection.disconnect()
                }
                connection.connect(host, port, timeoutMs = 10_000)
            }
        }

        val success = result.getOrDefault(false)
        return if (success) {
            preferences.saveHostPort(host, port)
            val connected = AdbConnectionState.Connected
            _state.value = connected
            connected
        } else {
            // BUG FIX pola sama seperti rework sebelumnya: SELALU beri
            // tahu alasan kegagalan yang spesifik, jangan pernah diam.
            // Kalau sudah pernah pairing tapi sekarang ditolak,
            // kemungkinan besar pengguna mencabut izin debugging secara
            // manual — arahkan untuk pairing ulang, bukan sekadar "gagal"
            // generik.
            val hadPreviousPairing = preferences.getSavedHostPort() != null
            val exception = result.exceptionOrNull()
            val failure = AdbConnectionState.Failed(
                reason = when {
                    exception != null && exception.javaClass.simpleName.contains("PairingRequired", ignoreCase = true) ->
                        AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR
                    hadPreviousPairing -> AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR
                    else -> AdbFailureReason.HOST_UNREACHABLE
                },
                detail = exception?.message ?: "Tidak bisa terhubung ke $host:$port.",
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

    /**
     * Jalankan satu perintah shell lewat koneksi yang sedang aktif.
     * Dipakai oleh [com.aether.x.core.shell.EmbeddedShellExecutor].
     *
     * Exit code TIDAK disediakan langsung oleh `AdbStream` (murni
     * InputStream/OutputStream seperti Process biasa) — dipakai trik shell
     * standar `command; echo <marker>$?` supaya exit code bisa diparse
     * dari output tanpa command tambahan yang mengubah semantik perintah
     * aslinya.
     */
    suspend fun execShell(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val marker = "__AETHERX_EXIT_CODE__"
        val wrapped = "$command; echo \"$marker\$?\""
        val stream: AdbStream = connection.openStream("shell:$wrapped")
        try {
            val output = stream.openInputStream().use { input -> input.readBytes() }.toString(Charsets.UTF_8)
            val markerIndex = output.lastIndexOf(marker)
            if (markerIndex == -1) {
                // Marker tidak ditemukan (mis. koneksi terputus di
                // tengah) — anggap gagal, tampilkan seluruh output apa
                // adanya supaya tidak ada informasi yang hilang.
                return@withContext 1 to output
            }
            val exitCodeText = output.substring(markerIndex + marker.length).trim()
            val exitCode = exitCodeText.toIntOrNull() ?: 1
            val cleanOutput = output.substring(0, markerIndex)
            exitCode to cleanOutput
        } finally {
            stream.close()
        }
    }

    fun isConnected(): Boolean = _state.value == AdbConnectionState.Connected && ::connection.isInitialized && connection.isConnected

    /**
     * "Lupakan perangkat ini" — dipanggil HANYA dari aksi eksplisit
     * pengguna di layar Izin Akses / Pengaturan. Menutup koneksi aktif,
     * menghapus host:port tersimpan, DAN menghapus keypair+certificate
     * (supaya pairing berikutnya benar-benar dari nol, bukan cuma lupa
     * alamat tapi masih dikenali certificate lama oleh adbd).
     */
    fun forgetPairing() {
        runCatching { if (::connection.isInitialized) connection.disconnect() }
        keyManager.forgetIdentity()
        _state.value = AdbConnectionState.NotPaired
        scope.launch { preferences.clearHostPort() }
    }

    fun disconnectOnly() {
        runCatching { if (::connection.isInitialized) connection.disconnect() }
        if (_state.value == AdbConnectionState.Connected) {
            _state.value = AdbConnectionState.PairedNotConnected
        }
    }

    /**
     * Subclass konkret [AbsAdbConnectionManager] milik libadb-android —
     * mengikuti PERSIS pola resmi yang dicontohkan README library
     * tersebut (lihat CATATAN_API_LIBADB.md), memakai [AdbKeyManager]
     * sebagai sumber identitas kriptografi AetherX.
     */
    private class AetherXAdbConnectionManager(
        keyManager: AdbKeyManager,
    ) : AbsAdbConnectionManager() {

        private val identity = keyManager.getOrCreateIdentity()

        init {
            setApi(Build.VERSION.SDK_INT)
        }

        fun connect(host: String, port: Int, timeoutMs: Long): Boolean {
            setTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            return connect(host, port)
        }

        override fun getPrivateKey(): PrivateKey = identity.privateKey

        override fun getCertificate(): Certificate = identity.certificate

        override fun getDeviceName(): String = "AetherX"
    }
}
