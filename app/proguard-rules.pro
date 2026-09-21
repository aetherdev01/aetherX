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

# Keep native method declarations so R8 does not remove/rename JNI entry points.
-keepclasseswithmembernames class * {
    native <methods>;
}
