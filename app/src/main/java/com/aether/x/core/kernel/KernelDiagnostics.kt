package com.aether.x.core.kernel

/** Read-only feature probe. This never changes CPU/GPU governors or sysfs values. */
object KernelDiagnostics {
    val readOnlyCommand: String = """
        echo '=== DEVICE ==='
        printf 'Manufacturer: '; getprop ro.product.manufacturer
        printf 'Model: '; getprop ro.product.model
        printf 'Android: '; getprop ro.build.version.release
        printf 'SDK: '; getprop ro.build.version.sdk
        printf 'ABI: '; getprop ro.product.cpu.abi
        printf 'SoC: '; getprop ro.soc.model
        printf 'Hardware: '; getprop ro.hardware
        printf 'Kernel: '; uname -r
        printf 'Root identity: '; id
        echo '=== ROOT MANAGER HINTS ==='
        if command -v magisk >/dev/null 2>&1; then echo 'Magisk CLI: available'; else echo 'Magisk CLI: not found'; fi
        if [ -d /data/adb/ksu ] || [ -d /data/adb/ksud ]; then echo 'KernelSU path: detected'; else echo 'KernelSU path: not detected'; fi
        if [ -d /data/adb/ap ] || [ -f /data/adb/apd ]; then echo 'APatch path: detected'; else echo 'APatch path: not detected'; fi
        echo '=== CPU GOVERNOR INTERFACES ==='
        found=0
        for p in /sys/devices/system/cpu/cpufreq/policy* /sys/devices/system/cpu/cpu*/cpufreq; do
          [ -d "${'$'}p" ] || continue
          [ -r "${'$'}p/scaling_available_governors" ] || continue
          found=1
          printf '%s available: ' "${'$'}p"; cat "${'$'}p/scaling_available_governors"
          if [ -r "${'$'}p/scaling_governor" ]; then printf '%s current: ' "${'$'}p"; cat "${'$'}p/scaling_governor"; fi
          if [ -r "${'$'}p/scaling_cur_freq" ]; then printf '%s current_freq_khz: ' "${'$'}p"; cat "${'$'}p/scaling_cur_freq"; fi
        done
        [ "${'$'}found" = 1 ] || echo 'No readable cpufreq governor interface found (not necessarily an error).'
        echo '=== GPU / DEVFREQ ==='
        found=0
        for p in /sys/class/devfreq/*; do
          [ -d "${'$'}p" ] || continue
          found=1
          printf '%s: ' "${'$'}{p##*/}"
          if [ -r "${'$'}p/cur_freq" ]; then printf 'current_freq='; cat "${'$'}p/cur_freq"; else printf 'current_freq=n/a '; fi
          if [ -r "${'$'}p/available_governors" ]; then printf 'governors='; cat "${'$'}p/available_governors"; else printf 'governors=n/a'; fi
        done
        [ "${'$'}found" = 1 ] || echo 'No devfreq devices exposed.'
        echo '=== THERMAL ==='
        found=0
        for p in /sys/class/thermal/thermal_zone*; do
          [ -d "${'$'}p" ] || continue
          found=1
          type='unknown'
          [ -r "${'$'}p/type" ] && type=${'$'}(cat "${'$'}p/type")
          printf '%s: %s' "${'$'}{p##*/}" "${'$'}type"
          if [ -r "${'$'}p/temp" ]; then printf ' temp='; cat "${'$'}p/temp"; else echo; fi
        done
        [ "${'$'}found" = 1 ] || echo 'No thermal zones exposed.'
        echo '=== ZRAM / SWAP ==='
        if [ -r /proc/swaps ]; then cat /proc/swaps; else echo '/proc/swaps unavailable'; fi
        for p in /sys/block/zram*/disksize; do [ -r "${'$'}p" ] && { printf '%s: ' "${'$'}p"; cat "${'$'}p"; }; done
        echo '=== INTERPRETATION ==='
        echo 'All checks above are read-only. Missing nodes mean unsupported, hidden, or access-limited; do not infer hardware failure.'
    """.trimIndent()
}
