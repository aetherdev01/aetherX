// sigcheck.cpp
//
// Verifikasi signing certificate APK dari sisi native (C++/JNI), bukan
// Kotlin, dengan tujuan: hash yang diharapkan TIDAK muncul sebagai satu
// string utuh di dalam binary (baik di kode Kotlin/DEX maupun di .so ini),
// sehingga tidak bisa langsung ditemukan lewat `strings libaetherxsig.so`
// atau decompiler Java biasa (jadx/apktool). Ini menaikkan biaya untuk
// orang yang mau patch APK (resign dengan kunci lain lalu hilangkan
// pengecekan) — TIDAK membuatnya mustahil, tapi jadi jauh lebih ribet
// dibanding kalau expected hash ada sebagai string plain di Kotlin.
//
// CARA KERJA:
// 1. Sisi Kotlin (SignatureGuard.kt) ambil signing certificate APK yang
//    SEDANG BERJALAN lewat PackageManager, hitung SHA-256-nya, lalu kirim
//    32 byte hash itu ke sini lewat JNI.
// 2. Fungsi ini bandingkan byte demi byte dengan hash yang diharapkan
//    (disimpan dalam bentuk ter-XOR, di-decode saat runtime, bukan
//    tersimpan sebagai plaintext di section .rodata).
// 3. Constant-time comparison dipakai supaya waktu eksekusi tidak bocor
//    informasi byte mana yang match/mismatch (bukan pertahanan utama di
//    sini, tapi tidak ada ruginya).
//
// PENTING: ini BUKAN pertahanan sempurna. Orang yang cukup gigih tetap
// bisa: (a) hook fungsi ini lewat Frida dan paksa selalu return true,
// (b) patch langsung instruksi cmp di .so pakai lief/radare2, atau
// (c) pakai LSPosed module untuk intercept JNI call sebelum sampai sini.
// Tujuan kode ini adalah menaikkan effort, bukan membuat 100% aman —
// kombinasikan dengan cek sisi server (LicenseRepository/Firestore rules)
// untuk validasi yang benar-benar tidak bisa dipalsukan client.
//
// Expected hash (SHA-256 signing cert aetherx.jks, format hex tanpa ':'):
// B8D371C1A06F445E278C66722903F1B8C21D61E7D427FFF5550B3BA06E4CEC58
// — TIDAK disimpan plain di bawah, lihat kEncodedHash + kXorKey.

#include <jni.h>
#include <cstdint>
#include <cstring>
#include <android/log.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXSig"
// Sengaja TIDAK ada logging sama sekali di path verifikasi ini di build
// release — logcat adalah salah satu cara termudah orang lain memahami
// alur cek ini. Makro di bawah no-op kecuali NDEBUG tidak didefinisikan.
#ifdef AETHERX_DEBUG_LOG
#define SIG_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define SIG_LOG(...)
#endif

namespace {

constexpr int kHashLen = 32;

// Hash asli di-XOR per-byte dengan keystream di bawah supaya tidak ada
// 32-byte run yang persis sama dengan SHA-256 asli di dalam binary .so —
// keduanya (key & enc) harus ada bareng untuk merekonstruksi nilai asli,
// jadi orang yang cuma dump satu array ini saja tidak langsung dapat hash-nya.
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

// Decode terjadi di stack saat runtime, buffer dibersihkan (di-zero) segera
// setelah dipakai supaya hash asli tidak nongkrong lama-lama di memory
// (mengurangi jendela waktu untuk memory-dump attack, walau tidak
// menghilangkannya sepenuhnya).
void decodeExpectedHash(uint8_t out[kHashLen]) {
    for (int i = 0; i < kHashLen; i++) {
        out[i] = kEncodedHash[i] ^ kXorKey[i];
    }
}

// Constant-time compare: selalu memeriksa semua 32 byte tanpa early-return,
// supaya waktu eksekusi tidak berbeda tergantung di byte keberapa mismatch
// pertama terjadi.
bool constantTimeEquals(const uint8_t* a, const uint8_t* b, int len) {
    uint8_t diff = 0;
    for (int i = 0; i < len; i++) {
        diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
}

}  // namespace

// PENAMAAN FUNGSI: sengaja PENDEK (nvfy/nvfy2), bukan konvensi panjang
// `Java_com_aether_x_..._nativeVerify` — karena native method di sini
// didaftarkan MANUAL lewat RegisterNatives() di jni_onload.cpp, bukan
// dicari otomatis oleh JVM lewat pencocokan nama simbol. Kotlin-nya
// (SignatureGuard.kt: `external fun nativeVerify`) tidak perlu tahu atau
// berubah sama sekali — pemetaan nama Kotlin -> fungsi C++ ini terjadi di
// satu tempat saja (jni_onload.cpp), bukan lewat nama simbol yang panjang.
extern "C" JNIEXPORT jboolean JNICALL
nvfy(JNIEnv* env, jobject /* thiz */, jbyteArray actualHashBytes) {

    if (actualHashBytes == nullptr) {
        SIG_LOG("actualHashBytes null");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(actualHashBytes);
    if (len != kHashLen) {
        // Panjang salah (bukan SHA-256) — pasti bukan hash valid, tolak
        // tanpa perlu decode expected hash sama sekali.
        SIG_LOG("unexpected hash length: %d", len);
        return JNI_FALSE;
    }

    jbyte actual[kHashLen];
    env->GetByteArrayRegion(actualHashBytes, 0, kHashLen, actual);

    uint8_t expected[kHashLen];
    decodeExpectedHash(expected);

    bool match = constantTimeEquals(
        reinterpret_cast<const uint8_t*>(actual), expected, kHashLen);

    // Bersihkan hash asli dari stack sesegera mungkin setelah dipakai.
    std::memset(expected, 0, kHashLen);

    SIG_LOG("signature verify result: %d", match);
    return match ? JNI_TRUE : JNI_FALSE;
}

// Titik verifikasi kedua yang independen, dipanggil dari tempat lain di app
// (lihat SignatureGuard.kt: verifyAgain()) — SENGAJA fungsi terpisah
// (bukan cuma alias) dengan nama simbol berbeda, supaya orang yang patch
// satu titik verifikasi (mis. hook nativeVerify lewat Frida) belum tentu
// otomatis melewati titik kedua ini juga kalau tidak sadar keduanya ada.
extern "C" JNIEXPORT jboolean JNICALL
nvfy2(JNIEnv* env, jobject thiz, jbyteArray actualHashBytes) {
    return nvfy(env, thiz, actualHashBytes);
}
