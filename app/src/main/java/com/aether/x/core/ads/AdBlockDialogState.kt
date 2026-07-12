package com.aether.x.core.ads

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
 */
object AdBlockDialogState {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _openMembershipRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openMembershipRequests: SharedFlow<Unit> = _openMembershipRequests.asSharedFlow()

    // In-memory, SENGAJA hanya sekali per proses app (bukan per-trigger) —
    // supaya dua titik pemicu di atas TIDAK saling menumpuk dialog kalau
    // kebetulan terpicu berdekatan dalam satu sesi (mis. app baru dibuka
    // lalu pengguna langsung Force Stop sebuah app). "Tegas" (sesuai
    // pilihan presentasi pengguna) tetap terpenuhi karena dialog ini
    // onDismissRequest={} (lihat AdBlockDialog.kt) — begitu tampil SEKALI,
    // pengguna WAJIB memilih salah satu tombol sebelum bisa lanjut, tidak
    // perlu diulang berkali-kali dalam sesi yang sama untuk tetap terasa
    // tegas.
    @Volatile
    private var shownThisSession = false

    fun requestShow() {
        if (shownThisSession) return
        shownThisSession = true
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }

    fun requestOpenMembership() {
        _openMembershipRequests.tryEmit(Unit)
    }
}
