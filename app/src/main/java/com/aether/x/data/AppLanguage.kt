package com.aether.x.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Bahasa aplikasi — FITUR BARU (permintaan "tambahkan beberapa fitur baru
 * di Settings"), MENGGANTIKAN opsi Satuan Suhu yang dihapus (permintaan
 * "hapus opsi suhu"). Terintegrasi penuh dengan [AppPreferences.appLanguage]
 * + [AetherXPreferences.setAppLanguage] (lihat file itu untuk penyimpanan
 * DataStore-nya) dan dipilih dari [com.aether.x.ui.settings.SettingsScreen].
 *
 * BUG FIX (lihat error CI: "Unresolved reference 'appcompat'" /
 * "Unresolved reference 'AppCompatDelegate'"): implementasi SEBELUMNYA
 * memakai `androidx.appcompat.app.AppCompatDelegate.setApplicationLocales`
 * (per-app language API AndroidX) — TAPI project ini (lihat app/build.gradle.kts)
 * TIDAK mendeklarasikan dependency `androidx.appcompat:appcompat` sama
 * sekali (dan tidak memakai version-catalog entry untuk itu), jadi class
 * tersebut tidak ada di classpath dan build gagal. [MainActivity] juga
 * `ComponentActivity` biasa, BUKAN `AppCompatActivity`, mengonfirmasi
 * project ini sengaja tidak memakai AppCompat.
 *
 * SEKARANG: memakai API `android.content.res.Configuration`/
 * `Context.createConfigurationContext` BAWAAN Android SDK murni (API level
 * 17+, jauh di bawah `minSdk = 32` project ini) — TIDAK butuh dependency
 * tambahan apa pun. Konsekuensinya, penerapan locale di sini TIDAK otomatis
 * persisten oleh sistem seperti AppCompatDelegate — locale yang tersimpan
 * di [AetherXPreferences] (DataStore) SEKARANG jadi SATU-SATUNYA sumber
 * kebenaran, dan [wrapContext] (lihat KDoc-nya, di companion object di
 * bawah) idealnya dipanggil dari `Activity.attachBaseContext` setiap kali
 * sebuah Activity dibuat — bukan cuma sekali saat pengguna mengganti
 * pilihan lewat [applyToRunningActivity].
 */
enum class AppLanguage(val languageTag: String) {
    INDONESIAN("in"),
    ENGLISH("en"),
    ;

    /** [Locale] Java/Android yang merepresentasikan bahasa ini. */
    fun toLocale(): Locale = Locale.forLanguageTag(languageTag)

    /**
     * Terapkan bahasa ini SEKARANG JUGA ke [activity] yang sedang berjalan
     * dengan cara paling andal untuk mengganti locale runtime tanpa
     * AppCompat: update [Locale] default JVM + [Configuration] resources
     * Activity, lalu [Activity.recreate] supaya SEMUA string/Composable
     * yang sudah terlanjur ter-render ulang terbaca dengan locale baru
     * (`stringResource` di Compose membaca ulang resources saat Activity
     * dibuat ulang, tidak akan otomatis berubah tanpa recreate).
     *
     * Sepenuhnya SYNCHRONOUS (tidak menunggu apa pun) — aman dipanggil
     * langsung setelah `viewModel.setAppLanguage(language)` walau write ke
     * DataStore di situ berjalan async lewat coroutine, karena fungsi ini
     * tidak bergantung pada DataStore sama sekali (locale JVM & Configuration
     * di-set langsung dari parameter [language] yang sudah diketahui, bukan
     * dibaca ulang dari preferences).
     *
     * Dipanggil dari [com.aether.x.ui.settings.SettingsScreen] (bukan dari
     * ViewModel — ViewModel hanya punya [android.app.Application], tidak
     * punya akses ke [Activity] sungguhan untuk memanggil `recreate()`) saat
     * pengguna mengubah pilihan Bahasa secara manual di Settings.
     */
    fun applyToRunningActivity(activity: Activity) {
        val locale = toLocale()
        Locale.setDefault(locale)
        val resources = activity.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        activity.recreate()
    }

    companion object {
        /**
         * Bungkus [base] dengan [Context] baru yang sudah memakai locale
         * [language] — dipanggil dari `Activity.attachBaseContext(newBase)`
         * SETIAP Activity dibuat (termasuk saat sistem me-recreate Activity
         * akibat rotasi layar, bukan cuma [applyToRunningActivity]), supaya
         * pilihan bahasa yang tersimpan di [AetherXPreferences] (DataStore)
         * tetap terpakai walau app baru saja di-restart penuh (mis. setelah
         * force-stop / reboot device) — TANPA ini, locale akan kembali ke
         * bahasa sistem device setiap Activity baru dibuat dari nol.
         *
         * CATATAN INTEGRASI: [com.aether.x.MainActivity] SAAT INI belum
         * meng-override `attachBaseContext` untuk memanggil fungsi ini —
         * perlu ditambahkan manual:
         * ```
         * override fun attachBaseContext(newBase: Context) {
         *     val prefs = AetherXPreferences(newBase)
         *     val language = runBlocking { prefs.preferences.first().appLanguage }
         *     super.attachBaseContext(AppLanguage.wrapContext(newBase, language))
         * }
         * ```
         * (atau baca locale tersimpan lewat cara sinkron lain yang sudah
         * ada di project ini kalau `runBlocking` di titik ini tidak
         * diinginkan) — sebelum baris ini ditambahkan, bahasa yang dipilih
         * TETAP langsung terlihat berkat [applyToRunningActivity] (dipanggil
         * saat mengganti pilihan), HANYA SAJA belum otomatis ter-restore
         * kalau app di-restart total dari luar (mis. dari recent-apps/force-stop).
         */
        fun wrapContext(base: Context, language: AppLanguage): Context {
            val locale = language.toLocale()
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
