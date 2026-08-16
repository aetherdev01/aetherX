package com.aether.x.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Penyimpanan string sensitif (path deteksi native, ID konfigurasi SDK,
 * dll) dalam bentuk terenkripsi AES-256-GCM, bukan `const-string` polos —
 * tujuannya supaya nilai aslinya TIDAK muncul sebagai teks biasa yang bisa
 * langsung dibaca lewat `strings classes.dex` atau viewer smali/jadx tanpa
 * dijalankan (obfuscator R8/ProGuard bawaan hanya me-rename identifier,
 * TIDAK mengenkripsi literal string).
 *
 * INI BUKAN kriptografi rahasia dagang — kunci AES-nya tetap ikut di-ship
 * dalam APK (di-split 3 bagian di [keyPart1]/[keyPart2]/[keyPart3] dan
 * digabung saat runtime supaya tidak muncul sebagai satu blok byte yang
 * gampang di-grep), jadi siapa pun yang benar-benar niat trace eksekusi
 * (mis. pasang breakpoint di [reveal] lewat Frida) tetap bisa mendapatkan
 * plaintext-nya. Tujuannya HANYA menaikkan effort baca statis biasa
 * (`strings`, jadx tanpa dijalankan), bukan mencegah reverse engineering
 * total — sama seperti kebanyakan string encryption di aplikasi Android
 * pada umumnya.
 *
 * CARA TAMBAH STRING BARU: jalankan `encode_secret.py` (di root project,
 * TIDAK ikut di-package ke APK) dengan string plaintext-nya, lalu tempel
 * hasil Base64 dari script itu sebagai argumen [reveal] di pemanggil.
 */
internal object SecretStrings {

    // Tiga potongan kunci AES-256 (masing-masing Base64). Digabung di
    // [assembleKey] hanya saat dibutuhkan, tidak pernah disimpan utuh
    // sebagai satu field/const — mengurangi kemungkinan satu literal
    // 32-byte "menonjol" saat di-grep statis dari classes.dex.
    private const val keyPart1 = "WSx6RPBu8ZHPcrY="
    private const val keyPart2 = "gTUP/bCUJ7Kh31Q="
    private const val keyPart3 = "x/MpSkWgbSkwdw=="

    private fun assembleKey(): SecretKeySpec {
        val k1 = Base64.decode(keyPart1, Base64.NO_WRAP)
        val k2 = Base64.decode(keyPart2, Base64.NO_WRAP)
        val k3 = Base64.decode(keyPart3, Base64.NO_WRAP)
        val full = k1 + k2 + k3
        return SecretKeySpec(full, "AES")
    }

    /**
     * Dekripsi satu string yang sudah dienkode lewat `encode_secret.py`.
     * [payloadBase64] berisi 12 byte IV diikuti ciphertext+tag GCM,
     * di-Base64-kan jadi satu string oleh script encoder.
     *
     * Sengaja TIDAK di-cache/memoize — pemanggil (biasanya sekali per
     * proses, di `init`/companion const lazy) yang menentukan apakah
     * hasilnya perlu disimpan. Kalau dekripsi gagal (payload korup/format
     * salah), melempar exception apa adanya — pemanggil yang sensitif
     * terhadap crash (native guard) sebaiknya membungkus dengan
     * `runCatching` sendiri sesuai konvensi file masing-masing.
     */
    fun reveal(payloadBase64: String): String {
        val combined = Base64.decode(payloadBase64, Base64.NO_WRAP)
        require(combined.size > 12) { "Payload secret terlalu pendek" }
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, assembleKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(ciphertext)
        return String(plain, Charsets.UTF_8)
    }

    /** Versi list — dekode banyak string sekaligus (mis. daftar path sysfs). */
    fun revealList(vararg payloadsBase64: String): List<String> =
        payloadsBase64.map(::reveal)
}
