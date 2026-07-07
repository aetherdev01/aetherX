package com.aether.x.data

import com.aether.x.core.shell.ShellExecutor
import com.aether.x.core.shell.ShellResult

/**
 * Pilihan governor CPU untuk section Root di tab Tweak. [UNIVERSAL] bukan
 * nama governor kernel sungguhan — ini mode "otomatis" yang menyuruh
 * [TweakRepository.applyCpuGovernor] memilih sendiri governor hemat-daya
 * standar yang didukung kernel perangkat (schedutil > interactive > walt >
 * ondemand > conservative > lainnya yang tersedia), aman dipakai lintas
 * chipset Snapdragon/MediaTek/Exynos tanpa pengguna perlu tahu governor apa
 * yang cocok untuk HP-nya.
 */
enum class CpuGovernor(val sysfsName: String?) {
    SCHEDUTIL("schedutil"),
    PERFORMANCE("performance"),
    ONDEMAND("ondemand"),
    POWERSAVE("powersave"),
    UNIVERSAL(null),
}

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
     * Khusus root: terapkan governor CPU pilihan pengguna (Schedutil,
     * Performance, Ondemand, Battery/Powersave, atau Universal) ke semua
     * core sekaligus. Butuh akses tulis ke /sys/devices/system/cpu, yang
     * biasanya hanya bisa lewat root (bukan Shizuku/adb shell biasa).
     *
     * DUKUNGAN MULTI-CHIPSET: untuk [CpuGovernor.UNIVERSAL] (mode "otomatis
     * sesuai CPU bawaan"), nama governor TIDAK dipaksa satu nilai tetap —
     * banyak chipset MediaTek (Helio/Dimensity) memakai kernel yang
     * defaultnya "walt" atau "interactive" dan tidak mengenali "schedutil"
     * sama sekali (tulisan ke situ gagal diam-diam), sementara kernel
     * Snapdragon/Exynos modern umumnya default ke "schedutil". Governor
     * dipilih otomatis PER-CORE dari daftar governor yang benar-benar
     * didukung kernel tersebut (dibaca dari scaling_available_governors),
     * diprioritaskan ke governor hemat daya standar (schedutil > interactive
     * > walt > ondemand > conservative > lain-lain yang tersedia).
     *
     * Untuk governor eksplisit lain (Performance/Ondemand/Battery), nama
     * governor dituliskan langsung ke tiap core — kalau kernel perangkat
     * tidak mendukung nama tersebut, penulisan ke file itu gagal diam-diam
     * (perilaku sysfs standar) dan core itu tetap pada governor sebelumnya.
     */
    suspend fun applyCpuGovernor(executor: ShellExecutor, governor: CpuGovernor): ShellResult {
        val script = if (governor == CpuGovernor.UNIVERSAL) {
            """
            for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
              avail_file="${'$'}(dirname ${'$'}g)/scaling_available_governors"
              avail="${'$'}(cat ${'$'}avail_file 2>/dev/null)"
              chosen="schedutil"
              for candidate in schedutil interactive walt ondemand conservative; do
                case " ${'$'}avail " in
                  *" ${'$'}candidate "*) chosen="${'$'}candidate"; break ;;
                esac
              done
              echo ${'$'}chosen > ${'$'}g 2>/dev/null
            done
            """.trimIndent()
        } else {
            val name = governor.sysfsName
            """
            for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
              echo $name > ${'$'}g 2>/dev/null
            done
            """.trimIndent()
        }
        return executor.exec(script)
    }

    /**
     * Khusus root: turunkan swappiness kernel supaya sistem lebih jarang
     * menukar (swap) data game ke zram/disk, menjaga proses game tetap di
     * RAM. Dikembalikan ke nilai default (60) saat dimatikan. Butuh akses
     * tulis ke /proc/sys/vm, yang biasanya hanya bisa lewat root. Path ini
     * generik di semua kernel Linux/Android (Qualcomm/MediaTek/Exynos sama
     * saja), jadi tidak perlu penyesuaian per-chipset seperti governor CPU/GPU.
     */
    suspend fun applyRamPriority(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val value = if (enabled) 10 else 60
        return executor.exec("echo $value > /proc/sys/vm/swappiness 2>/dev/null")
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
     *
     * DUKUNGAN MULTI-CHIPSET: beberapa kernel MediaTek menandai sebagian
     * trip_point sebagai read-only (mis. zona "mtktscpu"/"tzs" pada
     * beberapa Helio/Dimensity), sehingga menulis ke situ gagal walau file-
     * nya ada. Penulisan sekarang dibungkus percobaan-diam (`2>/dev/null`)
     * per zona alih-alih satu perintah gabungan, supaya satu zona read-only
     * tidak menggagalkan penulisan ke zona lain yang writable di perangkat
     * yang sama.
     */
    suspend fun applyThermalThrottleOverride(executor: ShellExecutor, enabled: Boolean): ShellResult {
        return if (enabled) {
            executor.exec(
                """
                for z in /sys/class/thermal/thermal_zone*/trip_point_0_temp; do
                  echo 90000 > ${'$'}z 2>/dev/null
                done
                """.trimIndent(),
            )
        } else {
            executor.exec("echo 'thermal override dimatikan, restart perangkat untuk memulihkan batas asli vendor'")
        }
    }

    /**
     * Khusus root: kunci frekuensi GPU ke nilai maksimum yang didukung
     * selama bermain, mengurangi drop FPS akibat governor GPU turun-naik.
     * Butuh akses tulis ke sysfs GPU vendor, hanya bisa lewat root.
     *
     * DUKUNGAN MULTI-CHIPSET: versi sebelumnya cuma menyasar path kgsl
     * (driver Adreno/Qualcomm) — di perangkat MediaTek path itu tidak
     * pernah ada sama sekali sehingga tweak ini diam-diam tidak berefek.
     * Sekarang mencoba SEMUA jalur vendor umum sekaligus; yang tidak ada di
     * perangkat otomatis dilewati (dicek dengan `[ -f ... ]` dulu) tanpa
     * membuat proses gagal:
     *  - Qualcomm/Adreno: /sys/class/kgsl/kgsl-3d0/devfreq/governor
     *  - MediaTek/Mali (skema devfreq umum di Helio & sebagian Dimensity):
     *    /sys/class/devfreq/(dot)mali/governor atau (star)mali(star)/governor
     *  - MediaTek/Mali (skema GED, dipakai banyak Dimensity modern —
     *    "ged" = Graphics Engine Driver bawaan MediaTek yang mengatur
     *    frequency scaling GPU terpisah dari devfreq generik):
     *    /sys/module/ged/parameters/gpu_freq_bound (kunci ke frekuensi
     *    maksimum yang dibaca dari gpufreq_opp_freq_0, bukan governor)
     *  - Mali generik (Exynos/sebagian MediaTek lain):
     *    /sys/class/misc/mali0/device/dvfs_governor atau /sys/devices/platform/(star)/devfreq/(star)mali(star)/governor
     * Dikembalikan ke governor/mode hemat daya default masing-masing jalur
     * saat dimatikan; jalur GED dikembalikan dengan menghapus batas (echo 0).
     */
    suspend fun applyGpuPerformanceMode(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val devfreqGovernor = if (enabled) "performance" else "simple_ondemand"
        val misGovernor = if (enabled) "performance" else "default"
        val gedBlock = if (enabled) {
            """
            if [ -f /sys/module/ged/parameters/gpu_freq_bound ] && [ -f /proc/gpufreq/gpufreq_opp_freq ]; then
              max_freq="${'$'}(head -n1 /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | awk '{print ${'$'}1}')"
              [ -n "${'$'}max_freq" ] && echo ${'$'}max_freq > /sys/module/ged/parameters/gpu_freq_bound 2>/dev/null
            fi
            """.trimIndent()
        } else {
            """
            [ -f /sys/module/ged/parameters/gpu_freq_bound ] && echo 0 > /sys/module/ged/parameters/gpu_freq_bound 2>/dev/null
            """.trimIndent()
        }
        val script = """
            # Qualcomm/Adreno
            for g in /sys/class/kgsl/kgsl-3d0/devfreq/governor /sys/devices/platform/*/kgsl-3d0/devfreq/governor; do
              [ -f ${'$'}g ] && echo $devfreqGovernor > ${'$'}g 2>/dev/null
            done
            # MediaTek/Mali & Exynos/Mali — skema devfreq umum
            for g in /sys/class/devfreq/*mali*/governor /sys/devices/platform/*/devfreq/*mali*/governor; do
              [ -f ${'$'}g ] && echo $devfreqGovernor > ${'$'}g 2>/dev/null
            done
            # Mali generik lewat node misc
            for g in /sys/class/misc/mali0/device/dvfs_governor; do
              [ -f ${'$'}g ] && echo $misGovernor > ${'$'}g 2>/dev/null
            done
            # MediaTek GED (Dimensity modern) — kunci/lepas batas frekuensi opp tertinggi
            $gedBlock
        """.trimIndent()
        return executor.exec(script)
    }

    /**
     * FITUR BARU (lihat perintah rework — "tambahkan fitur baru yang
     * berguna khusus root" di section Root layar Tweak): nonaktifkan Doze /
     * App Standby sistem selama sesi bermain, supaya OS tidak membekukan
     * proses game atau service background-nya (mis. voice chat, download
     * assets) saat layar diredupkan sesaat atau perangkat dianggap idle.
     * `dumpsys deviceidle disable` butuh permission `DEVICE_POWER` yang di
     * mayoritas perangkat cuma diberikan ke shell UID root (Shizuku/adb
     * shell biasa akan ditolak), konsisten dengan tweak root lain di file
     * ini. Dikembalikan ke default sistem (`enable`) saat toggle dimatikan.
     */
    suspend fun applyDozeDisable(executor: ShellExecutor, enabled: Boolean): ShellResult {
        val mode = if (enabled) "disable" else "enable"
        return executor.exec("dumpsys deviceidle $mode")
    }

    /**
     * Khusus root: ganti I/O scheduler storage internal ke "kyber" (kalau
     * tersedia di kernel) yang dioptimalkan untuk latensi baca rendah —
     * cocok untuk baca aset game besar. Kalau kyber tidak didukung kernel,
     * dicoba fallback ke "bfq" yang juga lebih baik dari "noop"/"none"
     * untuk beban baca-tulis campuran. Dikembalikan ke governor default
     * kernel modern ("mq-deadline") saat dimatikan. Butuh akses tulis ke
     * /sys/block/(star)/queue/scheduler, hanya bisa lewat root.
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
        applyCpuGovernor(executor, CpuGovernor.UNIVERSAL),
        applyRamPriority(executor, enabled = false),
        applyThermalThrottleOverride(executor, enabled = false),
        applyGpuPerformanceMode(executor, enabled = false),
        applyIoSchedulerBoost(executor, enabled = false),
        applyVmHeapBoost(executor, enabled = false),
        applyDozeDisable(executor, enabled = false),
    )

    /**
     * Menerapkan seluruh tweak root sesuai satu [com.aether.x.data.GameProfile] —
     * dipakai oleh [com.aether.x.core.monitor.GameProfileMonitorService] saat
     * game yang punya profil tersimpan terdeteksi dibuka. Memakai fungsi
     * `apply*` yang SAMA dengan yang dipakai section "Root" global di layar
     * Tweak, hanya nilainya diambil dari profil per-game alih-alih dari
     * [com.aether.x.ui.tweak.TweakUiState] global.
     */
    suspend fun applyGameProfile(
        executor: ShellExecutor,
        profile: GameProfile,
    ): List<ShellResult> = listOf(
        applyCpuGovernor(
            executor,
            if (profile.cpuPerformanceMode) CpuGovernor.PERFORMANCE else CpuGovernor.UNIVERSAL,
        ),
        applyRamPriority(executor, profile.ramPriorityMode),
        applyThermalThrottleOverride(executor, profile.thermalThrottleOverride),
        applyGpuPerformanceMode(executor, profile.gpuPerformanceMode),
        applyIoSchedulerBoost(executor, profile.ioSchedulerBoost),
        applyVmHeapBoost(executor, profile.vmHeapBoost),
    )

    /**
     * Mengembalikan HANYA tweak root ("kernel-level": CPU/RAM/GPU/thermal/IO/
     * VM heap) ke kondisi OFF/default, TANPA menyentuh tweak lain (Input
     * Driver, refresh rate, game mode/DND) — dipakai saat game yang punya
     * Game Profile aktif ditutup dari recent apps, supaya tweak global lain
     * yang pengguna set manual di section non-root tidak ikut ter-reset.
     */
    suspend fun resetRootTweaksOnly(executor: ShellExecutor): List<ShellResult> = listOf(
        applyCpuGovernor(executor, CpuGovernor.UNIVERSAL),
        applyRamPriority(executor, enabled = false),
        applyThermalThrottleOverride(executor, enabled = false),
        applyGpuPerformanceMode(executor, enabled = false),
        applyIoSchedulerBoost(executor, enabled = false),
        applyVmHeapBoost(executor, enabled = false),
    )
}
