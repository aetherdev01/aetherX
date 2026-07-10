package com.aether.x.core.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Satu entri game yang terdaftar di `assets/gamelist.txt` DAN terpasang di
 * perangkat pengguna — satu-satunya kombinasi yang punya nama & ikon asli
 * untuk ditampilkan (lihat [GameProfileCatalog.loadInstalledGames]).
 *
 * [icon] sengaja sudah berupa [ImageBitmap] (bukan [Drawable] mentah) supaya
 * siap dipakai langsung oleh `Image(bitmap = ...)` di Compose tanpa
 * konversi ulang tiap recomposition.
 */
data class InstalledGameEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

/**
 * Membaca daftar package game dari `assets/gamelist.txt` (satu package name
 * per baris, format sama seperti daftar yang dipakai komunitas GameSpace/
 * tweak tools lain) lalu mencocokkannya dengan aplikasi yang BENAR-BENAR
 * terpasang di perangkat lewat [PackageManager] — supaya nama tampilan dan
 * ikon yang ditunjukkan adalah data asli dari APK terpasang, bukan hasil
 * dump/tebakan statis yang bisa basi atau salah untuk game yang belum rilis
 * di region pengguna.
 *
 * Game yang ada di gamelist.txt tapi TIDAK terpasang tidak akan muncul sama
 * sekali di hasil — tidak ada nama/ikon yang bisa ditampilkan untuk APK yang
 * tidak ada di perangkat, dan menampilkannya dengan ikon generik hanya akan
 * membuat daftar penuh entri yang tidak bisa diklik.
 */
object GameProfileCatalog {

    private const val ASSET_FILE_NAME = "gamelist.txt"

    // Cache in-memory hasil parse gamelist.txt (bukan hasil deteksi terpasang,
    // yang itu sengaja tidak di-cache karena game bisa install/uninstall
    // kapan saja) — supaya file assets tidak dibaca ulang dari disk tiap kali
    // layar Game Profile dibuka.
    @Volatile
    private var cachedPackageNames: List<String>? = null

    /**
     * Baca & parse `gamelist.txt` sekali, mengembalikan daftar package name
     * (baris kosong dan baris yang jelas bukan package name dilewati).
     * Hasilnya di-cache in-memory untuk pemanggilan berikutnya.
     */
    private suspend fun readCatalogPackageNames(context: Context): List<String> {
        cachedPackageNames?.let { return it }
        return withContext(Dispatchers.IO) {
            val names = try {
                context.assets.open(ASSET_FILE_NAME).bufferedReader().useLines { lines ->
                    lines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') }
                        .distinct()
                        .toList()
                }
            } catch (t: Throwable) {
                emptyList()
            }
            cachedPackageNames = names
            names
        }
    }

    /**
     * Mengembalikan game dari gamelist.txt yang terpasang di perangkat,
     * lengkap dengan nama tampilan & ikon ASLI yang dibaca langsung dari
     * [PackageManager] — diurutkan alfabet berdasarkan nama tampilan supaya
     * mudah dicari pengguna di sidebar Game Profile.
     */
    suspend fun loadInstalledGames(context: Context): List<InstalledGameEntry> =
        withContext(Dispatchers.IO) {
            val catalog = readCatalogPackageNames(context)
            val pm = context.packageManager
            catalog.mapNotNull { packageName -> loadEntryIfInstalled(pm, packageName) }
                .sortedBy { it.label.lowercase() }
        }

    /**
     * FITUR BARU (Game Booster — lihat perintah rework: "saat buka game
     * game booster jadi side bar/floating"): cek CEPAT apakah [packageName]
     * termasuk game yang dikenal `gamelist.txt`, TANPA scan
     * [PackageManager] (yang mahal untuk dipanggil tiap siklus poll
     * beberapa detik sekali) — cukup cek keberadaan di
     * [readCatalogPackageNames] yang SUDAH di-cache in-memory. Dipakai
     * [com.aether.x.core.monitor.GameProfileMonitorService] untuk memutuskan
     * kapan floating sidebar Game Booster perlu di-trigger otomatis,
     * TERLEPAS dari apakah game itu punya [com.aether.x.data.GameProfile]
     * tersimpan atau tidak (beda dari trigger Game Profile yang HANYA
     * berlaku untuk game yang profilnya sudah dikustomisasi pengguna).
     */
    suspend fun isKnownGamePackage(context: Context, packageName: String): Boolean =
        readCatalogPackageNames(context).contains(packageName)

    /**
     * FITUR BARU (Game Booster — lihat perintah rework floating booster:
     * card game di floating booster menampilkan ikon ASLI game, bukan
     * ikon generik): memuat [ImageBitmap] langsung dari [packageName],
     * TANPA perlu [InstalledGameEntry] lengkap atau cek keanggotaan
     * `gamelist.txt` — dipakai oleh
     * [com.aether.x.core.overlay.GameBoosterOverlayService] yang cuma
     * punya packageName dari sesi Game Booster aktif (lihat
     * [com.aether.x.core.booster.GameBoosterSession.icon]). Null kalau
     * package sudah di-uninstall atau iconnya gagal dibaca.
     */
    suspend fun loadIconForPackage(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                drawableToImageBitmap(pm.getApplicationIcon(appInfo))
            } catch (t: Throwable) {
                null
            }
        }

    private fun loadEntryIfInstalled(pm: PackageManager, packageName: String): InstalledGameEntry? {
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (t: Throwable) {
            packageName
        }
        val icon = try {
            drawableToImageBitmap(pm.getApplicationIcon(appInfo))
        } catch (t: Throwable) {
            return null
        }
        return InstalledGameEntry(packageName = packageName, label = label, icon = icon)
    }

    /**
     * Konversi manual [Drawable] -> [ImageBitmap] tanpa perlu menambah
     * dependency pihak ketiga (mis. Coil) hanya untuk menampilkan ikon
     * aplikasi yang sudah tersedia langsung dari [PackageManager]. Ikon
     * aplikasi biasanya berupa [android.graphics.drawable.AdaptiveIconDrawable]
     * atau bitmap sederhana; `draw(canvas)` generik ini menangani keduanya.
     *
     * DIUBAH dari `private` menjadi `internal` (lihat perintah rework
     * floating booster) supaya bisa dipakai ulang oleh
     * [loadIconForPackage] di atas TANPA duplikasi logic konversi
     * Drawable->ImageBitmap yang sama persis.
     */
    internal fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
