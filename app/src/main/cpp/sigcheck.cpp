#include <jni.h>
#include <cstdint>
#include <cstring>

#include "native_symbols.h"

namespace {

constexpr int kHashLen = 32;

constexpr uint8_t kXorKey[kHashLen] = {
    0x9F, 0xED, 0x89, 0xB6, 0x5E, 0x93, 0x96, 0xBC, 0x2B, 0xC7, 0xD4, 0x56,
    0x63, 0xA3, 0xCB, 0x5D, 0xEC, 0xFF, 0x4F, 0x65, 0xFF, 0xB3, 0x35, 0xC3,
    0xA9, 0xDE, 0xEA, 0x5E, 0x1C, 0x6D, 0xD3, 0xA3,
};

constexpr uint8_t kEncodedHash[kHashLen] = {
    0x27, 0x3E, 0xF8, 0x77, 0xFE, 0xFC, 0xD2, 0xE2, 0x0C, 0x4B, 0xB2, 0x24,
    0x4A, 0xA0, 0x3A, 0xE5, 0x2E, 0xE2, 0x2E, 0x82, 0x2B, 0x94, 0xCA, 0x36,
    0xFC, 0xD5, 0xD1, 0xFE, 0x72, 0x21, 0x3F, 0xFB,
};

void decodeExpectedHash(uint8_t out[kHashLen]) {
    for (int i = 0; i < kHashLen; i++) {
        out[i] = kEncodedHash[i] ^ kXorKey[i];
    }
}

bool constantTimeEquals(const uint8_t* a, const uint8_t* b, int len) {
    uint8_t diff = 0;
    for (int i = 0; i < len; i++) {
        diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
}

}

extern "C" JNIEXPORT jboolean JNICALL
nvfy(JNIEnv* env, jobject /* thiz */, jbyteArray actualHashBytes) {

    if (actualHashBytes == nullptr) {
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(actualHashBytes);
    if (len != kHashLen) {
        return JNI_FALSE;
    }

    jbyte actual[kHashLen];
    env->GetByteArrayRegion(actualHashBytes, 0, kHashLen, actual);

    uint8_t expected[kHashLen];
    decodeExpectedHash(expected);

    bool match = constantTimeEquals(
        reinterpret_cast<const uint8_t*>(actual), expected, kHashLen);

    std::memset(expected, 0, kHashLen);

    return match ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
nvfy2(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes) {
    return nvfy(env, thiz, actualHashBytes);
}
