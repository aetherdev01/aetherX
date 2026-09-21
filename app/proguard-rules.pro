# AetherX - standard R8/ProGuard rules
# No signing-certificate, signature, integrity, or App Check rules.

# Keep JNI bridge classes/methods that are called from native code.
-keep class com.aether.x.core.monitor.RootSystemMonitor { *; }
-keep class com.aether.x.core.security.DeviceFingerprint { *; }

# Keep native method declarations so R8 does not remove/rename JNI entry points.
-keepclasseswithmembernames class * {
    native <methods>;
}
