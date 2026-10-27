# AetherX - standard R8/ProGuard rules
# No signing-certificate, signature, integrity, or App Check rules.

# ─────────────────────────────────────────────────────────────────────────
# Obfuscation AKTIF: nama class/method/field diacak R8 jadi nama pendek
# (a, b, c, d, ...). Shrinking & optimasi tetap berjalan seperti biasa.
# -dontobfuscate yang lama DIHAPUS supaya reverse-engineering lebih sulit.
# -repackageclasses "" meratakan semua class hasil obfuscate ke satu
# package tanpa nama (bukan lagi com.aether.x.xxx), -allowaccessmodification
# & -overloadaggressively bikin R8 lebih agresif memendekkan & menggabungkan
# nama. -useuniqueclassmembernames bikin nama member konsisten lintas class.
-repackageclasses ""
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames
-flattenpackagehierarchy

# Keep JNI bridge classes themselves findable by FQCN from native code, but
# still let R8 obfuscate their method/field names — hanya nama CLASS yang
# dikunci (via -keepnames), bukan isinya, karena native code resolve class
# ini lewat FindClass(com/aether/x/...) pada classname, bukan method name.
-keepnames class com.aether.x.core.monitor.RootSystemMonitor
-keepnames class com.aether.x.core.security.DeviceFingerprint
-keepnames class com.aether.x.core.monitor.RamMonitor

# Keep native method declarations so R8 does not remove/rename JNI entry points
# (native method symbol tetap harus cocok dengan simbol di .so).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ─────────────────────────────────────────────────────────────────────────
# Ditambahkan saat minifyEnabled/shrinkResources dinyalakan (sebelumnya
# false, APK release ~60MB tanpa shrink). Rules di atas (JNI + dontobfuscate)
# TIDAK diubah — hanya menambah keep-rules untuk dependency yang pakai
# refleksi, supaya shrinking tidak mematahkan Firebase/libsu/Unity Ads.
# ─────────────────────────────────────────────────────────────────────────

# Firebase (Firestore, Messaging) & Play Services Ads membawa consumer
# proguard rules sendiri lewat AAR-nya, jadi TIDAK perlu `-keep` seluruh
# package (rule lama `-keep class com.google.firebase.** { *; }` dan
# `com.google.android.gms.** { *; }` mencegah R8 membuang kode tak terpakai
# dan menggendutkan APK). Firestore tetap aman karena model data yang
# dipetakan lewat refleksi ada di com.aether.x.data (dikunci di bawah).
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
