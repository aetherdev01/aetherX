package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

/**
 * Menerjemahkan nilai tweak (DPI, resolusi, pointer speed, refresh rate) menjadi
 * perintah shell Android resmi (`wm`, `settings`). Semua perintah ini sama
 * persis dengan yang dipakai lewat `adb shell`, hanya dijalankan lewat
 * [ShellExecutor] (Shizuku atau root) alih-alih kabel USB.
 */
class TweakRepository {

    suspend fun applyDensity(executor: ShellExecutor, dpi: Int): ShellResult =
        executor.exec("wm density $dpi")

    suspend fun resetDensity(executor: ShellExecutor): ShellResult =
        executor.exec("wm density reset")

    suspend fun applySize(executor: ShellExecutor, widthPx: Int, heightPx: Int): ShellResult =
        executor.exec("wm size ${widthPx}x${heightPx}")

    suspend fun resetSize(executor: ShellExecutor): ShellResult =
        executor.exec("wm size reset")

    suspend fun applyPointerSpeed(executor: ShellExecutor, speed: Int): ShellResult =
        executor.exec("settings put system pointer_speed $speed")

    /**
     * Eksperimental: key ini hanya berdampak nyata di sebagian perangkat
     * (mis. beberapa seri Samsung). Kegagalan di sini tidak dianggap fatal
     * karena memang tidak semua ROM mendukungnya.
     */
    suspend fun applyTouchBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val value = if (enabled) 1 else 0
        return executor.exec("settings put secure touch_sensitivity_enable $value")
    }

    suspend fun applyRefreshRate(executor: ShellExecutor, enabled: Boolean, maxHz: Float): ShellResult {
        return if (enabled) {
            executor.exec("settings put system peak_refresh_rate $maxHz; settings put system min_refresh_rate $maxHz")
        } else {
            executor.exec("settings delete system peak_refresh_rate; settings delete system min_refresh_rate")
        }
    }

    /**
     * Mode Game: mengaktifkan Do Not Disturb sistem supaya notifikasi tidak mengganggu
     * saat bermain.
     *
     * CATATAN PERBAIKAN BUG: sebelumnya ini hanya menulis `settings put global zen_mode`
     * secara langsung. Di Android modern (12+/One UI), menulis key itu saja TIDAK
     * benar-benar mengaktifkan DND — sistem notifikasi (NotificationManager) memvalidasi
     * ulang lewat rute "notification policy" resmi, jadi togglenya sering terlihat tidak
     * berefek sama sekali (bahkan saat perintah shell-nya "sukses"). Perintah resmi yang
     * benar-benar dipakai oleh System UI sendiri untuk toggle DND adalah `cmd notification
     * set_dnd`, yang menjamin konsistensi dengan status yang dibaca ulang lewat
     * `settings get global zen_mode`.
     *
     * `cmd notification set_dnd priority` = DND (Prioritas), setara "Jangan Ganggu" biasa.
     * `cmd notification set_dnd off` = kembali normal.
     * Sama seperti `adb shell cmd notification set_dnd priority`, hanya dijalankan lewat
     * Shizuku/root. Sebagai fallback (perangkat yang tidak mengenali subcommand ini),
     * kita tetap sertakan `settings put global zen_mode` sebagai upaya kedua.
     */
    suspend fun applyGameMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val mode = if (enabled) "priority" else "off"
        val zenValue = if (enabled) 2 else 0
        val primary = executor.exec("cmd notification set_dnd $mode")
        if (primary.success) return primary

        // Fallback untuk ROM/perangkat yang belum mengenali `cmd notification set_dnd`.
        return executor.exec("settings put global zen_mode $zenValue")
    }

    /**
     * Khusus root: kunci semua core CPU ke governor "performance" (clock
     * selalu maksimum) selama bermain, mengurangi micro-stutter akibat
     * frekuensi naik-turun. Dikembalikan ke "schedutil" (default umum kernel
     * modern) saat dimatikan. Butuh akses tulis ke /sys/devices/system/cpu,
     * yang biasanya hanya bisa lewat root (bukan Shizuku/adb shell biasa).
     */
    suspend fun applyCpuPerformanceMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val governor = if (enabled) "performance" else "schedutil"
        return executor.exec(
            "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$g; done",
        )
    }

    /**
     * Khusus root: turunkan swappiness kernel supaya sistem lebih jarang
     * menukar (swap) data game ke zram/disk, menjaga proses game tetap di
     * RAM. Dikembalikan ke nilai default (60) saat dimatikan. Butuh akses
     * tulis ke /proc/sys/vm, yang biasanya hanya bisa lewat root.
     */
    suspend fun applyRamPriority(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val value = if (enabled) 10 else 60
        return executor.exec("echo $value > /proc/sys/vm/swappiness")
    }

    /**
     * Khusus root: menaikkan batas suhu shutdown/throttle di zona termal
     * kernel supaya sistem tidak buru-buru menurunkan clock CPU/GPU saat
     * perangkat mulai panas bermain lama. Nilai ditulis dalam milidegree
     * Celsius (mis. 90000 = 90°C) ke setiap `trip_point_0_temp` yang ada.
     * Tidak ada nilai "default pabrik" yang seragam antar chipset, jadi saat
     * dimatikan kita tidak menulis ulang apa pun — cukup andalkan reboot
     * atau reset manual perangkat untuk kembali ke batas asli vendor.
     * Butuh akses tulis ke /sys/class/thermal, hanya bisa lewat root.
     */
    suspend fun applyThermalThrottleOverride(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                "for z in /sys/class/thermal/thermal_zone*/trip_point_0_temp; do " +
                    "echo 90000 > \$z; done",
            )
        } else {
            executor.exec("echo 'thermal override dimatikan, restart perangkat untuk memulihkan batas asli vendor'")
        }
    }

    /**
     * Khusus root: kunci frekuensi GPU ke nilai maksimum yang didukung
     * (governor "performance"), mirip cara kerja Mode Performa CPU. Path
     * governor GPU tidak seragam antar chipset (Adreno/Mali/dsb), jadi
     * perintah ini mencoba beberapa lokasi umum sekaligus — yang tidak ada
     * di perangkat akan otomatis diabaikan shell tanpa membuat proses gagal.
     * Dikembalikan ke "simple_ondemand"/"default" saat dimatikan.
     * Butuh akses tulis ke /sys/class/kgsl atau /sys/devices/[chip]/kgsl-3d0,
     * yang biasanya hanya bisa lewat root.
     */
    suspend fun applyGpuPerformanceMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val governor = if (enabled) "performance" else "simple_ondemand"
        return executor.exec(
            "for g in /sys/class/kgsl/kgsl-3d0/devfreq/governor " +
                "/sys/devices/platform/*/kgsl-3d0/devfreq/governor; do " +
                "[ -f \$g ] && echo $governor > \$g; done",
        )
    }

    /**
     * Khusus root: ganti I/O scheduler storage internal ke "kyber" (kalau
     * tersedia di kernel) yang dioptimalkan untuk latensi baca rendah —
     * cocok untuk baca aset game besar. Kalau kyber tidak didukung kernel,
     * dicoba fallback ke "bfq" yang juga lebih baik dari "noop"/"none"
     * untuk beban baca-tulis campuran. Dikembalikan ke governor default
     * kernel modern ("mq-deadline") saat dimatikan. Butuh akses tulis ke
     * /sys/block/*/queue/scheduler, hanya bisa lewat root.
     */
    suspend fun applyIoSchedulerBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                "for q in /sys/block/*/queue/scheduler; do " +
                    "(echo kyber > \$q 2>/dev/null || echo bfq > \$q 2>/dev/null); done",
            )
        } else {
            executor.exec(
                "for q in /sys/block/*/queue/scheduler; do " +
                    "echo mq-deadline > \$q 2>/dev/null; done",
            )
        }
    }

    /**
     * Khusus root: hentikan proses aplikasi pihak ketiga yang sedang berjalan
     * di background (bukan aplikasi sistem) lewat `am kill` per paket,
     * membebaskan RAM/CPU sebelum sesi bermain. Ini aksi SEKALI JALAN
     * (one-shot), bukan toggle yang mengubah kondisi permanen — makanya
     * tidak ada "kondisi mati" untuk dikembalikan, dan [enabled] dipakai
     * murni sebagai sinyal switch dinyalakan (bukan disimpan sebagai status
     * berkelanjutan seperti tweak root lain). Daftar paket berjalan diambil
     * lewat `pm list packages -3` (paket pihak ketiga) lalu satu per satu
     * dihentikan lewat `am kill`, yang sama seperti tombol "Force stop"
     * tapi tanpa dialog konfirmasi.
     */
    suspend fun applyKillBackgroundApps(executor: ShellExecutor, enabled: Boolean): ShellResult {
        if (!enabled) return ShellResult(success = true)
        return executor.exec(
            "for p in \$(pm list packages -3 | sed 's/package://'); do am kill \$p; done",
        )
    }

    /**
     * Khusus root: paksa heap Dalvik/ART jadi lebih besar & tunda GC lewat
     * properti sistem `dalvik.vm.heapgrowthlimit`/`dalvik.vm.heapsize`,
     * mengurangi jeda micro-stutter akibat garbage collection saat bermain.
     * Properti ini dibaca ulang oleh Zygote/ART, jadi perubahan baru terasa
     * penuh untuk proses yang baru dimulai setelah tweak diaktifkan (proses
     * yang sudah berjalan tidak terpengaruh retroaktif). Dikembalikan ke
     * nilai default umum AOSP saat dimatikan. Butuh akses tulis ke
     * properti sistem lewat `setprop`, hanya bisa lewat root (Shizuku/adb
     * shell biasa tidak diizinkan menulis properti `dalvik.vm.*`).
     */
    suspend fun applyVmHeapBoost(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                "setprop dalvik.vm.heapgrowthlimit 512m; setprop dalvik.vm.heapsize 1024m",
            )
        } else {
            executor.exec(
                "setprop dalvik.vm.heapgrowthlimit 256m; setprop dalvik.vm.heapsize 512m",
            )
        }
    }

    suspend fun resetAll(executor: ShellExecutor): List<ShellResult> = listOf(
        resetDensity(executor),
        resetSize(executor),
        applyPointerSpeed(executor, 0),
        applyTouchBoost(executor, false),
        applyRefreshRate(executor, enabled = false, maxHz = 60f),
        applyGameMode(executor, enabled = false),
        applyCpuPerformanceMode(executor, enabled = false),
        applyRamPriority(executor, enabled = false),
        applyThermalThrottleOverride(executor, enabled = false),
        applyGpuPerformanceMode(executor, enabled = false),
        applyIoSchedulerBoost(executor, enabled = false),
        applyVmHeapBoost(executor, enabled = false),
    )
}
