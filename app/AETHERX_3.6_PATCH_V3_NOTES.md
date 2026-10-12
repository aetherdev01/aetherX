# AetherX 3.6 — Follow-up source patch v3

## What changed

### Real backdrop blur
- Adds the Haze Compose backdrop-capture library (`dev.chrisbanes.haze:haze:1.6.1`).
- The bottom capsule captures app content through `hazeSource` and renders a backdrop `hazeEffect`; this is not `Modifier.blur()` on its own decorative gradient.
- `glassIntensity` now drives the Haze radius from `0.dp` through `30.dp` and a translucent contrast tint through `LocalHazeStyle`.
- Shared dialogs use `HazeDialog` and a shared `HazeState`. This includes the shared popup components, the crosshair style picker, the reboot confirmation, and app-level maintenance/update dialogs.
- Haze can use a tinted fallback when platform/device conditions prevent a fully accelerated blur. Blur rendering still needs visual/performance confirmation on actual devices.

### Advanced / kernel diagnostics
- Adds `KernelDiagnostics.readOnlyCommand` to inspect Android version/API, device model, ABI, SoC, kernel, root identity, and root-manager path hints.
- Probes readable CPU policy/governor interfaces, GPU/devfreq endpoints, thermal zones and ZRAM/swap. It does **not** write to sysfs or change kernel settings.
- Missing sysfs entries are reported as missing/unsupported/access-limited, rather than treated as proof that hardware is broken.
- Reboot, Recovery, and Bootloader actions still require confirmation and root; target ROMs may expose different supported boot modes.

## Validation performed in this workspace

Run from this module folder:

```bash
python validation/validate_patch.py
kotlinc src/main/java/com/aether/x/core/kernel/KernelDiagnostics.kt -d /tmp/kernel-diagnostics.jar
```

The local static validation currently reports **13/13 checks passed**, including XML parsing, expected blur wiring, read-only shell syntax, and dialog migration. The `KernelDiagnostics.kt` helper compiles by itself with `kotlinc`. The changed Compose files were checked for Kotlin parser/syntax diagnostics, but the full module was not compiled here.

## What is not claimed as tested

This archive is the Android **app module**, not the complete Gradle project. The supplied files do not include the root `settings.gradle(.kts)`, root build file/version catalog, or Gradle wrapper, and no Android SDK/device lab is available in this workspace. Therefore there is no verified APK build and no claim of tested runtime compatibility across Snapdragon, MediaTek, Exynos, Magisk, KernelSU, APatch, OEM ROMs, or battery-saver/GPU blur-disable conditions. Integrate this module into the complete project, build it, then run the diagnostics on representative devices before release.
