#include "aetherx_native.hpp"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

namespace aetherx::native {
namespace {

bool parseMemInfoLine(const char* line, const char* key, uint64_t* out_value) noexcept {
    if (line == nullptr || key == nullptr || out_value == nullptr) return false;
    const size_t keyLength = strlen(key);
    if (strncmp(line, key, keyLength) != 0) return false;

    const char* cursor = line + keyLength;
    while (*cursor == ' ' || *cursor == '\t') cursor++;
    char digits[32];
    size_t length = 0;
    while (*cursor >= '0' && *cursor <= '9' && length < sizeof(digits) - 1) {
        digits[length++] = *cursor++;
    }
    if (length == 0 || (*cursor != ' ' && *cursor != '\t' && *cursor != '\0' && *cursor != '\n')) {
        return false;
    }
    digits[length] = '\0';
    return aetherx_parse_u64(digits, out_value) == 1;
}

void readUptime(uint64_t* outputSeconds) noexcept {
    if (outputSeconds == nullptr) return;
    *outputSeconds = 0;
    FILE* file = fopen("/proc/uptime", "r");
    if (file == nullptr) return;

    double seconds = 0.0;
    const int scanned = fscanf(file, "%lf", &seconds);
    fclose(file);
    if (scanned == 1 && seconds >= 0.0) {
        *outputSeconds = static_cast<uint64_t>(seconds);
    }
}

}  // namespace

bool readMetricsSnapshot(MetricsSnapshot* out) noexcept {
    if (out == nullptr) return false;
    *out = MetricsSnapshot{};

    uint64_t memFreeKb = 0;
    uint64_t buffersKb = 0;
    uint64_t cachedKb = 0;
    bool foundTotal = false;
    bool foundAvailable = false;

    FILE* file = fopen("/proc/meminfo", "r");
    if (file != nullptr) {
        char line[256];
        while (fgets(line, sizeof(line), file) != nullptr) {
            uint64_t value = 0;
            if (parseMemInfoLine(line, "MemTotal:", &value)) {
                out->totalMemoryKb = value;
                foundTotal = true;
            } else if (parseMemInfoLine(line, "MemAvailable:", &value)) {
                out->availableMemoryKb = value;
                foundAvailable = true;
            } else if (parseMemInfoLine(line, "MemFree:", &value)) {
                memFreeKb = value;
            } else if (parseMemInfoLine(line, "Buffers:", &value)) {
                buffersKb = value;
            } else if (parseMemInfoLine(line, "Cached:", &value)) {
                cachedKb = value;
            }
        }
        fclose(file);
    }

    if (!foundAvailable) {
        out->availableMemoryKb = memFreeKb + buffersKb + cachedKb;
    }
    const long cpuCount = sysconf(_SC_NPROCESSORS_ONLN);
    out->onlineCpuCores = cpuCount > 0 && cpuCount <= 1024 ? static_cast<int32_t>(cpuCount) : 0;
    readUptime(&out->uptimeSeconds);

    return foundTotal && out->totalMemoryKb > 0;
}

int formatDiagnostics(char* output, size_t capacity) noexcept {
    if (output == nullptr || capacity == 0) return -1;

    MetricsSnapshot snapshot{};
    const bool metricsAvailable = readMetricsSnapshot(&snapshot);
    int written = 0;
    if (metricsAvailable) {
        written = snprintf(
            output,
            capacity,
            "AetherX v%s (%d); CPU cores=%d; RAM total=%llu KB; RAM available=%llu KB; uptime=%llu s",
            versionName(),
            static_cast<int>(versionCode()),
            static_cast<int>(snapshot.onlineCpuCores),
            static_cast<unsigned long long>(snapshot.totalMemoryKb),
            static_cast<unsigned long long>(snapshot.availableMemoryKb),
            static_cast<unsigned long long>(snapshot.uptimeSeconds));
    } else {
        written = snprintf(output, capacity, "AetherX v%s (%d); system metrics unavailable",
                           versionName(), static_cast<int>(versionCode()));
    }

    if (written < 0 || static_cast<size_t>(written) >= capacity) {
        output[capacity - 1] = '\0';
        return -1;
    }
    return written;
}

}  // namespace aetherx::native
