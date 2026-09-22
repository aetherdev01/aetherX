# AetherX - standard R8/ProGuard rules
# No signing-certificate, signature, integrity, or App Check rules.

# Nama class/method/field TIDAK diacak R8 (tetap com.aether.x.xxx, bukan a/a0/a1).
# Shrinking (penghapusan kode tak terpakai) & optimasi tetap berjalan seperti biasa.
# Proteksi terhadap reverse-engineering jadi tanggung jawab di luar R8 (mis. custom
# native protection), karena baris ini justru MENGHILANGKAN satu lapis proteksi bawaan.
-dontobfuscate

# Keep JNI bridge classes/methods that are called from native code.
-keep class com.aether.x.core.monitor.RootSystemMonitor { *; }
-keep class com.aether.x.core.security.DeviceFingerprint { *; }
-keep class com.aether.x.core.monitor.RamMonitor { *; }

# Keep native method declarations so R8 does not remove/rename JNI entry points.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─────────────────────────────────────────────────────────────────────────
# Ditambahkan saat minifyEnabled/shrinkResources dinyalakan (sebelumnya
# false, APK release ~60MB tanpa shrink). Rules di atas (JNI + dontobfuscate)
# TIDAK diubah — hanya menambah keep-rules untuk dependency yang pakai
# refleksi, supaya shrinking tidak mematahkan Firebase/libsu/Unity Ads.
# ─────────────────────────────────────────────────────────────────────────

# Firebase (Analytics, Firestore, Auth, Functions, Messaging) — Firestore
# deserialize dokumen ke data class lewat refleksi.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Model/data class yang dipetakan ke/dari Firestore butuh no-arg constructor
# & nama field tetap ada. Sesuaikan path package ini kalau model Firestore-mu
# tidak di com.aether.x.data.
-keepclassmembers class com.aether.x.data.** {
    public <init>();
    <fields>;
}

# libsu (root backend)
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Unity Ads
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.ads.**
-dontwarn com.unity3d.services.**

# Haze (blur) — beberapa versi awal punya internal API yang diakses reflektif
# oleh modul haze-materials.
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# DataStore Preferences
-keep class androidx.datastore.*.** { *; }

# Kotlin metadata umum (data class copy()/componentN(), enum valueOf/values()
# yang dipanggil dinamis di tempat lain)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlin.Metadata { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
