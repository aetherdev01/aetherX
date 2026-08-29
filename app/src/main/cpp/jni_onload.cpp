#include <jni.h>
#include <android/log.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXJNI"

namespace {

const JNINativeMethod kSignatureGuardMethods[] = {
    {"nativeVerify", "([B)Z", reinterpret_cast<void*>(nvfy)},
    {"nativeVerifyRecheck", "([B)Z", reinterpret_cast<void*>(nvfy2)},
};

// nvpn (deteksi VPN) SUDAH TIDAK dipakai/didaftarkan di sini — deteksi VPN
// pindah sepenuhnya ke ConnectivityManager/NetworkCapabilities di Kotlin
// (lihat AdBlockDetector.kt) karena getifaddrs()/NETLINK tidak lagi bisa
// diandalkan dari proses app biasa sejak Android 11. Fungsi nvpn di
// adblockguard.cpp dibiarkan ada (tidak dihapus) sebagai referensi/tidak
// mengganggu, tapi TIDAK didaftarkan lewat RegisterNatives lagi.
const JNINativeMethod kAdBlockDetectorMethods[] = {
    {"nativeMatchAdBlockDns", "([Ljava/lang/String;)Z", reinterpret_cast<void*>(ndns)},
    {"nativeMatchAdBlockModule", "(Ljava/lang/String;)Z", reinterpret_cast<void*>(nmod)},
};

// Device fingerprint (lihat devicefingerprint.h/.cpp) — dipakai
// DeviceFingerprint.kt untuk menurunkan deviceId yang dikunci lisensi,
// menggantikan ANDROID_ID mentah.
const JNINativeMethod kDeviceFingerprintMethods[] = {
    {"nativeDeriveFingerprint", "([B)[B", reinterpret_cast<void*>(nfgp)},
};

// Monitor CPU/GPU real-time root-only (lihat sysmonitor.h/.cpp,
// sysmonitor_jni.cpp) — dipakai RootSystemMonitor.kt. Kegagalan registrasi
// di sini TIDAK fatal (return JNI_ERR) seperti sigOk/integrityOk, karena
// fitur ini murni tambahan UI (monitor grafik), bukan pemblokir keamanan
// app — kalau gagal, RootSystemMonitor.kt cukup melaporkan monitor tidak
// tersedia.
const JNINativeMethod kSysMonitorMethods[] = {
    {"nativeReadCpuSnapshot", "()[F", reinterpret_cast<void*>(nsmc)},
    {"nativeReadGpuSnapshot", "()[F", reinterpret_cast<void*>(nsmg)},
    {"nativeResetCpuDelta", "()V", reinterpret_cast<void*>(nsmr)},
};

bool registerClass(JNIEnv* env, const char* classBinaryName,
                    const JNINativeMethod* methods, int methodCount) {
    jclass clazz = env->FindClass(classBinaryName);
    if (clazz == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    bool ok = env->RegisterNatives(clazz, methods, methodCount) == JNI_OK;
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(clazz);
    return ok;
}

}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    const bool sigOk = registerClass(
        env, "com/aether/x/core/security/SignatureGuard",
        kSignatureGuardMethods,
        sizeof(kSignatureGuardMethods) / sizeof(kSignatureGuardMethods[0]));

    if (!sigOk) {
        return JNI_ERR;
    }

    const bool adBlockOk = registerClass(
        env, "com/aether/x/core/ads/AdBlockDetector",
        kAdBlockDetectorMethods,
        sizeof(kAdBlockDetectorMethods) / sizeof(kAdBlockDetectorMethods[0]));
    if (!adBlockOk) {
        __android_log_print(
            ANDROID_LOG_WARN, LOG_TAG,
            "");
    }

    const bool fingerprintOk = registerClass(
        env, "com/aether/x/core/security/DeviceFingerprint",
        kDeviceFingerprintMethods,
        sizeof(kDeviceFingerprintMethods) / sizeof(kDeviceFingerprintMethods[0]));
    if (!fingerprintOk) {
        __android_log_print(
            ANDROID_LOG_WARN, LOG_TAG,
            "DeviceFingerprint native registration failed");
    }

    const bool sysMonitorOk = registerClass(
        env, "com/aether/x/core/monitor/RootSystemMonitor",
        kSysMonitorMethods,
        sizeof(kSysMonitorMethods) / sizeof(kSysMonitorMethods[0]));
    if (!sysMonitorOk) {
        __android_log_print(
            ANDROID_LOG_WARN, LOG_TAG,
            "RootSystemMonitor native registration failed");
    }

    return JNI_VERSION_1_6;
}
