#include <jni.h>
#include <stdint.h>
#include <string.h>

#include "native_symbols.h"
#include "common.h"
#include "devicefingerprint.h"

namespace {
using aetherx::devicefingerprint::kDigestLen;
using aetherx::devicefingerprint::kMaxInputLen;

constexpr uint32_t kSha256InitialH[8] = {
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
};

constexpr uint32_t kSha256K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
    0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
    0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
};

inline uint32_t rotr(uint32_t x, int n) {
    return (x >> n) | (x << (32 - n));
}

void sha256(const uint8_t* data, size_t len, uint8_t outDigest[32]) {
    uint32_t h[8];
    memcpy(h, kSha256InitialH, sizeof(h));

    const uint64_t bitLen = static_cast<uint64_t>(len) * 8;

    size_t paddedLen = len + 1 + 8;
    paddedLen = ((paddedLen + 63) / 64) * 64;

    uint8_t buffer[kMaxInputLen + 72];
    memset(buffer, 0, sizeof(buffer));
    memcpy(buffer, data, len);
    buffer[len] = 0x80;
    for (int i = 0; i < 8; i++) {
        buffer[paddedLen - 1 - i] = static_cast<uint8_t>(bitLen >> (8 * i));
    }

    for (size_t chunkStart = 0; chunkStart < paddedLen; chunkStart += 64) {
        uint32_t w[64];
        for (int i = 0; i < 16; i++) {
            const uint8_t* p = buffer + chunkStart + i * 4;
            w[i] = (static_cast<uint32_t>(p[0]) << 24) |
                   (static_cast<uint32_t>(p[1]) << 16) |
                   (static_cast<uint32_t>(p[2]) << 8) |
                   static_cast<uint32_t>(p[3]);
        }
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
            uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }

        uint32_t a = h[0], b = h[1], c = h[2], d = h[3];
        uint32_t e = h[4], f = h[5], g = h[6], hh = h[7];

        for (int i = 0; i < 64; i++) {
            uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
            uint32_t ch = (e & f) ^ ((~e) & g);
            uint32_t temp1 = hh + s1 + ch + kSha256K[i] + w[i];
            uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
            uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t temp2 = s0 + maj;

            hh = g; g = f; f = e; e = d + temp1;
            d = c; c = b; b = a; a = temp1 + temp2;
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d;
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
    }

    for (int i = 0; i < 8; i++) {
        outDigest[i * 4 + 0] = static_cast<uint8_t>(h[i] >> 24);
        outDigest[i * 4 + 1] = static_cast<uint8_t>(h[i] >> 16);
        outDigest[i * 4 + 2] = static_cast<uint8_t>(h[i] >> 8);
        outDigest[i * 4 + 3] = static_cast<uint8_t>(h[i]);
    }
}

constexpr size_t kShaBlockLen = 64;

void hmacSha256(const uint8_t* key, size_t keyLen,
                 const uint8_t* msg, size_t msgLen,
                 uint8_t outDigest[kDigestLen]) {
    uint8_t keyBlock[kShaBlockLen];
    memset(keyBlock, 0, sizeof(keyBlock));

    if (keyLen > kShaBlockLen) {
        sha256(key, keyLen, keyBlock);
    } else {
        memcpy(keyBlock, key, keyLen);
    }

    uint8_t ipad[kShaBlockLen];
    uint8_t opad[kShaBlockLen];
    for (size_t i = 0; i < kShaBlockLen; i++) {
        ipad[i] = static_cast<uint8_t>(keyBlock[i] ^ 0x36);
        opad[i] = static_cast<uint8_t>(keyBlock[i] ^ 0x5c);
    }

    uint8_t innerBuf[kShaBlockLen + kMaxInputLen];
    const size_t innerMsgLen = (msgLen > kMaxInputLen) ? kMaxInputLen : msgLen;
    memcpy(innerBuf, ipad, kShaBlockLen);
    memcpy(innerBuf + kShaBlockLen, msg, innerMsgLen);
    uint8_t innerHash[kDigestLen];
    sha256(innerBuf, kShaBlockLen + innerMsgLen, innerHash);

    uint8_t outerBuf[kShaBlockLen + kDigestLen];
    memcpy(outerBuf, opad, kShaBlockLen);
    memcpy(outerBuf + kShaBlockLen, innerHash, kDigestLen);
    sha256(outerBuf, sizeof(outerBuf), outDigest);

    memset(keyBlock, 0, sizeof(keyBlock));
    memset(ipad, 0, sizeof(ipad));
    memset(opad, 0, sizeof(opad));
}

constexpr int kKeyLen = 32;

constexpr uint8_t kFingerprintXorKey[kKeyLen] = {
    0xAF, 0x69, 0x79, 0x70, 0xD0, 0x49, 0xEA, 0x26, 0xCD, 0xF8, 0xF0, 0x6C,
    0xAB, 0xE4, 0x5A, 0x8B, 0x6A, 0x3E, 0x09, 0x25, 0xB3, 0xB2, 0x39, 0xFE,
    0xC4, 0xCA, 0x18, 0x3E, 0xB8, 0x6F, 0x39, 0xBC,
};

constexpr uint8_t kFingerprintEncodedKey[kKeyLen] = {
    0x91, 0xD1, 0x2A, 0xBF, 0xF4, 0xC2, 0x3E, 0x0C, 0xBA, 0x16, 0x9E, 0x70,
    0x03, 0xBF, 0x35, 0x1A, 0x27, 0xB8, 0x36, 0x40, 0xDC, 0x29, 0x1A, 0xAC,
    0x0D, 0x31, 0x29, 0xCE, 0x14, 0x30, 0x3B, 0x3B,
};

constexpr bool arraysEqual(const uint8_t* a, const uint8_t* b, int n) {
    for (int i = 0; i < n; i++) {
        if (a[i] != b[i]) return false;
    }
    return true;
}

static_assert(!arraysEqual(kFingerprintXorKey, kFingerprintEncodedKey, kKeyLen),
              "kFingerprintXorKey and kFingerprintEncodedKey are still identical placeholders — "
              "generate the real HMAC key and XOR-encode it before building release.");
}

extern "C" jbyteArray JNICALL
nfgp(JNIEnv* env, jobject, jbyteArray rawInput) {
    if (rawInput == nullptr) return nullptr;

    const jsize len = env->GetArrayLength(rawInput);
    if (len <= 0) return nullptr;

    const size_t useLen =
        (static_cast<size_t>(len) > kMaxInputLen) ? kMaxInputLen : static_cast<size_t>(len);

    uint8_t inputBuf[kMaxInputLen];
    env->GetByteArrayRegion(rawInput, 0, static_cast<jsize>(useLen),
                             reinterpret_cast<jbyte*>(inputBuf));

    uint8_t key[kKeyLen];
    aetherx::common::xorDecode(kFingerprintEncodedKey, kFingerprintXorKey, key, kKeyLen);

    uint8_t digest[kDigestLen];
    hmacSha256(key, kKeyLen, inputBuf, useLen, digest);
    memset(key, 0, sizeof(key));

    jbyteArray result = env->NewByteArray(kDigestLen);
    if (result == nullptr) {
        memset(digest, 0, sizeof(digest));
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, kDigestLen, reinterpret_cast<jbyte*>(digest));
    memset(digest, 0, sizeof(digest));
    return result;
}
