#include <jni.h>
#include <cstring>
#include <strings.h>

#include "native_symbols.h"

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

namespace {

// BUG FIX (lihat perintah rework — "cuma dns.adguard yang kedeteksi,
// provider lain enggak"): daftar SEBELUMNYA cuma benar-benar lengkap
// untuk AdGuard; keempat varian Mullvad selain "adblock" (base/extended/
// family/all) TIDAK PERNAH bisa cocok lewat IPv6-nya karena hanya 2 dari
// 5 alamat IPv6 Mullvad yang didaftarkan ("::3"/adblock dan salah satu
// tebakan keliru), dan provider populer lain (Mullvad "family" varian
// baru, ControlD) tidak ada sama sekali. Semua IP di bawah diverifikasi
// ulang terhadap dokumentasi resmi masing-masing provider:
// - AdGuard DNS: https://adguard-dns.io/en/public-dns.html
// - Mullvad DoT/DoH: https://mullvad.net/en/help/dns-over-https-and-dns-over-tls
// - Control D Free DNS ("Ads & Tracking" — profil p2): https://controld.com/free-dns
//
// CATATAN SENGAJA: Cloudflare (1.1.1.1/1.1.1.2/1.1.1.3), Quad9 (9.9.9.9),
// dan NextDNS TIDAK dimasukkan — default resolver mereka memblokir
// malware/keamanan, BUKAN iklan (blocking iklan NextDNS baru aktif kalau
// pengguna secara eksplisit mengonfigurasi blocklist iklan sendiri di
// dashboard mereka, tidak bisa ditebak dari IP/hostname resolvernya saja).
// Memasukkan mereka akan jadi FALSE POSITIVE (dialog tampil ke pengguna
// yang sebenarnya tidak memblokir iklan sama sekali).
constexpr const char* kAdBlockDnsIps[] = {
    // AdGuard DNS — "Default" (blokir iklan+tracker) & "Family protection"
    // (blokir iklan+tracker+konten dewasa). "Non-filtering" (94.140.14.140/
    // .141) SENGAJA tidak dimasukkan — itu varian yang TIDAK memblokir apa
    // pun.
    "94.140.14.14", "94.140.15.15",
    "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
    "94.140.14.15", "94.140.15.16",
    "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff",

    // Mullvad Encrypted DNS — SEMUA varian yang benar-benar memblokir
    // iklan (adblock/base/extended/family/all). "Vanilla" (194.242.2.2,
    // 2a07:e340::2) SENGAJA tidak dimasukkan — itu varian tanpa blocking
    // sama sekali, hanya DNS terenkripsi biasa.
    "194.242.2.3", "2a07:e340::3",   // adblock.dns.mullvad.net
    "194.242.2.4", "2a07:e340::4",   // base.dns.mullvad.net
    "194.242.2.5", "2a07:e340::5",   // extended.dns.mullvad.net
    "194.242.2.6", "2a07:e340::6",   // family.dns.mullvad.net
    "194.242.2.9", "2a07:e340::9",   // all.dns.mullvad.net

    // Control D — Free DNS profil "p2" (Ads & Tracking), satu-satunya
    // profil gratis mereka yang secara default memblokir iklan.
    "76.76.2.2", "76.76.10.2",
};
constexpr size_t kAdBlockDnsIpCount = sizeof(kAdBlockDnsIps) / sizeof(kAdBlockDnsIps[0]);

bool isKnownAdBlockDnsIp(const char* ip) {
    for (size_t i = 0; i < kAdBlockDnsIpCount; i++) {
        if (strcmp(ip, kAdBlockDnsIps[i]) == 0) return true;
    }
    return false;
}

constexpr const char* kAdBlockDnsHostnames[] = {
    // AdGuard DNS — hostname DoT/Private DNS "Hostname" mode. Bentuk
    // "-dns.com" ditambahkan sebagai alias karena beberapa versi Android/
    // OEM melaporkan privateDnsServerName dalam bentuk reverse-DNS resmi
    // (mis. "dns.adguard-dns.com"), BUKAN hostname yang diketik pengguna
    // ("dns.adguard.com") — keduanya valid tergantung device.
    "dns.adguard.com",
    "dns-family.adguard.com",
    "dns.adguard-dns.com",
    "family.adguard-dns.com",

    // Mullvad Encrypted DNS — SEMUA varian yang memblokir iklan. "Vanilla"
    // (dns.mullvad.net, tanpa blocking) SENGAJA tidak dimasukkan.
    "adblock.dns.mullvad.net",
    "base.dns.mullvad.net",
    "extended.dns.mullvad.net",
    "family.dns.mullvad.net",
    "all.dns.mullvad.net",

    // Control D — Free DNS profil "p2" (Ads & Tracking).
    "p2.freedns.controld.com",
};
constexpr size_t kAdBlockDnsHostnameCount =
    sizeof(kAdBlockDnsHostnames) / sizeof(kAdBlockDnsHostnames[0]);

bool isKnownAdBlockDnsHostname(const char* hostname) {
    for (size_t i = 0; i < kAdBlockDnsHostnameCount; i++) {
        if (strcasecmp(hostname, kAdBlockDnsHostnames[i]) == 0) return true;
    }
    return false;
}

constexpr const char* kAdBlockModuleKeywords[] = {
    "adaway",
    "systemless-hosts",
    "systemlesshosts",
    "adblock",
    "ad-block",
    "ad_block",
    "youtube_adaway",
    "noads",
    "no-ads",
    "no_ads",
};
constexpr size_t kAdBlockModuleKeywordCount =
    sizeof(kAdBlockModuleKeywords) / sizeof(kAdBlockModuleKeywords[0]);

bool containsKnownAdBlockModuleKeyword(const char* listing) {
    for (size_t i = 0; i < kAdBlockModuleKeywordCount; i++) {
        if (strcasestr(listing, kAdBlockModuleKeywords[i]) != nullptr) {
            return true;
        }
    }
    return false;
}

}

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
