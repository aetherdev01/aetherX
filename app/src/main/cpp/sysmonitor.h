#pragma once

#include <stddef.h>
#include <stdint.h>

namespace aetherx::sysmonitor {
inline constexpr int kMaxCpuCores = 16;

struct CpuSnapshot {
    int coreCount = 0;
    float aggregateLoadPercent = -1.0f;
    float perCoreLoadPercent[kMaxCpuCores] = {};
};

struct GpuSnapshot {
    float loadPercent = -1.0f;
    float freqMhz = -1.0f;
};

bool nsmReadCpu(CpuSnapshot* out);

bool nsmReadGpu(GpuSnapshot* out);

void nsmResetCpuDelta();
}
