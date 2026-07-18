#include <jni.h>
#include <cstring>
#include <strings.h>
#include <cstdint>
#include <cstddef>

#include "native_symbols.h"
#include "common.h"

// BUG FIX (lihat perintah rework — "VPN detection tidak berfungsi kecuali
// DNS AdGuard"): deteksi VPN lewat getifaddrs()+pencocokan nama interface
// ("tun"/"ppp") SUDAH DIHAPUS dari sini. Sejak Android 11, proses app
// biasa (tanpa privilese NETLINK) tidak lagi diizinkan enumerasi
// interface jaringan mentah lewat API itu — panggilannya nyaris selalu
// gagal/kosong di device modern walau VPN benar-benar aktif. Deteksi VPN
// sekarang SEPENUHNYA di Kotlin lewat ConnectivityManager/
// NetworkCapabilities.hasTransport(TRANSPORT_VPN) — lihat
// AdBlockDetector.kt. Fungsi native `nvpn` juga sudah tidak didaftarkan
// di jni_onload.cpp.
//
// ==========================================================================
// STRING OBFUSCATION (fix "hasil .so nya banyak string bocor"):
//
// Sebelumnya, seluruh IP/hostname DNS AdBlock dan keyword modul Magisk di
// bawah ini tersimpan sebagai string literal PLAIN di source, yang berarti
// muncul mentah-mentah dan mudah dibaca lewat `strings libaetherX.so` oleh
// siapa pun yang membongkar APK — termasuk daftar lengkap provider DNS
// yang dianggap "adblock" (memudahkan orang membuat adblocker custom yang
// sengaja menghindari deteksi ini) dan keyword modul (memudahkan penamaan
// ulang modul Magisk supaya lolos dari nmod()).
//
// Sekarang SEMUA string signature (kAdBlockDnsIps, kAdBlockDnsHostnames,
// kAdBlockModuleKeywords) disimpan ter-XOR-encode terhadap kAdBlockXorKey
// (32 byte, konsisten dengan pola kFingerprintXorKey di
// devicefingerprint.cpp dan kEncodedHash di sigcheck.cpp) dan HANYA
// di-decode ke buffer stack sesaat sebelum dipakai untuk satu perbandingan,
// lalu langsung di-wipe (`std::memset`) — plaintext-nya tidak pernah
// tersimpan sebagai konstanta di section .rodata binary, dan tidak lama
// nongkrong di memory saat runtime.
//
// CATATAN: ini menaikkan biaya reverse-engineering (harus dump+XOR key dan
// decode manual, bukan cuma `strings` sekali jalan), TAPI bukan proteksi
// absolut — siapa pun yang menjalankan library ini di debugger/emulator
// tetap bisa membaca hasil decode di memory saat fungsi berjalan. Ini
// levelnya "menyulitkan casual reverse-engineering", bukan DRM tingkat
// tinggi.
//
// PENTING (WAJIB DIISI SEBELUM BUILD RELEASE): kAdBlockXorKey di bawah
// SUDAH diisi nilai acak (bukan placeholder kosong/berulang) saat file ini
// dibuat. Kalau nanti perlu regenerate seluruh daftar (menambah/menghapus
// entri), pakai script generator terpisah (simpan di luar repo/tidak
// commit) yang men-XOR tiap string terhadap key BARU, lalu ganti
// kAdBlockXorKey dan seluruh isi array sekaligus — jangan pernah menambah
// entri baru dengan key LAMA yang sudah pernah dipakai di build yang
// sudah dirilis (mengurangi kesulitan analisis dibanding key yang selalu
// berbeda tiap kali daftar berubah).
// ==========================================================================

