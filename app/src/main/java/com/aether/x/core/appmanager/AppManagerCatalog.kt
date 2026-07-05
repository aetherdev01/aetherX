package com.aether.x.core.appmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aether.x.core.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Membaca daftar aplikasi yang BERHAK muncul di App Manager: SEMUA aplikasi
 * pihak ketiga terpasang ([AppOrigin.THIRD_PARTY]) ditambah aplikasi sistem
 * yang namanya ADA di `assets/app_manager_bloatware_whitelist.txt`
 * ([AppOrigin.KNOWN_BLOATWARE]) — mengikuti pola pemisahan katalog+deteksi
 * yang sama dengan [com.aether.x.core.apps.GameProfileCatalog], termasuk
 * fungsi [drawableToImageBitmap] yang identik (didup ulang di sini, bukan
 * di-share langsung, supaya App Manager tetap independen dari package
 * `core.apps` yang murni untuk Game Profile).
 *
 * KENAPA TIDAK MENAMPILKAN SEMUA APP SISTEM SECARA BEBAS: banyak app
 * sistem (`FLAG_SYSTEM`) adalah bagian INTI Android yang kalau di-freeze
 * bisa membuat perangkat bootloop, kehilangan fungsi telepon/SMS, atau
 * Settings force-close. Menampilkan SEMUA app sistem dengan bebas seperti
 * kernel manager sungguhan (mis. Titanium Backup) mengasumsikan pengguna
 * paham mana yang aman — App Manager ini SENGAJA membatasi ke whitelist
 * yang sudah dikurasi (lihat isi file whitelist) supaya risiko salah
 * freeze jauh lebih kecil, konsisten dengan filosofi "tidak mengganggu"
 * yang sama seperti sistem iklan di app ini.
 */
object AppManagerCatalog {

    private const val WHITELIST_ASSET_FILE_NAME = "app_manager_bloatware_whitelist.txt"

    // Cache in-memory hasil parse whitelist — sama alasannya dengan
    // GameProfileCatalog.cachedPackageNames (file assets tidak berubah
    // selama proses app hidup, tidak perlu dibaca ulang tiap kali).
    @Volatile
    private var cachedWhitelist: Set<String>? = null

    private suspend fun readWhitelist(context: Context): Set<String> {
        cachedWhitelist?.let { return it }
        return withContext(Dispatchers.IO) {
            val names = try {
                context.assets.open(WHITELIST_ASSET_FILE_NAME).bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') }
                        .toSet()
                }
            } catch (t: Throwable) {
                emptySet()
            }
            cachedWhitelist = names
            names
        }
    }

    /**
     * Baca semua aplikasi pihak ketiga terpasang + app sistem yang cocok
     * whitelist bloatware, lengkap dengan status frozen SAAT INI (dibaca
     * lewat `pm list packages -d`, satu panggilan shell untuk semua
     * package sekaligus — bukan satu panggilan per app, konsisten dengan
     * alasan efisiensi yang sama seperti [com.aether.x.core.kernel.KernelInfoReader]).
     *
     * Diurutkan: [AppOrigin.THIRD_PARTY] dulu (alfabet), baru
     * [AppOrigin.KNOWN_BLOATWARE] (alfabet) — pihak ketiga biasanya yang
     * paling relevan/dikenal pengguna, bloatware sistem sebagai kategori
     * tambahan di bawahnya.
     */
    suspend fun loadManageableApps(context: Context, executor: ShellExecutor): List<InstalledAppEntry> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val whitelist = readWhitelist(context)
            val frozenPackages = readFrozenPackageNames(executor)

            @Suppress("DEPRECATION") // getInstalledApplications(Int) - API lama tapi masih valid, tidak ada pengganti langsung untuk kebutuhan ini
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            // AetherX sendiri SENGAJA dikecualikan dari daftar pihak ketiga —
            // freeze aplikasi sendiri secara tidak sengaja akan mengunci
            // pengguna keluar dari app ini tanpa cara mudah untuk unfreeze
            // lagi (App Manager-nya sendiri jadi tidak bisa dibuka).
            val ownPackageName = context.packageName

            val thirdParty = allApps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ownPackageName }
                .mapNotNull { toEntry(pm, it, AppOrigin.THIRD_PARTY, frozenPackages) }
                .sortedBy { it.label.lowercase() }

            val bloatware = allApps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 && it.packageName in whitelist }
                .mapNotNull { toEntry(pm, it, AppOrigin.KNOWN_BLOATWARE, frozenPackages) }
                .sortedBy { it.label.lowercase() }

            thirdParty + bloatware
        }

    /**
     * `pm list packages -d` mengembalikan baris berformat `package:nama.paket`
     * untuk SEMUA package yang statusnya disabled (baik lewat App Manager
     * ini maupun lewat cara lain) — dibaca sekali di awal lalu dicocokkan
     * per-app, bukan query status satu-satu per package (lebih efisien).
     */
    private suspend fun readFrozenPackageNames(executor: ShellExecutor): Set<String> {
        val result = executor.exec("pm list packages -d")
        return result.output
            .mapNotNull { line -> line.trim().removePrefix("package:").takeIf { it.isNotBlank() } }
            .toSet()
    }

    private fun toEntry(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        origin: AppOrigin,
        frozenPackages: Set<String>,
    ): InstalledAppEntry? {
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (t: Throwable) {
            appInfo.packageName
        }
        val icon = try {
            drawableToImageBitmap(pm.getApplicationIcon(appInfo))
        } catch (t: Throwable) {
            return null
        }
        return InstalledAppEntry(
            packageName = appInfo.packageName,
            label = label,
            icon = icon,
            origin = origin,
            isFrozen = appInfo.packageName in frozenPackages,
        )
    }

    /** Identik dengan [com.aether.x.core.apps.GameProfileCatalog] — lihat KDoc di sana untuk alasan implementasinya. */
    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
