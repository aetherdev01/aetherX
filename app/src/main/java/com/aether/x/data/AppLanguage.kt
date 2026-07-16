package com.aether.x.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Bahasa aplikasi — FITUR BARU (permintaan "tambahkan beberapa fitur baru
 * di Settings"), MENGGANTIKAN opsi Satuan Suhu yang dihapus (permintaan
 * "hapus opsi suhu"). Terintegrasi penuh dengan [AppPreferences.appLanguage]
 * + [AetherXPreferences.setAppLanguage] (lihat file itu untuk penyimpanan
 * DataStore-nya) dan dipilih dari [com.aether.x.ui.settings.SettingsScreen].
 *
 * CATATAN: pemanggilan eksplisit [applyToApp] di [AetherXPreferences] hanya
 * terjadi saat pengguna MENGUBAH pilihan bahasa (lewat [setAppLanguage]) —
 * TIDAK perlu dipanggil ulang manual setiap start-up aplikasi, karena
 * `AppCompatDelegate.setApplicationLocales` sendiri SUDAH dipersist otomatis
 * oleh AndroidX (disimpan terpisah dari DataStore app ini) dan diterapkan
 * ulang otomatis oleh sistem setiap process aplikasi dibuat, termasuk
 * setelah force-stop atau reboot device. Nilai `appLanguage` di
 * [AppPreferences]/DataStore murni untuk keperluan UI (menandai pilihan mana
 * yang aktif tercentang di [com.aether.x.ui.settings.SettingsScreen]), bukan
 * satu-satunya sumber kebenaran locale yang sedang berlaku.
 *
 * DEPENDENCY: memerlukan `androidx.appcompat:appcompat` di `build.gradle`
 * modul app (untuk `AppCompatDelegate`) — API ini tetap berfungsi normal
 * walau [com.aether.x.MainActivity] adalah `ComponentActivity` biasa (BUKAN
 * `AppCompatActivity`), TIDAK perlu mengubah base class Activity yang ada.
 * `build.gradle` tidak ikut dalam zip ini, jadi kalau dependency itu belum
 * ada di project, tambahkan `implementation("androidx.appcompat:appcompat:1.7.0")`
 * (atau versi lebih baru yang kompatibel) sebelum mem-build.
 */
enum class AppLanguage(val languageTag: String) {
    INDONESIAN("in"),
    ENGLISH("en"),
    ;

    /**
     * Terapkan bahasa ini ke seluruh aplikasi lewat Android per-app language
     * API (AndroidX AppCompat) — bekerja di semua versi Android yang
     * didukung minSdk project ini, TIDAK memerlukan restart Activity manual
     * (sistem otomatis me-recreate Activity yang sedang aktif dengan locale
     * baru setelah dipanggil).
     */
    fun applyToApp() {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    companion object {
        /** Baca locale aplikasi yang SEDANG aktif saat ini, fallback ke [INDONESIAN] kalau belum pernah di-set. */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }
                ?.get(0)?.language
            return entries.firstOrNull { it.languageTag == tag } ?: INDONESIAN
        }
    }
}
