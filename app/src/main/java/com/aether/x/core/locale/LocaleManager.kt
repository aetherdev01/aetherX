package com.aether.x.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Pengelola bahasa aplikasi (ID / EN).
 *
 * Pilihan disimpan di SharedPreferences biasa (bukan DataStore) karena harus
 * terbaca SINKRON di `attachBaseContext` sebelum UI dibuat. Setiap Context
 * yang menampilkan teks (Application, Activity, Service, Receiver) wajib
 * dibungkus lewat [wrap] supaya string mengikuti pilihan pengguna, bukan
 * bahasa sistem.
 *
 * App language manager (ID / EN). The choice is stored in plain
 * SharedPreferences so it can be read synchronously in `attachBaseContext`.
 */
object LocaleManager {

    private const val PREFS_NAME = "aetherx_locale"
    private const val KEY_LANGUAGE = "app_language"

    @Volatile
    private var localizedAppContext: Context? = null

    /** Bahasa yang sedang dipilih pengguna. */
    fun current(context: Context): AppLanguage {
        val prefs = context.applicationContextOrSelf()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))
    }

    /**
     * Simpan pilihan bahasa. Setelah memanggil ini, Activity harus di-`recreate()`
     * agar Compose memuat ulang seluruh string.
     */
    fun set(context: Context, language: AppLanguage) {
        val app = context.applicationContextOrSelf()
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
        Locale.setDefault(language.locale)
        localizedAppContext = buildLocalizedContext(app, language)
    }

    /** Bungkus [base] dengan locale pilihan pengguna. Panggil dari attachBaseContext. */
    fun wrap(base: Context): Context {
        val language = current(base)
        Locale.setDefault(language.locale)
        val wrapped = buildLocalizedContext(base, language)
        // Simpan Application context yang sudah dilokalkan untuk str().
        if (localizedAppContext == null) localizedAppContext = wrapped
        return wrapped
    }

    /**
     * Context ber-locale untuk kode non-Compose (ViewModel, Repository, Service).
     * Dipakai oleh [str].
     */
    internal fun localizedContext(): Context =
        localizedAppContext ?: error("LocaleManager.wrap() belum dipanggil dari AetherXApp.attachBaseContext")

    private fun buildLocalizedContext(base: Context, language: AppLanguage): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(language.locale)
        config.setLayoutDirection(language.locale)
        return base.createConfigurationContext(config)
    }

    private fun Context.applicationContextOrSelf(): Context = applicationContext ?: this
}
