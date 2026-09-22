#pragma once

#include <cstdint>

namespace aetherx::rammonitor {
struct RamSnapshot {
    float totalKb = -1.0f;
    float availableKb = -1.0f;

    float swapTotalKb = -1.0f;

    float swapFreeKb = -1.0f;
};

bool nrmRead(RamSnapshot* out);
}
