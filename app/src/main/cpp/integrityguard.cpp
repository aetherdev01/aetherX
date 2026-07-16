#include <jni.h>
#include <cstdint>
#include <cstring>

#include "native_symbols.h"

namespace {

constexpr size_t kScanLen = 256;

uint64_t fnv1a64(const uint8_t* data, size_t len, uint64_t seed) {
    uint64_t hash = seed;
    for (size_t i = 0; i < len; i++) {
        hash ^= data[i];
        hash *= 0x100000001B3ULL;
    }
    return hash;
}

constexpr uint64_t kXorKeyChecksum = 0x9F3B7C1E5A2D8064ULL;
constexpr uint64_t kEncodedChecksum = 0x9F3B7C1E5A2D8064ULL;

constexpr bool kPlaceholderNotConfigured = (kXorKeyChecksum == kEncodedChecksum);

uint64_t decodeExpectedChecksum() {
    return kEncodedChecksum ^ kXorKeyChecksum;
}

uint64_t computeLiveChecksum() {
    const auto* verifyAddr = reinterpret_cast<const uint8_t*>(&nvfy);
    const auto* recheckAddr = reinterpret_cast<const uint8_t*>(&nvfy2);

    uint64_t hash = fnv1a64(verifyAddr, kScanLen, 0xCBF29CE484222325ULL);
    hash = fnv1a64(recheckAddr, kScanLen, hash);

    return hash;
}

}

extern "C" JNIEXPORT jint JNICALL
nvint(JNIEnv* /* env */, jobject /* thiz */) {
    if (kPlaceholderNotConfigured) {
        return 2;
    }
    const uint64_t live = computeLiveChecksum();
    const uint64_t expected = decodeExpectedChecksum();
    const bool match = (live == expected);
    return match ? 1 : 0;
}