namespace {

using aetherx::common::xorDecode;

// String ter-encode: bytes hasil XOR + panjang aslinya. kMaxEncodedLen
// dipilih dari entri terpanjang di ketiga daftar di bawah (saat ini 24
// byte, "extended.dns.mullvad.net") + sedikit headroom untuk entri baru.
constexpr size_t kMaxEncodedLen = 32;

struct EncodedStr {
    uint8_t bytes[kMaxEncodedLen];
    size_t len;
};

constexpr uint8_t kAdBlockXorKey[32] = {0x3c, 0xc4, 0xf0, 0x13, 0x0e, 0x62, 0xed, 0xc1, 0x0f, 0x03, 0xe1, 0x45, 0x41, 0xba, 0x48, 0x8f, 0x02, 0xa3, 0x5f, 0xe0, 0xd8, 0xae, 0x85, 0xe0, 0x7d, 0x39, 0xec, 0xdd, 0x38, 0x0a, 0xca, 0xe7};

// IP address DNS AdGuard/Mullvad/ControlD yang memblokir iklan (ter-XOR).
// Lihat riwayat komentar rework sebelumnya untuk sumber/verifikasi tiap
// alamat (dokumentasi resmi masing-masing provider) — daftar LOGIKAnya
// tidak berubah sama sekali dari versi plain, hanya representasinya.
constexpr EncodedStr kAdBlockDnsIpsEncoded[] = {
    {{0x05, 0xf0, 0xde, 0x22, 0x3a, 0x52, 0xc3, 0xf0, 0x3b, 0x2d, 0xd0, 0x71}, 12},  // 94.140.14.14
    {{0x05, 0xf0, 0xde, 0x22, 0x3a, 0x52, 0xc3, 0xf0, 0x3a, 0x2d, 0xd0, 0x70}, 12},  // 94.140.15.15
    {{0x0e, 0xa5, 0xc1, 0x23, 0x34, 0x57, 0xdd, 0xa2, 0x3f, 0x39, 0xdb, 0x24, 0x25, 0x8b, 0x72, 0xe9, 0x64}, 17},  // 2a10:50c0::ad1:ff
    {{0x0e, 0xa5, 0xc1, 0x23, 0x34, 0x57, 0xdd, 0xa2, 0x3f, 0x39, 0xdb, 0x24, 0x25, 0x88, 0x72, 0xe9, 0x64}, 17},  // 2a10:50c0::ad2:ff
    {{0x05, 0xf0, 0xde, 0x22, 0x3a, 0x52, 0xc3, 0xf0, 0x3b, 0x2d, 0xd0, 0x70}, 12},  // 94.140.14.15
    {{0x05, 0xf0, 0xde, 0x22, 0x3a, 0x52, 0xc3, 0xf0, 0x3a, 0x2d, 0xd0, 0x73}, 12},  // 94.140.15.16
    {{0x0e, 0xa5, 0xc1, 0x23, 0x34, 0x57, 0xdd, 0xa2, 0x3f, 0x39, 0xdb, 0x27, 0x20, 0xde, 0x79, 0xb5, 0x64, 0xc5}, 18},  // 2a10:50c0::bad1:ff
    {{0x0e, 0xa5, 0xc1, 0x23, 0x34, 0x57, 0xdd, 0xa2, 0x3f, 0x39, 0xdb, 0x27, 0x20, 0xde, 0x7a, 0xb5, 0x64, 0xc5}, 18},  // 2a10:50c0::bad2:ff
    {{0x0d, 0xfd, 0xc4, 0x3d, 0x3c, 0x56, 0xdf, 0xef, 0x3d, 0x2d, 0xd2}, 11},  // 194.242.2.3
    {{0x0e, 0xa5, 0xc0, 0x24, 0x34, 0x07, 0xde, 0xf5, 0x3f, 0x39, 0xdb, 0x76}, 12},  // 2a07:e340::3
    {{0x0d, 0xfd, 0xc4, 0x3d, 0x3c, 0x56, 0xdf, 0xef, 0x3d, 0x2d, 0xd5}, 11},  // 194.242.2.4
    {{0x0e, 0xa5, 0xc0, 0x24, 0x34, 0x07, 0xde, 0xf5, 0x3f, 0x39, 0xdb, 0x71}, 12},  // 2a07:e340::4
    {{0x0d, 0xfd, 0xc4, 0x3d, 0x3c, 0x56, 0xdf, 0xef, 0x3d, 0x2d, 0xd4}, 11},  // 194.242.2.5
    {{0x0e, 0xa5, 0xc0, 0x24, 0x34, 0x07, 0xde, 0xf5, 0x3f, 0x39, 0xdb, 0x70}, 12},  // 2a07:e340::5
    {{0x0d, 0xfd, 0xc4, 0x3d, 0x3c, 0x56, 0xdf, 0xef, 0x3d, 0x2d, 0xd7}, 11},  // 194.242.2.6
    {{0x0e, 0xa5, 0xc0, 0x24, 0x34, 0x07, 0xde, 0xf5, 0x3f, 0x39, 0xdb, 0x73}, 12},  // 2a07:e340::6
    {{0x0d, 0xfd, 0xc4, 0x3d, 0x3c, 0x56, 0xdf, 0xef, 0x3d, 0x2d, 0xd8}, 11},  // 194.242.2.9
    {{0x0e, 0xa5, 0xc0, 0x24, 0x34, 0x07, 0xde, 0xf5, 0x3f, 0x39, 0xdb, 0x7c}, 12},  // 2a07:e340::9
    {{0x0b, 0xf2, 0xde, 0x24, 0x38, 0x4c, 0xdf, 0xef, 0x3d}, 9},  // 76.76.2.2
    {{0x0b, 0xf2, 0xde, 0x24, 0x38, 0x4c, 0xdc, 0xf1, 0x21, 0x31}, 10},  // 76.76.10.2
};
constexpr size_t kAdBlockDnsIpsCount = sizeof(kAdBlockDnsIpsEncoded) / sizeof(kAdBlockDnsIpsEncoded[0]);

// Hostname Private DNS yang memblokir iklan (ter-XOR).
constexpr EncodedStr kAdBlockDnsHostnamesEncoded[] = {
    {{0x58, 0xaa, 0x83, 0x3d, 0x6f, 0x06, 0x8a, 0xb4, 0x6e, 0x71, 0x85, 0x6b, 0x22, 0xd5, 0x25}, 15},  // dns.adguard.com
    {{0x58, 0xaa, 0x83, 0x3e, 0x68, 0x03, 0x80, 0xa8, 0x63, 0x7a, 0xcf, 0x24, 0x25, 0xdd, 0x3d, 0xee, 0x70, 0xc7, 0x71, 0x83, 0xb7, 0xc3}, 22},  // dns-family.adguard.com
    {{0x58, 0xaa, 0x83, 0x3d, 0x6f, 0x06, 0x8a, 0xb4, 0x6e, 0x71, 0x85, 0x68, 0x25, 0xd4, 0x3b, 0xa1, 0x61, 0xcc, 0x32}, 19},  // dns.adguard-dns.com
    {{0x5a, 0xa5, 0x9d, 0x7a, 0x62, 0x1b, 0xc3, 0xa0, 0x6b, 0x64, 0x94, 0x24, 0x33, 0xde, 0x65, 0xeb, 0x6c, 0xd0, 0x71, 0x83, 0xb7, 0xc3}, 22},  // family.adguard-dns.com
    {{0x5d, 0xa0, 0x92, 0x7f, 0x61, 0x01, 0x86, 0xef, 0x6b, 0x6d, 0x92, 0x6b, 0x2c, 0xcf, 0x24, 0xe3, 0x74, 0xc2, 0x3b, 0xce, 0xb6, 0xcb, 0xf1}, 23},  // adblock.dns.mullvad.net
    {{0x5e, 0xa5, 0x83, 0x76, 0x20, 0x06, 0x83, 0xb2, 0x21, 0x6e, 0x94, 0x29, 0x2d, 0xcc, 0x29, 0xeb, 0x2c, 0xcd, 0x3a, 0x94}, 20},  // base.dns.mullvad.net
    {{0x59, 0xbc, 0x84, 0x76, 0x60, 0x06, 0x88, 0xa5, 0x21, 0x67, 0x8f, 0x36, 0x6f, 0xd7, 0x3d, 0xe3, 0x6e, 0xd5, 0x3e, 0x84, 0xf6, 0xc0, 0xe0, 0x94}, 24},  // extended.dns.mullvad.net
    {{0x5a, 0xa5, 0x9d, 0x7a, 0x62, 0x1b, 0xc3, 0xa5, 0x61, 0x70, 0xcf, 0x28, 0x34, 0xd6, 0x24, 0xf9, 0x63, 0xc7, 0x71, 0x8e, 0xbd, 0xda}, 22},  // family.dns.mullvad.net
    {{0x5d, 0xa8, 0x9c, 0x3d, 0x6a, 0x0c, 0x9e, 0xef, 0x62, 0x76, 0x8d, 0x29, 0x37, 0xdb, 0x2c, 0xa1, 0x6c, 0xc6, 0x2b}, 19},  // all.dns.mullvad.net
    {{0x4c, 0xf6, 0xde, 0x75, 0x7c, 0x07, 0x88, 0xa5, 0x61, 0x70, 0xcf, 0x26, 0x2e, 0xd4, 0x3c, 0xfd, 0x6d, 0xcf, 0x3b, 0xce, 0xbb, 0xc1, 0xe8}, 23},  // p2.freedns.controld.com
};
constexpr size_t kAdBlockDnsHostnamesCount = sizeof(kAdBlockDnsHostnamesEncoded) / sizeof(kAdBlockDnsHostnamesEncoded[0]);

// Keyword nama modul Magisk/KernelSU AdBlock (ter-XOR).
constexpr EncodedStr kAdBlockModuleKeywordsEncoded[] = {
    {{0x5d, 0xa0, 0x91, 0x64, 0x6f, 0x1b}, 6},  // adaway
    {{0x4f, 0xbd, 0x83, 0x67, 0x6b, 0x0f, 0x81, 0xa4, 0x7c, 0x70, 0xcc, 0x2d, 0x2e, 0xc9, 0x3c, 0xfc}, 16},  // systemless-hosts
    {{0x4f, 0xbd, 0x83, 0x67, 0x6b, 0x0f, 0x81, 0xa4, 0x7c, 0x70, 0x89, 0x2a, 0x32, 0xce, 0x3b}, 15},  // systemlesshosts
    {{0x5d, 0xa0, 0x92, 0x7f, 0x61, 0x01, 0x86}, 7},  // adblock
    {{0x5d, 0xa0, 0xdd, 0x71, 0x62, 0x0d, 0x8e, 0xaa}, 8},  // ad-block
    {{0x5d, 0xa0, 0xaf, 0x71, 0x62, 0x0d, 0x8e, 0xaa}, 8},  // ad_block
    {{0x45, 0xab, 0x85, 0x67, 0x7b, 0x00, 0x88, 0x9e, 0x6e, 0x67, 0x80, 0x32, 0x20, 0xc3}, 14},  // youtube_adaway
    {{0x52, 0xab, 0x91, 0x77, 0x7d}, 5},  // noads
    {{0x52, 0xab, 0xdd, 0x72, 0x6a, 0x11}, 6},  // no-ads
    {{0x52, 0xab, 0xaf, 0x72, 0x6a, 0x11}, 6},  // no_ads
};
constexpr size_t kAdBlockModuleKeywordsCount = sizeof(kAdBlockModuleKeywordsEncoded) / sizeof(kAdBlockModuleKeywordsEncoded[0]);

// Decode satu EncodedStr ke buffer stack null-terminated. Buffer pemanggil
// harus berukuran minimal kMaxEncodedLen + 1. Mengembalikan panjang hasil
// decode (tanpa null terminator).
size_t decodeToBuffer(const EncodedStr& enc, char* outBuf) {
    xorDecode(enc.bytes, kAdBlockXorKey, reinterpret_cast<uint8_t*>(outBuf), enc.len);
    outBuf[enc.len] = '\0';
    return enc.len;
}

bool isKnownAdBlockDnsIp(const char* ip) {
    char decoded[kMaxEncodedLen + 1];
    bool found = false;
    for (size_t i = 0; i < kAdBlockDnsIpsCount && !found; i++) {
        decodeToBuffer(kAdBlockDnsIpsEncoded[i], decoded);
        if (strcmp(ip, decoded) == 0) found = true;
    }
    std::memset(decoded, 0, sizeof(decoded));
    return found;
}

bool isKnownAdBlockDnsHostname(const char* hostname) {
    char decoded[kMaxEncodedLen + 1];
    bool found = false;
    for (size_t i = 0; i < kAdBlockDnsHostnamesCount && !found; i++) {
        decodeToBuffer(kAdBlockDnsHostnamesEncoded[i], decoded);
        if (strcasecmp(hostname, decoded) == 0) found = true;
    }
    std::memset(decoded, 0, sizeof(decoded));
    return found;
}

bool containsKnownAdBlockModuleKeyword(const char* listing) {
    char decoded[kMaxEncodedLen + 1];
    bool found = false;
    for (size_t i = 0; i < kAdBlockModuleKeywordsCount && !found; i++) {
        decodeToBuffer(kAdBlockModuleKeywordsEncoded[i], decoded);
        if (strcasestr(listing, decoded) != nullptr) found = true;
    }
    std::memset(decoded, 0, sizeof(decoded));
    return found;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
ndns(JNIEnv* env, jobject /* thiz */, jobjectArray dnsServers) {
    if (dnsServers == nullptr) return JNI_FALSE;

    const jsize count = env->GetArrayLength(dnsServers);
    for (jsize i = 0; i < count; i++) {
        auto element = static_cast<jstring>(env->GetObjectArrayElement(dnsServers, i));
        if (element == nullptr) continue;

        const char* ip = env->GetStringUTFChars(element, nullptr);
        const bool match = (ip != nullptr) &&
            (isKnownAdBlockDnsIp(ip) || isKnownAdBlockDnsHostname(ip));
        if (ip != nullptr) env->ReleaseStringUTFChars(element, ip);
        env->DeleteLocalRef(element);

        if (match) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
nmod(JNIEnv* env, jobject /* thiz */, jstring moduleListing) {
    if (moduleListing == nullptr) return JNI_FALSE;

    const char* listing = env->GetStringUTFChars(moduleListing, nullptr);
    if (listing == nullptr) return JNI_FALSE;

    const bool match = containsKnownAdBlockModuleKeyword(listing);
    env->ReleaseStringUTFChars(moduleListing, listing);
    return match ? JNI_TRUE : JNI_FALSE;
}
