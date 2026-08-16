package com.aether.x.core.security

import android.content.Context
import android.util.Log
import com.aether.x.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

object AppCheckInitializer {

    private const val TAG = "AppCheckInitializer"

    fun init(context: Context) {
        runCatching {
            Firebase.initialize(context)

            // DEBUG BUILD: pakai Debug provider, BUKAN Play Integrity.
            //
            // Kenapa: Play Integrity API cuma mengeluarkan token valid untuk
            // APK yang didistribusikan lewat jalur resmi Play Console
            // (internal testing/production) ATAU device yang didaftarkan
            // sebagai license tester di Play Console > Setup > App
            // Integrity. APK hasil GitHub Actions artifact yang di-sideload
            // manual TIDAK lolos verifikasi ini walau signing key-nya sama
            // persis dengan yang dipakai release asli — ini penyebab
            // "membership tidak terhubung server" (Cloud Function menolak
            // dengan UNAUTHENTICATED sebelum logic lisensi sempat jalan).
            //
            // DebugAppCheckProviderFactory menghasilkan token debug lokal.
            // Saat run pertama, Logcat (tag "DebugAppCheckProvider" dari
            // Firebase SDK, BUKAN tag kita) akan mencetak sebuah UUID debug
            // token — token itu WAJIB didaftarkan manual di Firebase Console
            // > App Check > (pilih app) > Manage debug tokens, supaya
            // Cloud Function mau menerimanya. Tanpa didaftarkan di sana,
            // debug provider ini TETAP akan ditolak server persis seperti
            // Play Integrity yang gagal.
            //
            // WAJIB: BuildConfig.DEBUG false untuk build release yang
            // benar-benar didistribusikan lewat Play Store — kalau tidak,
            // release asli ikut pakai debug provider dan jadi lubang
            // keamanan (siapa pun bisa daftar token debug sendiri).
            val providerFactory = if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }

            Firebase.appCheck.installAppCheckProviderFactory(providerFactory)
        }.onFailure { e ->

            Log.e(TAG, "Gagal memasang App Check provider", e)
        }
    }
}
