#include <jni.h>
#include "native_symbols.h"

namespace {
const JNINativeMethod kDeviceFingerprintMethods[] = {
    {"nativeDeriveFingerprint", "([B)[B", reinterpret_cast<void*>(nfgp)},
};

const JNINativeMethod kSysMonitorMethods[] = {
    {"nativeReadCpuSnapshot", "()[F", reinterpret_cast<void*>(nsmc)},
    {"nativeReadGpuSnapshot", "()[F", reinterpret_cast<void*>(nsmg)},
    {"nativeResetCpuDelta", "()V", reinterpret_cast<void*>(nsmr)},
};

const JNINativeMethod kRamMonitorMethods[] = {
    {"nativeReadRamSnapshot", "()[F", reinterpret_cast<void*>(nrmc)},
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

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    const bool fingerprintOk = registerClass(
        env, "com/aether/x/core/security/DeviceFingerprint",
        kDeviceFingerprintMethods,
        sizeof(kDeviceFingerprintMethods) / sizeof(kDeviceFingerprintMethods[0]));
    if (!fingerprintOk) {
    }

    const bool sysMonitorOk = registerClass(
        env, "com/aether/x/core/monitor/RootSystemMonitor",
        kSysMonitorMethods,
        sizeof(kSysMonitorMethods) / sizeof(kSysMonitorMethods[0]));
    if (!sysMonitorOk) {
    }

    const bool ramMonitorOk = registerClass(
        env, "com/aether/x/core/monitor/RamMonitor",
        kRamMonitorMethods,
        sizeof(kRamMonitorMethods) / sizeof(kRamMonitorMethods[0]));
    if (!ramMonitorOk) {
    }

    return JNI_VERSION_1_6;
}
