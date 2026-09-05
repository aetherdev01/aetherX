# ── Native JNI bridge classes ────────────────────────────────────────────────
# WAJIB: jni_onload.cpp mendaftarkan native method ke class-class ini lewat
# env->FindClass("com/aether/x/...") dengan STRING LITERAL (bukan reflection
# Kotlin biasa), dan RootSystemMonitor.kt/SignatureGuard.kt/dst mendeklarasikan
# method `external fun` yang dipanggil balik dari native lewat RegisterNatives.
#
# R8/ProGuard TIDAK TAHU soal pemanggilan ini (tidak ada call graph yang
# terlihat dari sisi Kotlin/Java biasa) — kalau class atau method member-nya
# di-rename/dihapus saat minify, FindClass() di native akan gagal (silent,
# hanya log warning untuk kelas non-fatal seperti RootSystemMonitor) ATAU
# RegisterNatives gagal cocokkan signature (untuk kelas fatal seperti
# SignatureGuard/NativeIntegrityGuard, bikin JNI_OnLoad return JNI_ERR dan
# app crash total saat startup).
#
# -keep class ... { *; } menjaga nama class DAN seluruh method (termasuk yang
# `external fun`) tetap seperti sumber aslinya.
-keep class com.aether.x.core.monitor.RootSystemMonitor { *; }
-keep class com.aether.x.core.security.SignatureGuard { *; }
-keep class com.aether.x.core.security.NativeIntegrityGuard { *; }
-keep class com.aether.x.core.security.DeviceFingerprint { *; }

# Jaga semua native method declaration di project (lapisan pengaman kedua,
# menutup kemungkinan ada class JNI lain yang lolos dari daftar eksplisit di
# atas kalau nanti ditambah tapi lupa di-keep juga di sini).
-keepclasseswithmembernames class * {
    native <methods>;
}
