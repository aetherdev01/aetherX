package com.aether.x.core.locale

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Ambil string sesuai bahasa pilihan pengguna dari kode mana pun
 * (Composable, ViewModel, Repository, Service) tanpa perlu Context.
 *
 * Fetch a string in the user's chosen language from anywhere, no Context needed.
 */
fun str(@StringRes id: Int, vararg args: Any?): String {
    val res = LocaleManager.localizedContext().resources
    return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
}

fun strPlural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
    LocaleManager.localizedContext().resources.getQuantityString(id, quantity, *args)
