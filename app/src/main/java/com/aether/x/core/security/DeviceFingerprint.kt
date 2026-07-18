package com.aether.x.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Turunan device fingerprint hash yang menggantikan `ANDROID_ID` mentah
 * sebagai `deviceId` yang dikunci ke lisensi (lihat [com.aether.x.data.DeviceId],
 * [com.aether.x.data.LicenseRepository], [com.aether.x.data.DeviceRegistry]).
 *
 * MASALAH SEBELUMNYA: `ANDROID_ID` (Settings.Secure.ANDROID_ID) dikirim APA
 * ADANYA ke Firestore sebagai document ID. Nilai ini trivial diambil siapa
 * pun lewat `adb shell settings get secure android_id`, atau dibaca dari
 * SharedPreferences/cache app kalau device di-root — tidak ada usaha sama
 * sekali untuk membuatnya sulit diclone/diforge ke device lain demi
 * membajak binding lisensi.
 *
 * SOLUSI DI SINI: `ANDROID_ID` (+ sinyal tambahan `Build.FINGERPRINT` dan
 * `Build.MODEL`, supaya clone lintas-app-data juga tidak cukup) digabung
 * jadi satu string, dikirim ke NATIVE lewat JNI, di-HMAC-SHA256 dengan
 * kunci yang di-XOR-obfuscate di binary native (lihat devicefingerprint.cpp)
 * — HASIL HASH itulah yang dipakai sebagai `deviceId`, BUKAN input
 * mentahnya. Orang yang cuma tahu ANDROID_ID device korban (mis. bocor dari
 * log/screenshot debug) TETAP TIDAK BISA menghitung fingerprint hash yang
 * sama tanpa reverse-engineer kunci HMAC dari libaetherX.so.
 *
 * CATATAN JUJUR SOAL BATASAN (sama semangat dengan [SignatureGuard] &
 * [NativeIntegrityGuard]): ini MENAIKKAN EFFORT untuk clone/forge casual,
 * BUKAN proteksi sempurna. Penegakan yang benar-benar tidak bisa dilewati
 * tetap di server — lihat firestore.rules yang mengunci
 * `licenses/{key}.deviceId` ke SATU nilai begitu terisi pertama kali.
 * Modul ini hanya membuat nilai yang dikunci itu tidak lagi trivial
 * diprediksi dari luar aplikasi.
 */
object DeviceFingerprint {

    private const val TAG = "AetherX-DeviceFingerprint"

    // Separator tetap antar komponen identifier mentah sebelum digabung
    // jadi satu input HMAC. Sengaja karakter yang tidak mungkin muncul di
    // ANDROID_ID (hex 16 char)/Build.FINGERPRINT/Build.MODEL biasa, supaya
    // dua kombinasi berbeda tidak bisa menghasilkan string gabungan yang
    // sama persis (mis. "AB" + "" + "C" vs "A" + "" + "BC").
    private const val SEPARATOR = "\u0001"

    init {
        // Nama library HARUS "aetherX" — sama seperti SignatureGuard.kt &
        // NativeIntegrityGuard.kt, lihat catatan di SignatureGuard.kt kenapa
        // ketiganya harus selalu sebut nama yang persis sama.
        System.loadLibrary("aetherX")
    }

    private external fun nativeDeriveFingerprint(rawInput: ByteArray): ByteArray?

    /**
     * Hitung fingerprint hash untuk perangkat ini, dalam bentuk hex string
     * lowercase 64 karakter (32 byte HMAC-SHA256), aman dipakai langsung
     * sebagai document ID Firestore (tidak ada karakter `/`, spasi, dst).
     *
     * Return `null` kalau:
     * - `ANDROID_ID` tidak terbaca sama sekali (sangat jarang), ATAU
     * - Native gagal menghitung hash (mis. library gagal dimuat).
     *
     * Pemanggil ([com.aether.x.data.DeviceId]) WAJIB menangani `null` ini
     * secara eksplisit — JANGAN fallback diam-diam ke ANDROID_ID mentah,
     * karena itu akan mengalahkan tujuan seluruh modul ini.
     */
    fun compute(context: Context): String? {
        val rawInput = buildRawInput(context)
        val digest = runCatching { nativeDeriveFingerprint(rawInput.toByteArray(Charsets.UTF_8)) }
            .onFailure { e -> Log.w(TAG, "Gagal menghitung device fingerprint (native)", e) }
            .getOrNull()

        if (digest == null || digest.isEmpty()) {
            Log.w(TAG, "Device fingerprint native mengembalikan hasil kosong/null")
            return null
        }

        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    @SuppressLint("HardwareIds")
    private fun buildRawInput(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""
        return buildString {
            append(androidId)
            append(SEPARATOR)
            append(Build.FINGERPRINT)
            append(SEPARATOR)
            append(Build.MODEL)
        }
    }
}
