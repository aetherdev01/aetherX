package com.aether.x.core.security

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Pasang Firebase App Check dengan provider Play Integrity — ini GUARD
 * PALING PENTING untuk menutup celah brute-force pada sistem lisensi (lihat
 * SECURITY.md untuk kronologi lengkap kenapa ini ditambahkan).
 *
 * MASALAH SEBELUMNYA: `firestore.rules` lama punya `allow get: if true` pada
 * koleksi `licenses/{key}`. Ini membuat Firestore REST API bisa diakses
 * LANGSUNG dari luar app (curl/Postman/script) tanpa autentikasi apa pun.
 * Siapa pun yang mem-brute-force ribuan kombinasi kode ke endpoint tsb bisa
 * tahu kode mana yang valid hanya dari respons 200 vs 404 — tanpa perlu
 * membuka aplikasi Android sama sekali, dan tanpa batas kecepatan.
 *
 * SOLUSI: App Check membuat setiap request Firestore (dari SDK Android)
 * membawa token yang membuktikan permintaan itu datang dari APK asli, dengan
 * signature rilis yang terdaftar, yang lolos verifikasi Play Integrity API
 * Google. `firestore.rules` sekarang menolak SEMUA request yang tidak
 * membawa token ini (lihat fungsi `isVerifiedApp()`). Efeknya:
 * - Script/curl langsung ke REST API: DITOLAK (tidak punya token App Check).
 * - APK hasil decompile-modifikasi-recompile-resign sendiri: DITOLAK (App
 *   Check + Play Integrity memverifikasi APK ditandatangani dengan kunci
 *   rilis yang terdaftar di Play Console, bukan sembarang kunci).
 * - App asli, terinstall dari Play Store atau sideload APK release resmi
 *   yang signing key-nya terdaftar: DITERIMA seperti biasa.
 *
 * CATATAN SETUP WAJIB (lihat SECURITY.md bagian "Setup App Check"):
 * 1. Firebase Console -> Build -> App Check -> Apps -> pilih app Android ->
 *    aktifkan provider "Play Integrity".
 * 2. Play Console -> App integrity -> pastikan Play Integrity API aktif
 *    untuk applicationId `com.aether.x` (dan `.debug` kalau ingin testing
 *    debug build lewat App Check juga — App Check punya "debug provider"
 *    terpisah untuk itu, lihat catatan initDebugProviderIfNeeded() di bawah
 *    kalau builds debug perlu tetap bisa mengakses Firestore saat development).
 * 3. Firebase Console -> Firestore -> Rules -> deploy firestore.rules yang
 *    sudah memakai `isVerifiedApp()`. JANGAN deploy rules ini sebelum App
 *    Check aktif dan APK rilis sudah terverifikasi minimal sekali, supaya
 *    tidak tiba-tiba mengunci akses app produksi yang sudah beredar.
 * 4. Sebaiknya aktifkan mode "Enforced" di Firebase Console App Check secara
 *    bertahap: mulai dari "Unenforced/Monitor" untuk lihat metrik berapa
 *    banyak request yang akan ditolak, baru pindah ke "Enforced" kalau
 *    request app asli sudah lolos normal.
 */
object AppCheckInitializer {

    private const val TAG = "AppCheckInitializer"

    fun init(context: Context) {
        runCatching {
            Firebase.initialize(context)
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }.onFailure { e ->
            // Best-effort: kalau init App Check gagal (mis. Play services
            // tidak tersedia di device tertentu), JANGAN crash app di sini —
            // biarkan Firestore rules yang menolak request tanpa token App
            // Check nanti (hasilnya: LicenseResult.NetworkError di
            // LicenseRepository, bukan crash). Tapi ini dicatat karena
            // artinya device tsb tidak akan pernah bisa memakai fitur
            // lisensi/Firestore sama sekali sampai Play services tersedia.
            Log.e(TAG, "Gagal memasang App Check provider", e)
        }
    }
}
