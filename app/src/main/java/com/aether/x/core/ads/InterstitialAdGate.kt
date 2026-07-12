package com.aether.x.core.ads

import android.app.Activity

/**
 * GUARD untuk interstitial ad — dipakai di titik TRANSISI natural setelah
 * sebuah aksi selesai (mis. setelah "Kill Background Apps" berhasil di
 * TweakViewModel), BUKAN untuk memblokir aksi itu sendiri.
 *
 * PRINSIP UTAMA (konsisten dengan [RewardGate] dan filosofi "member = no
 * ads" produk ini — lihat KDoc [com.aether.x.ui.membership.MembershipViewModel]):
 * - Member ([isMember] true) TIDAK PERNAH melihat interstitial sama sekali.
 * - Interstitial TIDAK PERNAH memblokir/menunda aksi yang memicunya — aksi
 *   sudah selesai duluan, iklan cuma tampil SESUDAHNYA sebagai transisi.
 *   Kalau iklan gagal/belum siap, alur aplikasi lanjut seperti biasa, TIDAK
 *   ada efek yang terlihat pengguna selain tidak ada iklan.
 * - Dibatasi [COOLDOWN_MILLIS] (1 menit) supaya tidak berpotensi muncul
 *   berkali-kali beruntun kalau pemanggil trigger-nya sendiri sering
 *   terpicu berulang dalam waktu singkat — interstitial yang terlalu
 *   sering adalah bentuk paling umum dari "iklan yang mengganggu".
 *
 * Berbeda dari [RewardGate]: TIDAK ADA kuota harian atau kredit — cooldown
 * di sini murni soal jarak waktu, bukan soal "jatah pemakaian gratis".
 *
 * FITUR BARU (deteksi adblock — lihat [AdBlockDetector]/[AdBlockDialogState]):
 * SEBELUM mencoba tampilkan iklan, dicek dulu apakah ada sinyal adblock
 * aktif — kalau ada, dialog jujur ditampilkan SEBAGAI GANTI mencoba
 * memuat iklan (yang toh besar kemungkinan akan gagal/diblokir), BUKAN
 * cara diam-diam melewati adblock-nya (lihat KDoc [AdBlockDetector] soal
 * batasan lingkup ini — TETAP HANYA deteksi+pesan jujur).
 *
 * CONTOH PEMAKAIAN (lihat pemasangan nyata di
 * [com.aether.x.ui.tweak.TweakViewModel.onKillBackgroundAppsChange]):
 * ```
 * interstitialGate.maybeShow(activity = activity, isMember = membershipActive)
 * ```
 */
class InterstitialAdGate(
    private val adManager: InterstitialAdManager,
) {

    /**
     * Tampilkan interstitial SEKARANG kalau: [isMember] false, tidak ada
     * sinyal adblock terdeteksi, ada iklan siap, dan cooldown sejak
     * tampilan terakhir sudah lewat. Kalau salah satu syarat tidak
     * terpenuhi, fungsi ini TIDAK melakukan apa pun selain (mungkin)
     * dialog adblock — pemanggil tidak perlu menangani kasus gagal secara
     * khusus, karena interstitial memang tidak pernah boleh mengubah alur
     * aplikasi.
     *
     * SEKARANG `suspend` (sebelumnya fungsi biasa) — dibutuhkan untuk
     * memanggil [AdBlockDetector.detect] yang juga suspend. SEMUA
     * pemanggil yang sudah ada (TweakViewModel, AppManagerViewModel,
     * BuildPropViewModel) TIDAK PERLU diubah sama sekali — keempatnya
     * SUDAH memanggil fungsi ini dari dalam `viewModelScope.launch { }`
     * (dibuktikan lewat `preferences.preferences.first()` yang dipanggil
     * tepat sebelum `maybeShow` di keempat titik itu — `.first()` sendiri
     * suspend, jadi keempatnya sudah pasti berada di coroutine context).
     */
    suspend fun maybeShow(activity: Activity, isMember: Boolean) {
        if (isMember) return

        val adBlockSignals = AdBlockDetector.detect(activity)
        if (adBlockSignals.anyDetected) {
            AdBlockDialogState.requestShow()
            return // TIDAK mencoba tampilkan iklan yang toh kemungkinan diblokir
        }

        if (!adManager.isReady) return

        val now = System.currentTimeMillis()
        if (now - lastShownAtMillis < COOLDOWN_MILLIS) return

        lastShownAtMillis = now
        adManager.show(activity) { /* tidak ada reward/hasil yang perlu ditindaklanjuti */ }
    }

    private companion object {
        // In-memory (bukan DataStore) SENGAJA — cooldown 1 menit yang
        // "reset" saat proses app di-kill dampaknya nyaris tidak terasa
        // pengguna, jadi tidak sepadan menambah I/O disk tiap kali dicek.
        // volatile karena bisa diakses dari beberapa ViewModel berbeda.
        @Volatile
        private var lastShownAtMillis: Long = 0L

        const val COOLDOWN_MILLIS = 60_000L
    }
}
