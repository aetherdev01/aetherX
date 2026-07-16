// ==================== Delta for cpp/jni_onload.cpp ====================
// ADDITIONS:
//   - #include <sys/ptrace.h>
//   - Anti-debug helper `IsBeingDebugged()` using ptrace(PTRACE_TRACEME)
//   - Call `IsBeingDebugged()` before registering natives; if true -> JNI_ERR.
//
// NOTE: The anti-debug check is executed early and only once, making it
//       lightweight yet effective against casual Frida/ptrace-based tooling.
//       It does NOT interfere with normal operation because legitimate apps
//       never have a tracer attached.
// =====================================================================

#include <jni.h>
#include <android/log.h>
#include <sys/ptrace.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXJNI"

namespace {

// --- Anti-debug -- Memanfaatkan ptrace(PTRACE_TRACEME) ---
// Sebuah proses hanya bisa memiliki SATU tracer.  Jika PTRACE_TRACEME
// gagal (return -1), artinya sudah ada tracer yang terpasang -> kemungkinan
// besar sedang di-debug-make.
bool IsBeingDebugged() {
    // Gunakan PT_TRACE_ME untuk portabilitas (sama dengan PTRACE_TRACEME)
    return ptrace(PTRACE_TRACEME, 0, 0, 0) == -1;
}

const JNINativeMethod kSignatureGuardMethods[] = {
    {"nativeVerify", "([B)Z", reinterpret_cast<void*>(nvfy)},
    {"nativeVerifyRecheck", "([B)Z", reinterpret_cast<void*>(nvfy2)},
};

const JNINativeMethod kIntegrityGuardMethods[] = {
    {"nativeVerifyIntegrity", "()I", reinterpret_cast<void*>(nvint)},
};

const JNINativeMethod kAdBlockDetectorMethods[] = {
    {"nativeDetectVpnInterface", "()Z", reinterpret_cast<void*>(nvpn)},
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

    // ----- ANTI-DEBUG GATE -----
    if (IsBeingDebugged()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "AXKM: Debugging detected refusing to load.");
        return JNI_ERR;  // library tidak akan dilanjutkan
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
            "AXKM (Security).");
    }

    return JNI_VERSION_1_6;
}