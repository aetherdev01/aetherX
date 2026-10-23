package com.aether.x.core.locale

import java.util.Locale

/**
 * Bahasa yang didukung aplikasi.
 *
 * Folder `values/` berisi Bahasa Indonesia (default), `values-en/` berisi
 * Bahasa Inggris. Menambah bahasa baru cukup menambah entri di sini dan
 * folder `values-<kode>/` yang sesuai.
 *
 * Supported app languages. `values/` holds Indonesian (default) and
 * `values-en/` holds English.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English");

    val locale: Locale get() = Locale(tag)

    companion object {
        val DEFAULT = INDONESIAN

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
