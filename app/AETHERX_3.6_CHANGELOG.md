# AetherX 3.6 — Settings & Advanced source update

## Implemented in this source patch
- Default appearance remains Light (`DarkModePref.LIGHT`), with Light/Dark/System choices exposed in Settings.
- Added persisted font family choices: Poppins/default, Serif, and Monospace. Typography choice is applied globally through `AetherXTheme`.
- Added persisted card styles: Glass, Soft, and Outline. `SectionCard` uses the selected radius, surface treatment, border and elevation.
- Added persisted glass intensity slider. It controls card glass tint/border strength, decorative glow blur, and navbar pill intensity.
- Strengthened the capsule press animation spring while retaining the existing low-recomposition drawing approach.
- Added Advanced/System controls: reboot, recovery reboot, bootloader reboot, kernel/ABI/root information, and read-only kernel diagnostics for CPU governors, swap/ZRAM, thermal-zone types and mount summary.
- Reboot commands require explicit confirmation and run through the existing libsu root shell; they fail with an error if root access is unavailable.

## Validation and limitations
- XML resources parse successfully and edited Kotlin files have balanced delimiters.
- This is a source patch, not a signed APK.
- Full Android compilation was not possible from the supplied archive because the Gradle wrapper, `settings.gradle(.kts)`, and `gradle/libs.versions.toml` version catalog are absent.
- Glass intensity currently controls translucent surfaces and blurred decorative highlights. It does not implement a true real-time backdrop blur behind every dialog/window; that requires a separate window-level blur implementation and device testing.


## Follow-up patch v3 — backdrop blur and compatibility diagnostics
- Added Haze 1.6.1 source capture so the bottom capsule blurs the actual app content beneath it rather than blurring a decorative gradient.
- The glass-intensity slider now controls the real navbar backdrop blur radius, with a tinted translucent surface for contrast.
- Central `PopupDialog` and `PopupDialogPanel` now use `HazeDialog` and share the MainScreen backdrop state; the crosshair style picker and reboot confirmation use the shared blurred dialog path too.
- Replaced the narrow CPU0-only probe with a read-only compatibility diagnostic that checks CPU policy interfaces, devfreq/GPU interfaces, thermal zones, ZRAM/swap, Android/ABI/kernel, and root-manager indicators without writing sysfs.
- Static validation is possible here, but an actual Gradle build and device/chipset/root-manager test matrix still need the full root Gradle project and physical/emulated test devices. No unsupported capability is reported as universally available.
