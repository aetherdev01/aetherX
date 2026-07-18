package com.aether.x.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.aether.x.core.security.DeviceFingerprint

/**
 * Sumber tunggal untuk menentukan `deviceId` perangkat ini. Dipakai oleh
 * [UserIdRepository] (pendataan device + alokasi userId ke Firestore) dan
 * [LicenseRepository] (pengunci lisensi per-device) supaya keduanya selalu
 * memakai nilai yang identik — bukan dua implementasi terpisah yang bisa
 * diam-diam berbeda.
 *
 * BERUBAH (lihat [DeviceFingerprint] untuk detail lengkap & alasan): nilai
 * yang dikembalikan SEKARANG adalah hash HMAC-SHA256 (dihitung di native,
 * kunci ter-obfuscate) dari gabungan `ANDROID_ID` + `Build.FINGERPRINT` +
 * `Build.MODEL` — BUKAN `ANDROID_ID` mentah lagi. `ANDROID_ID` mentah
 * trivial diambil siapa pun lewat
 * `adb shell settings get secure android_id`, sehingga kalau dikirim apa
 * adanya ke Firestore, siapa pun yang tahu ANDROID_ID device korban bisa
 * memforge device lain untuk membajak binding lisensinya. Hash native ini
 * tidak bisa direproduksi tanpa tahu kunci HMAC yang tersimpan (dalam
 * bentuk ter-XOR) di dalam libaetherX.so.
 *
 * PERINGATAN MIGRASI: device yang sudah mendata dirinya SEBELUM perubahan
 * ini (dokumen `devices/{ANDROID_ID_lama}` dan lisensi yang terkunci ke
 * `ANDROID_ID_lama`) TIDAK akan otomatis cocok lagi dengan `deviceId` baru
 * (hash), karena keduanya adalah nilai yang berbeda secara fundamental.
 * Kalau app ini sudah pernah dirilis dengan skema ANDROID_ID mentah,
 * migrasi data lama di Firestore (mis. script sekali-jalan yang menghitung
 * hash untuk tiap dokumen `devices` yang ada) WAJIB dilakukan SEBELUM
 * rilis versi ini ke pengguna lama — kalau tidak, lisensi yang sudah
 * terkunci ke device lama akan tampak "BoundToOtherDevice"/hilang
 * bindingnya dari sudut pandang device yang sama.
 *
 * Fallback ke `ANDROID_ID` mentah HANYA terjadi kalau native benar-benar
 * gagal (mis. library corrupt) — dicatat jelas ke Logcat dengan prefix
 * `[fallback]` supaya gampang dibedakan dari deviceId hash normal saat
 * debugging/monitoring Firebase Console.
 */
object DeviceId {
    private const val TAG = "AetherX-DeviceId"

    fun read(context: Context): String =
        DeviceFingerprint.compute(context) ?: run {
            Log.w(TAG, "Device fingerprint native gagal — fallback ke ANDROID_ID mentah")
            "fallback:${readRawAndroidId(context)}"
        }

    @SuppressLint("HardwareIds")
    private fun readRawAndroidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
}
