package com.aether.x.core.adb

import android.content.Context
import dadb.AdbKeyPair
import java.io.File

/**
 * REWORK TOTAL PERMISSION — "ADB tertanam" (lihat perintah rework
 * "buatkan sistem seperti shizuku langsung tertanam dalam aplikasinya...
 * hapus semua yang bersangkutan dengan shizuku").
 *
 * Pengelola keypair RSA 2048-bit yang dipakai AetherX untuk
 * mengautentikasi dirinya sendiri ke `adbd` — persis seperti keypair
 * `~/.android/adbkey` yang dipakai command-line `adb` di komputer, atau
 * keypair internal yang dipakai Shizuku/AxManager. Disimpan sekali di
 * penyimpanan internal privat app (`filesDir`, tidak bisa diakses app lain
 * tanpa root) dan dipakai ulang selamanya — INILAH kunci utama kenapa
 * pairing "tidak gampang ter-reset": begitu satu kali di-pair, adbd di
 * perangkat mengingat PUBLIC KEY ini secara permanen (tersimpan di
 * `/data/misc/adb/adb_keys` oleh sistem), sehingga sesi ADB berikutnya
 * (`AdbShellConnection.connect`) tidak perlu pairing ulang — HANYA connect
 * biasa ke port ADB debugging (bukan port pairing) yang otomatis diterima
 * karena key sudah dikenali, sama seperti `adb shell` di komputer yang
 * sudah pernah "always allow" pada suatu perangkat.
 *
 * Yang TETAP tidak bisa dihindari (batasan Android, sama untuk Shizuku
 * ataupun AxManager): kalau pengguna me-reboot perangkat, service ADB
 * debugging bawaan Android sendiri berhenti sampai pengguna
 * mengaktifkannya lagi secara manual di Pengaturan (atau ini yang
 * dijalankan otomatis kalau device root — lihat RootShellExecutor untuk
 * mode itu). Keypair ini TIDAK hilang saat reboot; yang hilang hanyalah
 * koneksi aktifnya, dan begitu Wireless debugging dinyalakan lagi,
 * AetherX bisa langsung connect ulang TANPA pairing ulang karena key
 * sudah dikenal adbd.
 */
class AdbKeyManager(context: Context) {

    private val keyDir = File(context.filesDir, "adb_key").apply { mkdirs() }
    private val privateKeyFile = File(keyDir, "aetherx_adb_key")
    private val publicKeyFile = File(keyDir, "aetherx_adb_key.pub")

    /**
     * Ambil keypair yang sudah tersimpan, atau bikin baru sekali kalau ini
     * pertama kalinya AetherX dipasang/dijalankan. Keypair yang sama ini
     * dipakai TERUS-MENERUS untuk semua percobaan pairing & connect
     * berikutnya — mengganti keypair akan membuat adbd menganggap AetherX
     * sebagai "perangkat"/klien baru yang belum dikenal, sehingga wajib
     * pairing ulang. Karena itu keypair ini SENGAJA tidak pernah
     * di-regenerate otomatis oleh AetherX sendiri.
     */
    @Synchronized
    fun getOrCreateKeyPair(): AdbKeyPair {
        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            return runCatching {
                AdbKeyPair.read(privateKeyFile, publicKeyFile)
            }.getOrElse {
                // Berkas korup (mis. penyimpanan penuh saat menulis
                // sebelumnya) — regenerasi adalah satu-satunya pilihan
                // aman, meski konsekuensinya pengguna wajib pairing ulang
                // sekali lagi.
                regenerate()
            }
        }
        return regenerate()
    }

    private fun regenerate(): AdbKeyPair {
        val pair = AdbKeyPair.generate()
        AdbKeyPair.write(pair, privateKeyFile, publicKeyFile)
        return pair
    }

    /**
     * Hapus keypair secara eksplisit — dipakai HANYA oleh aksi pengguna
     * "Lupakan perangkat ini" / "Reset pairing" di Pengaturan (lihat
     * PermissionSetupScreen), BUKAN dipanggil otomatis oleh alur mana pun.
     * Setelah ini, pairing wireless debugging wajib diulang dari awal
     * karena adbd di perangkat masih mengingat public key LAMA yang sudah
     * tidak lagi cocok dengan private key yang baru akan dibuat.
     */
    fun forgetKeyPair() {
        privateKeyFile.delete()
        publicKeyFile.delete()
    }

    fun hasKeyPair(): Boolean = privateKeyFile.exists() && publicKeyFile.exists()
}
