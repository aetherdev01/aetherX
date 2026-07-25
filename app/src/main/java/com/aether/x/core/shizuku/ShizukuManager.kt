package com.aether.x.core.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * ROLLBACK TOTAL (lihat perintah — "jadikan sistem adb kembali ke shizuku
 * pure... hapus semua yang bersangkutan dengan adb tertanam"): pengganti
 * `AdbConnectionManager` (DIHAPUS beserta seluruh package `core/adb/`).
 *
 * Kenapa rollback: ADB tertanam (libadb-android) mengharuskan AetherX
 * sendiri mengimplementasikan seluruh siklus wireless debugging (mDNS
 * discovery, TLS pairing 6-digit, reconnect port yang sering basi) —
 * TERBUKTI tidak cukup andal di lapangan, gejala "Pairing Gagal / Tidak
 * bisa terhubung" berulang di ROM agresif (MIUI dkk) walau sudah diberi
 * banyak lapis retry, WakeLock, dan goAsync.
 *
 * Shizuku murni MENGHILANGKAN seluruh permukaan masalah itu: AetherX tidak
 * pernah melakukan pairing atau koneksi jaringan sama sekali. Pengguna
 * menginstal app Shizuku Manager terpisah, men-start service-nya sendiri
 * DI SANA (lewat ADB wireless/USB sekali jalan, root, atau modul Sui —
 * terserah metode yang mereka pilih, itu bukan urusan AetherX), dan
 * AetherX HANYA perlu:
 *   1. Cek apakah binder Shizuku hidup ([Shizuku.pingBinder]).
 *   2. Kalau hidup, cek/minta izin ([Shizuku.checkSelfPermission] /
 *      [Shizuku.requestPermission]).
 *   3. Kalau diizinkan, jalankan command lewat [Shizuku.newProcess].
 * Tidak ada state machine pairing, tidak ada mDNS, tidak ada TLS custom —
 * jauh lebih sedikit yang bisa gagal, dan begitu Shizuku hidup, koneksinya
 * TIDAK PERNAH "basi" seperti port TCP ADB (binder Android yang dipakai
 * Shizuku otomatis reconnect selama proses service masih hidup).
 */
object ShizukuManager {

    /** Package name resmi Shizuku Manager di Play Store / GitHub release
     *  (moe.shizuku.privileged.api) — dipakai untuk deep-link ke app itu
     *  kalau belum terinstal (buka halaman Play Store) atau untuk
     *  membukanya langsung kalau sudah terinstal. */
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    private const val REQUEST_CODE = 9001

    private val _state = MutableStateFlow<ShizukuConnectionState>(ShizukuConnectionState.ServiceNotRunning)
    val state: StateFlow<ShizukuConnectionState> = _state.asStateFlow()

    private var initialized = false
    private var appContext: Context? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuConnectionState.ServiceNotRunning
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
        _state.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
            ShizukuConnectionState.Connected
        } else {
            ShizukuConnectionState.PermissionDenied
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        // addBinderReceivedListenerSticky — kalau binder SUDAH hidup saat
        // listener ini didaftarkan (mis. Shizuku sudah jalan sebelum
        // AetherX dibuka), listener tetap dipanggil langsung sekali. Beda
        // dari addBinderReceivedListener biasa yang HANYA terpanggil untuk
        // kejadian binder baru terhubung SETELAH listener didaftarkan —
        // tanpa versi sticky ini, status "Shizuku sudah aktif" bisa tidak
        // pernah terdeteksi kalau service-nya sudah lebih dulu hidup
        // sebelum AetherX di-launch.
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        refresh()
    }

    /** Cek ulang status Shizuku sekarang juga — dipanggil otomatis saat
     *  binder baru tersambung, dan sebaiknya juga dipanggil manual setiap
     *  layar Izin Akses kembali ke foreground (`ON_RESUME`), karena
     *  pengguna bisa saja baru saja start/stop Shizuku Manager lewat
     *  recent apps tanpa AetherX mendapat callback binder apa pun untuk
     *  kasus "app Shizuku Manager di-force-stop paksa". */
    fun refresh() {
        val context = appContext ?: return
        if (!isShizukuInstalled(context)) {
            _state.value = ShizukuConnectionState.NotInstalled
            return
        }
        if (!Shizuku.pingBinder()) {
            _state.value = ShizukuConnectionState.ServiceNotRunning
            return
        }
        _state.value = if (hasPermission()) {
            ShizukuConnectionState.Connected
        } else {
            ShizukuConnectionState.PermissionNotGranted
        }
    }

    fun isShizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)

    fun isServiceRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        if (Shizuku.isPreV11()) {
            // Versi Shizuku pra-11 pakai permission model lama (izin
            // dicek lewat checkPermission, bukan checkSelfPermission).
            @Suppress("DEPRECATION")
            Shizuku.checkPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    /** Minta izin ke pengguna lewat dialog resmi Shizuku (mirip dialog
     *  runtime permission Android biasa). Hasilnya masuk lewat
     *  [permissionResultListener] di atas, BUKAN return value langsung —
     *  Shizuku sendiri yang menampilkan UI dialognya. */
    fun requestPermission() {
        if (!isServiceRunning()) {
            refresh()
            return
        }
        if (hasPermission()) {
            _state.value = ShizukuConnectionState.Connected
            return
        }
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** Buka app Shizuku Manager kalau sudah terinstal (untuk pengguna
     *  men-start service-nya sendiri di sana), atau arahkan ke Play Store
     *  kalau belum terinstal sama sekali. */
    fun openShizukuManager(context: Context) {
        if (isShizukuInstalled(context)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            if (launchIntent != null) {
                runCatching { context.startActivity(launchIntent) }
                return
            }
        }
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=$SHIZUKU_PACKAGE_NAME"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val webFallback = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE_NAME"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(marketIntent) }
            .recoverCatching { context.startActivity(webFallback) }
    }
}
