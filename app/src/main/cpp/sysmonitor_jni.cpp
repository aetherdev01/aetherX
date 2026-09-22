#include <jni.h>

#include "native_symbols.h"
#include "sysmonitor.h"

using aetherx::sysmonitor::CpuSnapshot;
using aetherx::sysmonitor::GpuSnapshot;
using aetherx::sysmonitor::kMaxCpuCores;
using aetherx::sysmonitor::nsmReadCpu;
using aetherx::sysmonitor::nsmReadGpu;
using aetherx::sysmonitor::nsmResetCpuDelta;

extern "C" {
jfloatArray JNICALL nsmc(JNIEnv* env, jobject) {
    CpuSnapshot snapshot;
    if (!nsmReadCpu(&snapshot)) {
        return nullptr;
    }

    jsize length = static_cast<jsize>(1 + snapshot.coreCount);
    jfloatArray result = env->NewFloatArray(length);
    if (result == nullptr) return nullptr;

    float buffer[kMaxCpuCores + 1];
    buffer[0] = snapshot.aggregateLoadPercent;
    for (int i = 0; i < snapshot.coreCount; i++) {
        buffer[i + 1] = snapshot.perCoreLoadPercent[i];
    }

    env->SetFloatArrayRegion(result, 0, length, buffer);
    return result;
}

jfloatArray JNICALL nsmg(JNIEnv* env, jobject) {
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

void JNICALL nsmr(JNIEnv*, jobject) {
    nsmResetCpuDelta();
}
}
