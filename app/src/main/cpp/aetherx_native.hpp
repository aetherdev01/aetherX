#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
namespace aetherx::native {

// Keep these values in sync with app/build.gradle.kts.
inline constexpr char kVersionName[] = "3.10.1";
inline constexpr int32_t kVersionCode = 31000001;

struct MetricsSnapshot {
    uint64_t totalMemoryKb;
    uint64_t availableMemoryKb;
    uint64_t uptimeSeconds;
    int32_t onlineCpuCores;
};

const char* versionName() noexcept;
int32_t versionCode() noexcept;
bool readMetricsSnapshot(MetricsSnapshot* out) noexcept;
// Returns the number of bytes written excluding the terminator, or -1 on error/truncation.
int formatDiagnostics(char* output, size_t capacity) noexcept;

}  // namespace aetherx::native

extern "C" {
#endif

// Strict decimal parser shared by the C and C++ implementations. Returns 1 on success.
int aetherx_parse_u64(const char* text, uint64_t* out_value);

#ifdef __cplusplus
}  // extern "C"
#endif
