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
# SHIZUKU (rikka.shizuku) — wajib di-keep utuh, sebagian dipanggil lewat
# reflection (lihat ShizukuProcessCompat) dan lewat AIDL/Binder.
# =============================================================================
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

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
