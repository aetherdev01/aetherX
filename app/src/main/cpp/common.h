#pragma once

#include <cstddef>
#include <cstdint>

namespace aetherx::common {
inline bool constantTimeEquals(const uint8_t* a, const uint8_t* b, size_t len) {
    uint8_t diff = 0;
    for (size_t i = 0; i < len; i++) {
        diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
}

inline void xorDecode(const uint8_t* encoded, const uint8_t* key, uint8_t* out, size_t len) {
    for (size_t i = 0; i < len; i++) {
        out[i] = encoded[i] ^ key[i];
    }
}
}
