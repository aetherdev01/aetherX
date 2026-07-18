package com.aether.x.core.ads

import android.content.Context
import com.aether.x.data.AetherXPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State holder untuk dialog adblock — StateFlow (BUKAN AlertDialog native
 * android.app.AlertDialog seperti percobaan pertama fitur ini) supaya
 * KONSISTEN dengan SELURUH dialog lain di app ini yang semuanya
 * androidx.compose.material3.AlertDialog (lihat MembershipScreen.kt,
 * BuildPropScreen.kt, AppManagerScreen.kt, CrosshairColorPickerDialog.kt
 * — TIDAK ADA SATU PUN yang pakai dialog native sebelum percobaan pertama
 * yang sudah dikoreksi ini).
 *
 * DUA TITIK PEMICU (sesuai pilihan pengguna "keduanya" saat fitur ini
 * dibuat) SAMA-SAMA memanggil [requestShow] di object ini:
 * 1. [com.aether.x.ui.main.MainScreen] — sekali tiap app dibuka.
 * 2. [InterstitialAdGate.maybeShow] — sebelum mencoba tampilkan iklan,
 *    dipanggil dari ViewModel LAIN (TweakViewModel, AppManagerViewModel,
 *    dst.), di luar scope Composable manapun.
 *
 * [visible] didengarkan oleh SATU Composable ([AdBlockDialog], dirender
 * di [com.aether.x.ui.main.MainScreen] supaya selalu ada terlepas tab mana
 * yang sedang aktif) — jadi kedua titik pemicu di atas HANYA perlu
 * mengubah state di sini, TIDAK perlu tahu/peduli Composable tree yang
 * sedang aktif sama sekali.
 *
 * BUG FIX (lihat perintah rework — "dialog tidak bisa di-cancel kecuali
 * sudah menonaktifkan AdBlock, dan MASIH MUNCUL LAGI walau sudah ditutup
 * setelah app dibuka ulang"): implementasi SEBELUMNYA hanya menyimpan
 * status "sudah pernah tampil" di [shownThisSession] — variabel IN-MEMORY
 * yang otomatis balik ke false setiap kali PROSES app baru dimulai (mis.
 * app di-kill lalu dibuka lagi, yang di Android adalah hal BIASA, bukan
 * kejadian langka). Efeknya: menekan "Tutup" cuma menyembunyikan dialog
 * untuk SESI berjalan ini saja — begitu app dibuka ulang dan sinyal
 * adblock masih terdeteksi (yang WAJAR kalau pengguna memang belum
 * menonaktifkannya), [MainScreen] memanggil [requestShow] lagi dari nol
 * dan dialog nongol lagi, seolah-olah "tidak bisa ditutup permanen".
 * TIDAK ADA jalan bagi pengguna untuk bilang "saya sudah lihat, jangan
 * tanya lagi" kalau mereka MEMILIH tetap memakai adblock-nya (hak mereka
 * — lihat KDoc [AdBlockDetector], dialog ini cuma pesan jujur, BUKAN
 * paksaan menonaktifkan).
 *
 * PERBAIKAN: [dismiss] sekarang MENYIMPAN [AdBlockDetector.AdBlockSignals.signalKey]
 * yang sedang ditampilkan ke [AetherXPreferences] (persist lewat
 * DataStore, BUKAN in-memory) sebagai "sinyal yang sudah diakui
 * pengguna". [requestShow] membandingkan sinyal SEKARANG dengan sinyal
 * tersimpan itu — kalau SAMA PERSIS, dialog TIDAK ditampilkan lagi
 * (pengguna sudah menutupnya untuk kombinasi sinyal ini, keputusannya
 * dihormati). Kalau BERBEDA (mis. pengguna benar-benar menonaktifkan
 * adblock-nya lalu memasang metode lain, atau baru pertama kali
 * terdeteksi), dialog WAJAR tampil lagi karena secara substansi ini
 * situasi baru. `shownThisSession` tetap dipertahankan sebagai lapisan
 * TAMBAHAN (bukan pengganti) supaya dua titik pemicu di atas tidak saling
 * menumpuk dialog dalam satu sesi yang sama.
 */
object AdBlockDialogState {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _openMembershipRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openMembershipRequests: SharedFlow<Unit> = _openMembershipRequests.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // In-memory, lapisan TAMBAHAN di atas persistensi DataStore (lihat
    // KDoc kelas di atas) — mencegah dua titik pemicu saling menumpuk
    // dialog dalam SATU sesi berjalan yang sama, terlepas dari hasil
    // pengecekan persist.
    @Volatile
    private var shownThisSession = false

    // Sinyal yang SEDANG ditampilkan di dialog ini sekarang (kalau ada) —
    // dibutuhkan [dismiss] supaya tahu snapshot APA yang harus disimpan
    // sebagai "sudah diakui pengguna" saat mereka menutupnya.
    @Volatile
    private var currentlyShownSignalKey: String? = null

    /**
     * Diminta menampilkan dialog untuk kombinasi [signals] yang baru saja
     * terdeteksi. TIDAK menampilkan apa pun kalau: sudah tampil di sesi
     * ini ([shownThisSession]), ATAU kombinasi sinyal yang SAMA PERSIS
     * sudah pernah diakui/ditutup pengguna sebelumnya (persisten lewat
     * [AetherXPreferences.getAdBlockAcknowledgedSignal]).
     */
    fun requestShow(context: Context, signals: AdBlockDetector.AdBlockSignals) {
        if (shownThisSession) return
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (shownThisSession) return@withLock
                val preferences = AetherXPreferences(appContext)
                val acknowledged = preferences.getAdBlockAcknowledgedSignal()
                if (acknowledged == signals.signalKey) return@withLock

                shownThisSession = true
                currentlyShownSignalKey = signals.signalKey
                _visible.value = true
            }
        }
    }

    /**
     * Menutup dialog DAN menyimpan sinyal yang sedang ditampilkan sebagai
     * "sudah diakui pengguna" secara PERMANEN (lintas sesi/restart app) —
     * lihat KDoc kelas di atas kenapa ini penting. [context] wajib diisi
     * supaya penyimpanan bisa langsung terjadi dari titik pemanggilan
     * mana pun (baik dari [AdBlockDialog] Composable maupun pemanggil
     * lain di masa depan).
     */
    fun dismiss(context: Context) {
        _visible.value = false
        val signalKey = currentlyShownSignalKey ?: return
        currentlyShownSignalKey = null
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                AetherXPreferences(appContext).setAdBlockAcknowledgedSignal(signalKey)
            }
        }
    }

    fun requestOpenMembership() {
        _openMembershipRequests.tryEmit(Unit)
    }
}
