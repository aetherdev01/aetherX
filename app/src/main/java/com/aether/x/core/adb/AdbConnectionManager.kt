package com.aether.x.core.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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

    // FITUR BARU — Auto-Pairing: job discovery yang sedang berjalan (kalau
    // ada), supaya bisa dibatalkan kalau pengguna menekan "Batal" di
    // notifikasi mengambang "Searching for Pairing…" sebelum service
    // pairing ditemukan.
    private var autoPairingJob: Job? = null

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
        context: Context,
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int,
    ): AdbConnectionState = AdbWakeLock.withWakeLock(context) {
        connectMutex.withLock {
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
                return@withWakeLock failure
            }

            // Pairing sukses — adbd sekarang mengenal certificate AetherX.
            // Lanjut langsung connect pakai port KONEKSI (bukan port pairing).
            preferences.saveHostPort(pairingHost, connectPort)
            connectLocked(pairingHost, connectPort)
        }
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

    // -------------------------------------------------------------------
    // FITUR BARU — Auto-Pairing (lihat perintah rework: tombol "Start"
    // tunggal, tanpa form IP/port manual sama sekali — host & port pairing
    // otomatis didapat lewat NSD/mDNS, PERSIS mekanisme broadcast yang
    // dipakai Android sendiri untuk Wireless debugging).
    //
    // Alur lengkap (lihat juga AdbAutoPairingDiscovery):
    //   1. [startAutoPairing] dipanggil dari tombol "Start" di kartu ->
    //      state jadi [AdbConnectionState.SearchingForPairing] -> UI
    //      menampilkan notifikasi mengambang "Searching for Pairing…".
    //   2. AetherX mendengarkan mDNS "_adb-tls-pairing._tcp" di background.
    //      Begitu pengguna membuka Opsi Developer > Wireless debugging >
    //      "Pasangkan perangkat dengan kode pairing", Android otomatis
    //      mem-broadcast service itu (TANPA aksi tambahan apa pun dari
    //      pengguna selain membuka dialog itu) -> host+port ditemukan.
    //   3. State jadi [AdbConnectionState.PairingFound] (host+port sudah
    //      ada) -> UI mengganti notifikasi jadi "Pairing found" dan
    //      menampilkan dialog input kode 6-digit SAJA (tidak ada field
    //      IP/port apa pun yang perlu diisi pengguna).
    //   4. Pengguna mengetik kode dari dialog Android -> [confirmAutoPairingCode]
    //      dipanggil -> pairing (host+port hasil auto-discovery + kode
    //      yang diketik) -> port koneksi TIDAK perlu diminta terpisah lagi:
    //      begitu pairing sukses, AetherX langsung mendengarkan
    //      "_adb-tls-connect._tcp" sesaat untuk mendapatkan port koneksi
    //      terbaru secara otomatis juga (lihat [pairAndAutoConnect]).
    // -------------------------------------------------------------------

    /**
     * Tahap 1 auto-pairing — mulai mendengarkan mDNS untuk service pairing.
     * Aman dipanggil ulang (job lama dibatalkan dulu) kalau pengguna
     * menekan Start lagi setelah timeout/gagal sebelumnya.
     */
    fun startAutoPairing(context: Context) {
        autoPairingJob?.cancel()
        _state.value = AdbConnectionState.SearchingForPairing
        val job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AdbAutoPairingDiscovery.discoverPairingService(context) }
            }
            result.fold(
                onSuccess = { service ->
                    _state.value = AdbConnectionState.PairingFound(service.host, service.port)
                },
                onFailure = { error ->
                    val reason = if (error is TimeoutCancellationException) {
                        AdbFailureReason.AUTO_DISCOVERY_TIMEOUT
                    } else {
                        AdbFailureReason.UNKNOWN
                    }
                    _state.value = AdbConnectionState.Failed(
                        reason = reason,
                        detail = error.message ?: "Tidak menemukan service pairing di jaringan lokal.",
                    )
                },
            )
        }
        autoPairingJob = job
        job.invokeOnCompletion { if (autoPairingJob === job) autoPairingJob = null }
    }

    /** Membatalkan pencarian "Searching for Pairing…" YANG SEDANG berjalan,
     * atau menutup dialog kode setelah service pairing ditemukan (pengguna
     * menekan Batal di kedua tahap tersebut). Tidak berpengaruh apa pun
     * kalau pairing sudah lanjut ke tahap Pairing/Connecting (kode sudah
     * terlanjur dikirim, tidak bisa dibatalkan lagi di tengah jalan). */
    fun cancelAutoPairing() {
        autoPairingJob?.cancel()
        autoPairingJob = null
        if (_state.value == AdbConnectionState.SearchingForPairing || _state.value is AdbConnectionState.PairingFound) {
            _state.value = AdbConnectionState.NotPaired
        }
    }

    /**
     * Tahap 2 auto-pairing — dipanggil setelah pengguna mengetik kode
     * 6-digit di dialog "Pairing found". Host+port SUDAH didapat otomatis
     * dari [startAutoPairing], jadi hanya kode yang perlu diberikan di sini.
     *
     * Setelah pairing sukses, port koneksi (beda dari port pairing) JUGA
     * dicari otomatis lewat mDNS "_adb-tls-connect._tcp" — pengguna tidak
     * pernah diminta mengetik port koneksi secara manual sama sekali.
     */
    suspend fun confirmAutoPairingCode(
        context: Context,
        pairingCode: String,
    ): AdbConnectionState {
        val found = _state.value as? AdbConnectionState.PairingFound
            ?: return AdbConnectionState.Failed(
                reason = AdbFailureReason.UNKNOWN,
                detail = "Sesi pairing sudah kedaluwarsa, tekan Start lagi.",
            )
        return pairAndAutoConnect(context, found.host, found.port, pairingCode)
    }

    private suspend fun pairAndAutoConnect(
        context: Context,
        pairingHost: String,
        pairingPort: Int,
        pairingCode: String,
    ): AdbConnectionState = AdbWakeLock.withWakeLock(context) {
        connectMutex.withLock {
            _state.value = AdbConnectionState.Pairing

            val paired = withContext(Dispatchers.IO) {
                runCatching { connection.pair(pairingHost, pairingPort, pairingCode) }
            }

            if (paired.isFailure) {
                val failure = AdbConnectionState.Failed(
                    reason = AdbFailureReason.PAIRING_CODE_INVALID_OR_EXPIRED,
                    detail = paired.exceptionOrNull()?.message ?: "Pairing ke $pairingHost:$pairingPort gagal atau kode kedaluwarsa.",
                )
                _state.value = failure
                return@withWakeLock failure
            }

            // Pairing sukses — sekarang cari port KONEKSI secara otomatis juga
            // (mDNS "_adb-tls-connect._tcp"), supaya pengguna TIDAK PERNAH
            // diminta mengetik port koneksi secara manual. Wireless debugging
            // selalu mem-broadcast service ini selama fiturnya aktif, jadi
            // timeout singkat (beberapa detik) sudah cukup — TAPI dicoba
            // BEBERAPA KALI (bukan sekali), lihat FIX di bawah.
            _state.value = AdbConnectionState.Connecting

            // FIX (Android 12/13 — "kode pairing benar tapi tetap 'Tidak bisa
            // terhubung'", lihat KDoc [AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED]):
            // SEBELUMNYA discoverConnectService HANYA dicoba SEKALI dengan
            // timeout default (15 detik) di titik ini — beda perlakuan dari
            // [autoReconnect] yang sudah retry 3x untuk masalah mDNS yang
            // persis sama. Tepat setelah tahap pairing selesai, radio Wi-Fi
            // baru saja dipakai intensif (handshake TLS pairing) — di banyak
            // build Android 12/13, NsdManager pada kondisi ini sering tidak
            // langsung mem-resolve service pertama yang muncul, BUKAN karena
            // service-nya tidak ada, murni butuh percobaan discovery baru.
            // Sekarang dicoba sampai maxConnectServiceAttempts kali, berhenti
            // begitu salah satu percobaan berhasil — sama persis pola yang
            // sudah terbukti di [autoReconnect].
            //
            // FIX 2 (lihat KDoc [AdbWakeLock]): seluruh blok ini (discovery +
            // connect) sekarang dibungkus PARTIAL_WAKE_LOCK — sebelumnya,
            // kalau kode pairing dibalas dari notifikasi lalu layar mati/idle
            // (skenario paling umum), ROM agresif (MIUI/ColorOS dll) bisa
            // membekukan CPU proses AetherX PERSIS di tengah proses ini,
            // membuat pairing certificate-nya sendiri sukses terkirim tapi
            // tahap connect sesudahnya gagal — persis gejala "Pairing Gagal /
            // Tidak bisa terhubung" walau Wireless debugging tetap aktif.
            val maxConnectServiceAttempts = 3
            var connectService: AdbAutoPairingDiscovery.DiscoveredService? = null
            for (attempt in 1..maxConnectServiceAttempts) {
                connectService = withContext(Dispatchers.IO) {
                    runCatching { AdbAutoPairingDiscovery.discoverConnectService(context, timeoutMs = 6_000) }
                }.getOrNull()
                if (connectService != null) break
            }

            val connectPort = connectService?.port
            if (connectPort == null) {
                val failure = AdbConnectionState.Failed(
                    reason = AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED,
                    detail = "Pairing berhasil, tapi port koneksi tidak ditemukan otomatis. Coba \"Sambungkan\" lagi.",
                )
                _state.value = failure
                return@withWakeLock failure
            }

            preferences.saveHostPort(pairingHost, connectPort)

            // FIX (bagian kedua dari fix Android 12/13 di atas): connectLocked
            // pertama di sini JUGA sebelumnya hanya dicoba sekali — kalau
            // socket TLS pertama gagal terbentuk (race serupa: radio baru saja
            // sibuk, atau adbd di sisi device belum sepenuhnya siap menerima
            // koneksi baru tepat setelah pairing), kegagalan itu SEBELUMNYA
            // langsung dilaporkan sebagai [AdbFailureReason.HOST_UNREACHABLE]
            // — pesan yang salah konteks untuk connect PERTAMA KALI (lihat
            // KDoc [AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED]). Sekarang
            // retry beberapa kali dengan jeda singkat sebelum benar-benar
            // dilaporkan gagal, dan kalau semua percobaan tetap gagal, reason
            // yang dipakai adalah CONNECT_AFTER_PAIRING_FAILED (bukan
            // HOST_UNREACHABLE) supaya pesannya akurat untuk konteks ini.
            val maxConnectAttempts = 3
            var lastResult: AdbConnectionState = AdbConnectionState.Connecting
            for (attempt in 1..maxConnectAttempts) {
                lastResult = connectLocked(pairingHost, connectPort)
                if (lastResult is AdbConnectionState.Connected) return@withWakeLock lastResult
                if (attempt < maxConnectAttempts) delay(1_500)
            }

            val failure = AdbConnectionState.Failed(
                reason = AdbFailureReason.CONNECT_AFTER_PAIRING_FAILED,
                detail = "Pairing berhasil, tapi koneksi shell belum bisa dibentuk. Coba \"Sambungkan\" lagi — pairing tidak perlu diulang.",
            )
            _state.value = failure
            failure
        }
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
            // FIX (lihat perintah rework wireless: sebelumnya SETIAP
            // kegagalan connect setelah pernah pairing otomatis
            // diklasifikasikan SHELL_REJECTED_NEEDS_REPAIR ("wajib pairing
            // ulang dari nol") HANYA karena `hadPreviousPairing == true` —
            // padahal penyebab paling umum kegagalan ini adalah PORT BASI
            // (lihat KDoc autoReconnect di atas), bukan certificate yang
            // benar-benar ditolak adbd. Sekarang HANYA diklasifikasikan
            // SHELL_REJECTED_NEEDS_REPAIR kalau library BENAR-BENAR
            // melempar exception jenis "pairing diperlukan lagi" (sinyal
            // eksplisit dari adbd bahwa certificate ini tidak/tidak lagi
            // dikenal) — kegagalan lain (timeout, connection refused ke
            // port basi, dst) tetap [AdbFailureReason.HOST_UNREACHABLE]
            // yang pesannya lebih akurat ("coba sambungkan lagi", BUKAN
            // "wajib pairing ulang").
            val exception = result.exceptionOrNull()
            val certificateRejected = exception != null &&
                exception.javaClass.simpleName.contains("PairingRequired", ignoreCase = true)
            val failure = AdbConnectionState.Failed(
                reason = if (certificateRejected) {
                    AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR
                } else {
                    AdbFailureReason.HOST_UNREACHABLE
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
     *
     * FIX (lihat perintah: "wireless sifatnya sementara... coba jadikan
     * lebih gampang untuk aktifkan lagi biar ga ribet hapus data aplikasi
     * lalu melakukan permission wireless ulang"): PORT KONEKSI
     * (`_adb-tls-connect._tcp`) Android SELALU berubah setiap kali
     * Wireless debugging dimatikan-nyalakan ulang ATAU perangkat
     * di-reboot — ini perilaku BAWAAN Android, bukan sesuatu yang bisa
     * AetherX cegah. Sebelum fix ini, [connect] hanya mencoba port LAMA
     * yang tersimpan di [preferences] — begitu port itu basi, percobaan
     * SELALU gagal dan diklasifikasikan [AdbFailureReason.SHELL_REJECTED_NEEDS_REPAIR]
     * ("wajib pairing ulang dari nol"), padahal CERTIFICATE AetherX
     * (lihat [AdbKeyManager]) MASIH SEPENUHNYA valid & masih dikenal adbd
     * — satu-satunya yang basi cuma NOMOR PORT-nya, bukan identitas
     * pairing-nya. Menghapus data aplikasi (yang menghapus certificate
     * juga) untuk masalah PORT SAJA sebenarnya berlebihan.
     *
     * Sekarang: kalau percobaan pertama pakai port tersimpan GAGAL,
     * SELAMA masih ada pairing tersimpan (bukan [AdbConnectionState.NotPaired]),
     * otomatis coba temukan port koneksi TERBARU lewat mDNS
     * ([AdbAutoPairingDiscovery.discoverConnectService] — service yang
     * SAMA yang sudah dipakai saat pairing pertama kali, lihat
     * [pairAndAutoConnect]) SEBELUM menyerah, lalu simpan port baru itu
     * dan coba connect ulang sekali lagi — SEMUA ini terjadi diam-diam di
     * background, TANPA pengguna perlu membuka dialog pairing kode 6-digit
     * apa pun, karena pairing-nya sendiri tidak pernah hilang. Wireless
     * debugging di perangkat HARUS sedang aktif supaya service mDNS ini
     * ter-broadcast — kalau memang mati, langkah ini akan timeout wajar
     * dan pengguna tetap diarahkan menyalakan Wireless debugging dulu.
     *
     * FIX 2 (Tombol "Sambungkan" terkadang menampilkan "Tidak bisa
     * terhubung" padahal Wireless debugging AKTIF dan pairing masih
     * valid): mDNS/NSD di Android TIDAK selalu mengembalikan hasil di
     * percobaan pertama — chipset Wi-Fi di banyak ROM (terutama
     * MIUI/ColorOS, bahkan dengan multicast lock sudah dipegang) kadang
     * butuh beberapa ratus milidetik sampai beberapa detik ekstra sebelum
     * benar-benar mem-flush paket multicast pertama, terutama tepat
     * setelah radio Wi-Fi baru saja bangun dari idle. Sebelumnya
     * rediscovery HANYA dicoba SATU KALI dengan timeout 15 detik — kalau
     * percobaan tunggal itu timeout (bukan berarti service-nya benar-benar
     * tidak ada, cuma belum sempat "kedengaran"), fungsi ini langsung
     * menyerah dan membiarkan state [AdbConnectionState.Failed] dari
     * percobaan [connect] pertama tetap tampil ke pengguna, walau service
     * koneksinya sebenarnya ada dan akan ditemukan kalau dicoba sekali
     * lagi. Sekarang rediscovery dicoba sampai `maxRediscoveryAttempts`
     * kali (timeout lebih pendek per percobaan supaya total waktu tunggu
     * tidak membengkak drastis), berhenti begitu salah satu percobaan
     * berhasil — Wireless debugging yang BENAR-BENAR mati tetap gagal di
     * semua percobaan secara wajar, hanya kegagalan sesaat akibat timing
     * mDNS yang sekarang tidak langsung dilaporkan sebagai gagal permanen.
     */
    fun autoReconnect() {
        scope.launch { autoReconnectSuspend() }
    }

    /**
     * Versi publik yang bisa DITUNGGU dari luar (dipakai
     * [com.aether.x.core.permission.PrivilegeManager.getExecutorAwaitingConnection]
     * — lihat KDoc di sana untuk bug yang diperbaiki: toast "Sambungkan
     * ADB tertanam atau Root dulu" muncul palsu karena caller sebelumnya
     * hanya membaca state APA ADANYA tanpa pernah menunggu reconnect
     * sungguhan). Aman dipanggil berkali-kali beruntun — [autoReconnectSuspend]
     * sudah idempotent (langsung kembali kalau memang sudah
     * [AdbConnectionState.Connected], dan [connectMutex] mencegah dua
     * reconnect berjalan bersamaan).
     */
    suspend fun awaitReconnect(): AdbConnectionState = autoReconnectSuspend()

    /**
     * FIX (bug "toast 'Perintah gagal dijalankan' padahal cuma butuh
     * waktu lebih untuk reconnect", lihat laporan pengguna): versi
     * [autoReconnect] yang BISA DITUNGGU hasil akhirnya (bukan
     * fire-and-forget lewat `scope.launch` biasa). Dipakai oleh
     * [markStreamFailureAndReconnect] supaya
     * [com.aether.x.core.shell.EmbeddedShellExecutor] tahu PERSIS kapan
     * proses reconnect (yang bisa memakan waktu total puluhan detik kalau
     * sampai perlu rediscovery mDNS 3x percobaan) benar-benar selesai,
     * alih-alih menebak lewat `delay()` tetap yang seringkali terlalu
     * pendek dan membuat command dianggap gagal padahal reconnect masih
     * berjalan di baliknya.
     *
     * Mengembalikan [AdbConnectionState.Connected] kalau berhasil, atau
     * state kegagalan/tidak berubah lainnya kalau tidak. Logika di dalam
     * SAMA PERSIS dengan [autoReconnect] sebelumnya, hanya dipisah supaya
     * hasil akhirnya bisa dikembalikan ke pemanggil, bukan cuma diketahui
     * lewat [state] StateFlow yang mungkin sudah "menimpa dirinya sendiri"
     * kalau ada aksi lain menyusul.
     */
    private suspend fun autoReconnectSuspend(): AdbConnectionState = AdbWakeLock.withWakeLock(appContext) {
        val saved = preferences.getSavedHostPort() ?: return@withWakeLock _state.value
        if (_state.value == AdbConnectionState.Connected) return@withWakeLock AdbConnectionState.Connected

        val firstAttempt = connectMutex.withLock { connectLocked(saved.first, saved.second) }
        if (firstAttempt is AdbConnectionState.Connected) return@withWakeLock firstAttempt

        // Port lama basi (paling sering setelah Wireless debugging
        // dimatikan-nyalakan ulang/reboot) — coba cari port terbaru
        // lewat mDNS TANPA meminta pairing ulang, selama identitas
        // pairing-nya sendiri masih ada (host tersimpan masih ada).
        // Dicoba beberapa kali (bukan sekali) karena mDNS kadang
        // butuh lebih dari satu percobaan sebelum "kedengaran".
        //
        // PENTING: [_state] SENGAJA dijaga tetap [Connecting] selama
        // rediscovery masih berjalan (bukan dibiarkan di [firstAttempt]
        // yang [Failed]) — [connectLocked] tiap dipanggil ulang di
        // bawah sudah menimpa state jadi [Connecting] lagi lewat baris
        // pertamanya sendiri, tapi KALAU rediscovery-nya sendiri butuh
        // beberapa detik untuk timeout/retry, tanpa baris ini state
        // akan "berkedip" balik ke [Failed] milik [firstAttempt] dulu
        // selama proses mDNS berjalan — memicu [AdbPairingNotifier]
        // menampilkan notifikasi error PADAHAL AetherX masih aktif
        // mencoba, bukan benar-benar sudah menyerah.
        _state.value = AdbConnectionState.Connecting

        val maxRediscoveryAttempts = 3
        var rediscovered: AdbAutoPairingDiscovery.DiscoveredService? = null
        for (attempt in 1..maxRediscoveryAttempts) {
            rediscovered = withContext(Dispatchers.IO) {
                runCatching { AdbAutoPairingDiscovery.discoverConnectService(appContext, timeoutMs = 6_000) }
            }.getOrNull()
            if (rediscovered != null) break
        }
        if (rediscovered == null) {
            // Semua percobaan rediscovery benar-benar habis — BARU
            // sekarang kegagalan [firstAttempt] final dipublish ke
            // pengguna (state publik jatuh ke Failed di sini, bukan
            // lebih awal).
            _state.value = firstAttempt
            return@withWakeLock firstAttempt
        }

        if (rediscovered.port == saved.second && rediscovered.host == saved.first) {
            // Sama persis dengan yang baru saja gagal — mengulang
            // connect() tidak akan mengubah apa pun, biarkan state
            // Failed dari percobaan pertama tetap ditampilkan ke
            // pengguna (biasanya berarti Wireless debugging memang
            // sedang mati, bukan cuma port basi).
            _state.value = firstAttempt
            return@withWakeLock firstAttempt
        }
        connectMutex.withLock { connectLocked(rediscovered.host, rediscovered.port) }
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
     *
     * FIX bug "tweak diam-diam tidak berefek tanpa toast error": execShell
     * sekarang ikut connectMutex supaya tidak race dengan connectLocked
     * (dipanggil autoReconnect/markStreamFailureAndReconnect) yang bisa
     * disconnect+connect ulang objek connection yang sama di tengah baca.
     */
    suspend fun execShell(command: String): Pair<Int, String> = connectMutex.withLock {
        withContext(Dispatchers.IO) {
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
    }

    fun isConnected(): Boolean = _state.value == AdbConnectionState.Connected && ::connection.isInitialized && connection.isConnected

    /**
     * FIX (Tweak selalu menampilkan "Perintah gagal dijalankan" walau
     * status Izin Akses terlihat "Terhubung"): [connection.isConnected]
     * milik libadb-android hanya mencerminkan status socket TCP terakhir
     * yang DIKETAHUI library, bukan hasil ping aktif — begitu koneksi
     * putus diam-diam di background (layar mati lama, Wireless debugging
     * dimatikan sebentar, perangkat pindah jaringan Wi-Fi), [isConnected]
     * bisa saja masih melaporkan `true` sampai percobaan `openStream`
     * BERIKUTNYA benar-benar gagal — sehingga [_state] tetap [Connected]
     * padahal socket sudah mati, dan setiap tweak yang dicoba SELALU
     * gagal tanpa penjelasan yang akurat ke pengguna (toast generik yang
     * menyuruh "periksa akses" padahal UI menunjukkan status tersambung).
     *
     * Dipanggil oleh [com.aether.x.core.shell.EmbeddedShellExecutor] tiap
     * kali [execShell] melempar exception I/O — menandai state SEBENARNYA
     * ([PairedNotConnected], BUKAN [AdbConnectionState.Failed] supaya
     * tidak memicu notifikasi pairing error yang tidak perlu) lalu MENUNGGU
     * hasil reconnect yang sebenarnya sebelum kembali ke pemanggil.
     *
     * FIX (bug "toast 'Perintah gagal dijalankan' padahal Wireless
     * debugging aktif dan cuma butuh waktu lebih", lihat laporan
     * pengguna): SEBELUMNYA fungsi ini `fun` biasa (bukan `suspend`) yang
     * memanggil [autoReconnect] fire-and-forget lewat `scope.launch`
     * terpisah — [EmbeddedShellExecutor] tidak punya cara mengetahui kapan
     * reconnect itu betul-betul selesai, jadi hanya menebak lewat
     * `delay(1500)` tetap lalu mengecek [isConnected] sekali. Padahal
     * reconnect asli ([autoReconnectSuspend]) bisa memakan waktu jauh
     * lebih lama dari 1.5 detik kalau sampai perlu rediscovery mDNS (3x
     * percobaan @ 6 detik timeout = puluhan detik) — command lalu
     * dianggap gagal permanen padahal reconnect-nya sendiri masih
     * berjalan dan akan berhasil sesaat lagi.
     *
     * Sekarang `suspend` dan langsung memanggil [autoReconnectSuspend],
     * mengembalikan state akhirnya — [EmbeddedShellExecutor] menunggu
     * hasil SEBENARNYA (selesai lebih cepat kalau memang cepat, atau
     * menunggu lebih lama kalau memang perlu mDNS, TANPA batas waktu
     * tebakan yang sewenang-wenang) sebelum memutuskan retry command atau
     * melaporkan gagal ke pengguna.
     */
    suspend fun markStreamFailureAndReconnect(): AdbConnectionState {
        if (_state.value == AdbConnectionState.Connected) {
            _state.value = AdbConnectionState.PairedNotConnected
        }
        return autoReconnectSuspend()
    }

    /**
     * "Lupakan perangkat ini" — dipanggil HANYA dari aksi eksplisit
     * pengguna di layar Izin Akses / Pengaturan. Menutup koneksi aktif,
     * menghapus host:port tersimpan, DAN menghapus keypair+certificate
     * (supaya pairing berikutnya benar-benar dari nol, bukan cuma lupa
     * alamat tapi masih dikenali certificate lama oleh adbd).
     */
    fun forgetPairing() {
        autoPairingJob?.cancel()
        autoPairingJob = null
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
