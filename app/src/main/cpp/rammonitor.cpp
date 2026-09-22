#include "rammonitor.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

namespace aetherx::rammonitor {
namespace {
bool matchLabelAndParseKb(const char* line, const char* label, float* outValueKb) {
    size_t labelLen = strlen(label);
    if (strncmp(line, label, labelLen) != 0) return false;
    if (line[labelLen] != ':') return false;

    const char* cursor = line + labelLen + 1;
    while (*cursor == ' ' || *cursor == '\t') cursor++;

    char* endPtr = nullptr;
    long long value = strtoll(cursor, &endPtr, 10);
    if (endPtr == cursor) return false;

    *outValueKb = static_cast<float>(value);
    return true;
}
}

bool nrmRead(RamSnapshot* out) {
    if (out == nullptr) return false;
    *out = RamSnapshot{};

    FILE* f = fopen("/proc/meminfo", "r");
    if (f == nullptr) {
        return false;
    }

    int foundCount = 0;
    const int kWantedCount = 4;

    char line[256];
    while (foundCount < kWantedCount && fgets(line, sizeof(line), f) != nullptr) {
        if (matchLabelAndParseKb(line, "MemTotal", &out->totalKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "MemAvailable", &out->availableKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "SwapTotal", &out->swapTotalKb)) {
            foundCount++;
        } else if (matchLabelAndParseKb(line, "SwapFree", &out->swapFreeKb)) {
            foundCount++;
        }
    }
    fclose(f);

    if (out->totalKb < 0.0f) {
        return false;
    }
    return true;
}
}
