# ═══════════════════════════════════════════════════════════════════════════════
# AETHERX — ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════════════════════

# ── R8 Optimisasi ─────────────────────────────────────────────────────────────
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-optimizations !code/simplification/cast,field/*,class/merging/*,code/allocation/variable

# ── Attributes wajib ──────────────────────────────────────────────────────────
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =============================================================================
# AETHERX — CORE
# =============================================================================
-keep public class com.aether.x.MainActivity { <init>(); }
-keep public class com.aether.x.AetherXApp { <init>(); }

# ── Native signature guard (JNI) ──────────────────────────────────────────────
# Nama class/method HARUS persis sama dengan yang dicari native code lewat
# JNI (Java_com_aether_x_core_security_SignatureGuard_...) — kalau R8
# me-rename/obfuscate class atau method ini, native library (libaetherX.so)
# tidak akan menemukan method-nya lagi saat runtime (UnsatisfiedLinkError).
-keep class com.aether.x.core.security.SignatureGuard {
    private native boolean nativeVerify(byte[]);
    private native boolean nativeVerifyRecheck(byte[]);
}

# ── Native integrity guard (JNI) ──────────────────────────────────────────────
# Sama alasannya seperti SignatureGuard di atas — method native ini juga ada
# di libaetherX.so (lihat integrityguard.cpp), jadi harus tetap persis sama
# namanya supaya JNI binding tidak putus setelah di-obfuscate.
-keep class com.aether.x.core.security.NativeIntegrityGuard {
    private native int nativeVerifyIntegrity();
}

# ── Native adblock detector (JNI) ──────────────────────────────────────────────
# BUG FIX KRITIS (force-close APK langsung dibuka): rule ini SEBELUMNYA
# TIDAK ADA — AdBlockDetector.kt tidak direferensikan dari kode Kotlin/Java
# mana pun (belum di-wire ke UI apa pun), jadi R8 menganggapnya "tidak
# dipakai" dan menghapusnya dari APK, padahal native code (jni_onload.cpp)
# tetap mencoba mendaftarkan method native ke class ini lewat FindClass()
# di SETIAP startup app (berbagi satu JNI_OnLoad yang sama dengan
# SignatureGuard/NativeIntegrityGuard di atas) — FindClass() yang gagal
# menyisakan exception pending yang meledak balik ke pemanggilan
# System.loadLibrary() PALING PERTAMA di app (SignatureGuard.kt, dipanggil
# di AetherXApp.onCreate() sebelum UI apa pun) dan membuat app force-close
# seketika dibuka. Rule ini mencegah R8 men-strip class ini SAMA SEKALI
# (perbaikan agar fitur benar-benar berfungsi) — lihat juga fix defensif
# env->ExceptionClear() di jni_onload.cpp (perbaikan agar TIDAK PERNAH
# crash lagi walau suatu saat ada class serupa yang lolos tanpa rule ini).
-keep class com.aether.x.core.ads.AdBlockDetector {
    private native boolean nativeDetectVpnInterface();
    private native boolean nativeMatchAdBlockDns(java.lang.String[]);
    private native boolean nativeMatchAdBlockModule(java.lang.String);
}

# =============================================================================
# KOTLIN / COROUTINES
# =============================================================================
-keep class kotlin.Metadata { *; }
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
    public static void parameter*(...);
}

# =============================================================================
# JETPACK COMPOSE / NAVIGATION
# =============================================================================
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(...);
    void sourceInformationMarkerStart(...);
    void sourceInformationMarkerEnd(...);
    boolean isTraceInProgress();
    void traceEventStart(...);
    void traceEventEnd();
}
-keepclassmembers class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# =============================================================================
# DATASTORE
# =============================================================================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# =============================================================================
# ADB TERTANAM ("libadb-android") — REWORK TOTAL (lihat perintah rework —
# "hapus semua yang bersangkutan dengan shizuku"): rule rikka.shizuku/
# moe.shizuku DIHAPUS (library-nya sudah tidak lagi jadi dependency).
# Library ini + sun-security-android (generate X509Certificate) +
# Conscrypt (provider TLS 1.3 untuk wireless pairing) berkomunikasi lewat
# protokol biner ADB/TLS (parsing manual byte-level, refleksi provider
# security) yang rawan salah kalau class-nya di-obfuscate/di-shrink R8.
# =============================================================================
-keep class io.github.muntashirakon.adb.** { *; }
-keep interface io.github.muntashirakon.adb.** { *; }
-keepclassmembers class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**

-keep class android.sun.security.** { *; }
-keepclassmembers class android.sun.security.** { *; }
-dontwarn android.sun.security.**

-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# =============================================================================
# LIBSU (com.topjohnwu.superuser) — eksekusi shell root
# =============================================================================
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# =============================================================================
# PARCELABLE / ENUM
# =============================================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final ** name();
    public final int ordinal();
}

# =============================================================================
# SUPPRESS WARNINGS
# =============================================================================
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**
