#include <jni.h>

#include "native_symbols.h"
#include "rammonitor.h"

using aetherx::rammonitor::RamSnapshot;
using aetherx::rammonitor::nrmRead;

extern "C" {
jfloatArray JNICALL nrmc(JNIEnv* env, jobject) {
    RamSnapshot snapshot;
    if (!nrmRead(&snapshot)) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) return nullptr;

    float buffer[4] = {
        snapshot.totalKb,
        snapshot.availableKb,
        snapshot.swapTotalKb,
        snapshot.swapFreeKb,
    };
    env->SetFloatArrayRegion(result, 0, 4, buffer);
    return result;
}
}
