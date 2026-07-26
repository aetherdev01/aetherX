#include <jni.h>

#include "native_symbols.h"
#include "sysmonitor.h"

// sysmonitor_jni.cpp — lapisan tipis yang mengonversi struct native
// (CpuSnapshot/GpuSnapshot, lihat sysmonitor.h) menjadi jfloatArray yang
// dikonsumsi RootSystemMonitor.kt. Dipisah dari sysmonitor.cpp supaya
// sysmonitor.cpp/.h tetap murni C++ tanpa dependensi JNI (lebih mudah
// diuji/dipakai ulang kalau suatu saat perlu, walau saat ini hanya
// dipanggil dari sini).

using aetherx::sysmonitor::CpuSnapshot;
using aetherx::sysmonitor::GpuSnapshot;
using aetherx::sysmonitor::kMaxCpuCores;
using aetherx::sysmonitor::nsmReadCpu;
using aetherx::sysmonitor::nsmReadGpu;
using aetherx::sysmonitor::nsmResetCpuDelta;

extern "C" {

JNIEXPORT jfloatArray JNICALL nsmc(JNIEnv* env, jobject /* thiz */) {
    CpuSnapshot snapshot;
    if (!nsmReadCpu(&snapshot)) {
        return nullptr;
    }

    // Elemen 0 = agregat, elemen 1..coreCount = per-core.
    jsize length = static_cast<jsize>(1 + snapshot.coreCount);
    jfloatArray result = env->NewFloatArray(length);
    if (result == nullptr) return nullptr;

    // Buffer sementara di stack — kMaxCpuCores kecil (16), aman tanpa heap alloc.
    float buffer[kMaxCpuCores + 1];
    buffer[0] = snapshot.aggregateLoadPercent;
    for (int i = 0; i < snapshot.coreCount; i++) {
        buffer[i + 1] = snapshot.perCoreLoadPercent[i];
    }

    env->SetFloatArrayRegion(result, 0, length, buffer);
    return result;
}

JNIEXPORT jfloatArray JNICALL nsmg(JNIEnv* env, jobject /* thiz */) {
    GpuSnapshot snapshot;
    if (!nsmReadGpu(&snapshot)) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(2);
    if (result == nullptr) return nullptr;

    float buffer[2] = {snapshot.loadPercent, snapshot.freqMhz};
    env->SetFloatArrayRegion(result, 0, 2, buffer);
    return result;
}

JNIEXPORT void JNICALL nsmr(JNIEnv* /* env */, jobject /* thiz */) {
    nsmResetCpuDelta();
}

}  // extern "C"
