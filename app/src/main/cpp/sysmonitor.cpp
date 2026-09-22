#include "sysmonitor.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

namespace aetherx::sysmonitor {
namespace {
struct CpuDeltaState {
    bool hasPrevious = false;
    long long prevTotal[kMaxCpuCores + 1] = {};
    long long prevIdle[kMaxCpuCores + 1] = {};
};

CpuDeltaState g_cpuState;

int g_debugCallCount = 0;
int g_debugFailCount = 0;

bool parseStatLine(const char* line, long long* outTotal, long long* outIdle) {
    if (line == nullptr || strncmp(line, "cpu", 3) != 0) return false;

    const char* cursor = line + 3;
    while (*cursor != '\0' && *cursor != ' ') cursor++;
    while (*cursor == ' ') cursor++;

    long long values[10] = {};
    int count = 0;
    while (count < 10 && *cursor != '\0' && *cursor != '\n') {
        char* endPtr = nullptr;
        long long v = strtoll(cursor, &endPtr, 10);
        if (endPtr == cursor) break;
        values[count++] = v;
        cursor = endPtr;
        while (*cursor == ' ') cursor++;
    }
    if (count < 4) return false;

    long long total = 0;
    for (int i = 0; i < count; i++) total += values[i];
    long long idle = values[3] + (count > 4 ? values[4] : 0);

    *outTotal = total;
    *outIdle = idle;
    return true;
}

float percentFromDelta(long long total, long long idle, long long prevTotal, long long prevIdle,
                        bool isAggregate) {
    long long totalDelta = total - prevTotal;
    long long idleDelta = idle - prevIdle;

    if (totalDelta <= 0) {
        if (isAggregate) {
            g_debugFailCount++;
        }
        return -1.0f;
    }

    float busy = static_cast<float>(totalDelta - idleDelta) / static_cast<float>(totalDelta) * 100.0f;
    if (busy < 0.0f) busy = 0.0f;
    if (busy > 100.0f) busy = 100.0f;
    return busy;
}

bool readSmallFile(const char* path, char* buffer, size_t bufferLen) {
    FILE* f = fopen(path, "r");
    if (f == nullptr) return false;
    size_t n = fread(buffer, 1, bufferLen - 1, f);
    fclose(f);
    if (n == 0) return false;
    buffer[n] = '\0';
    return true;
}

bool extractFirstNumber(const char* text, long long* out) {
    const char* cursor = text;
    while (*cursor != '\0' && (*cursor < '0' || *cursor > '9')) cursor++;
    if (*cursor == '\0') return false;
    char* endPtr = nullptr;
    *out = strtoll(cursor, &endPtr, 10);
    return endPtr != cursor;
}
}

bool nsmReadCpu(CpuSnapshot* out) {
    if (out == nullptr) return false;
    *out = CpuSnapshot{};

    FILE* f = fopen("/proc/stat", "r");
    if (f == nullptr) {
        return false;
    }

    char line[256];
    long long total = 0;
    long long idle = 0;

    bool aggregateOk = false;
    int parsedCores = 0;
    bool loggedFirstLine = false;

    while (fgets(line, sizeof(line), f) != nullptr) {
        if (strncmp(line, "cpu", 3) != 0) break;

        bool isAggregate = (line[3] == ' ');

        if (isAggregate && !loggedFirstLine) {
            char trimmed[256];
            strncpy(trimmed, line, sizeof(trimmed) - 1);
            trimmed[sizeof(trimmed) - 1] = '\0';
            size_t len = strlen(trimmed);
            if (len > 0 && trimmed[len - 1] == '\n') trimmed[len - 1] = '\0';

            loggedFirstLine = true;
        }

        if (!parseStatLine(line, &total, &idle)) {
            if (isAggregate) {
            }
            continue;
        }

        int slot = isAggregate ? 0 : (parsedCores + 1);
        if (slot > kMaxCpuCores) continue;

        float pct = -1.0f;
        if (g_cpuState.hasPrevious) {
            if (isAggregate) g_debugCallCount++;
            pct = percentFromDelta(total, idle, g_cpuState.prevTotal[slot], g_cpuState.prevIdle[slot], isAggregate);
        }
        g_cpuState.prevTotal[slot] = total;
        g_cpuState.prevIdle[slot] = idle;

        if (isAggregate) {
            out->aggregateLoadPercent = pct;
            aggregateOk = true;
        } else if (parsedCores < kMaxCpuCores) {
            out->perCoreLoadPercent[parsedCores] = pct;
            parsedCores++;
        }
    }
    fclose(f);

    out->coreCount = parsedCores;

    g_cpuState.hasPrevious = true;
    return aggregateOk;
}

void nsmResetCpuDelta() {
    g_cpuState = CpuDeltaState{};
    g_debugCallCount = 0;
    g_debugFailCount = 0;
}

bool nsmReadGpu(GpuSnapshot* out) {
    if (out == nullptr) return false;
    *out = GpuSnapshot{};

    static const char* kLoadPaths[] = {
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/devfreq/gpufreq/gpu_busy",
    };
    static const char* kFreqPaths[] = {
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/devfreq/gpufreq/cur_freq",
    };

    char buffer[64];
    bool anyOk = false;

    for (const char* path : kLoadPaths) {
        if (!readSmallFile(path, buffer, sizeof(buffer))) continue;
        long long value = 0;
        if (!extractFirstNumber(buffer, &value)) continue;
        if (value < 0 || value > 100) continue;
        out->loadPercent = static_cast<float>(value);
        anyOk = true;
        break;
    }

    for (const char* path : kFreqPaths) {
        if (!readSmallFile(path, buffer, sizeof(buffer))) continue;
        long long value = 0;
        if (!extractFirstNumber(buffer, &value)) continue;
        if (value <= 0) continue;

        float mhz = value > 100000 ? static_cast<float>(value) / 1000000.0f : static_cast<float>(value);
        out->freqMhz = mhz;
        anyOk = true;
        break;
    }

    return anyOk;
}
}
