#include <jni.h>

#include "native_symbols.h"
#include "rammonitor.h"

// rammonitor_jni.cpp — lapisan tipis konversi RamSnapshot -> jfloatArray,
// pola identik dengan sysmonitor_jni.cpp (lihat komentar di sana untuk
// alasan pemisahan native murni vs JNI).

using aetherx::rammonitor::RamSnapshot;
using aetherx::rammonitor::nrmRead;

extern "C" {

// Layout array hasil: [0]=totalKb [1]=availableKb [2]=swapTotalKb [3]=swapFreeKb.
// null kalau /proc/meminfo gagal dibuka sama sekali (lihat nrmRead).
JNIEXPORT jfloatArray JNICALL nrmc(JNIEnv* env, jobject /* thiz */) {
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

}  // extern "C"
