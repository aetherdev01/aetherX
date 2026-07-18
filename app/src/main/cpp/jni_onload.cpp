#include <jni.h>
#include <android/log.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXJNI"

namespace {

const JNINativeMethod kSignatureGuardMethods[] = {
    {"nativeVerify", "([B)Z", reinterpret_cast<void*>(nvfy)},
    {"nativeVerifyRecheck", "([B)Z", reinterpret_cast<void*>(nvfy2)},
};

const JNINativeMethod kIntegrityGuardMethods[] = {
    {"nativeVerifyIntegrity", "()I", reinterpret_cast<void*>(nvint)},
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

    const bool integrityOk = registerClass(
        env, "com/aether/x/core/security/NativeIntegrityGuard",
        kIntegrityGuardMethods,
        sizeof(kIntegrityGuardMethods) / sizeof(kIntegrityGuardMethods[0]));

    if (!sigOk || !integrityOk) {
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

    return JNI_VERSION_1_6;
}
