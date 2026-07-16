// adblockguard.cpp
//
// Deteksi (BUKAN pemblokiran/pemaksaan) tiga sinyal umum yang menunjukkan
// pengguna kemungkinan memakai adblock di level sistem/jaringan:
//   1. Interface VPN aktif (dipakai banyak app adblock berbasis VPN lokal,
//      mis. Blokada/AdGuard for Android/DNS66/PersonalDNSFilter).
//   2. DNS custom yang cocok dengan penyedia DNS pemblokir iklan yang
//      dikenal publik (mis. AdGuard DNS, Mullvad DNS varian adblock).
//   3. Modul Magisk yang dikenal terkait adblock (mis. AdAway
//      systemless-hosts) — hanya bisa dicek kalau app sudah mendapat akses
//      root (lihat AdBlockDetector.kt sisi Kotlin untuk kenapa).
//
// =====================================================================
// LINGKUP FILE INI — DIBACA DULU SEBELUM MENGUBAH APA PUN DI SINI:
// Tiga fungsi di bawah HANYA mengembalikan SINYAL boolean ke sisi Kotlin.
// TIDAK ADA satu baris pun di sini yang mencoba melewati/menonaktifkan
// adblock, memaksa permintaan iklan tetap terkirim, atau menyembunyikan
// hasil deteksi ini dari pengguna. Keputusan soal APA yang dilakukan
// dengan sinyal ini (mis. menampilkan pesan jujur yang mengajak pengguna
// menonaktifkan adblock atau upgrade ke Membership) sepenuhnya berada di
// sisi Kotlin/UI, di luar file ini — dan MEMANG SENGAJA dibuat begitu:
// diam-diam mengalahkan proteksi yang sengaja dipasang pengguna sendiri
// (tanpa pengguna tahu) adalah dark pattern yang tidak akan pernah
// diimplementasikan di app ini, baik di sini maupun di tempat lain.
// =====================================================================
//
// CATATAN JUJUR SOAL BATASAN tiap sinyal (baca sebelum menganggap hasil
// fungsi-fungsi ini sebagai kepastian mutlak):
// - VPN (nvpn): SINYAL PALING LEMAH dari ketiganya. Banyak pengguna
//   memakai VPN untuk alasan yang SAMA SEKALI TIDAK ADA hubungannya
//   dengan adblock (VPN korporat, VPN privasi seperti Mullvad/ProtonVPN/
//   NordVPN tanpa fitur adblock diaktifkan, dst.) — mendeteksi "ada
//   interface tun aktif" TIDAK SAMA dengan "pengguna ini memblokir
//   iklan". Perlakukan sinyal ini sebagai indikasi paling lemah, bukan
//   bukti tunggal.
// - DNS (ndns): lebih presisi dari VPN (IP yang cocok memang secara
//   eksplisit dipasarkan penyedianya sebagai layanan pemblokir iklan),
//   TAPI daftar di bawah SENGAJA TIDAK LENGKAP — provider dengan IP
//   per-akun/rotating (NextDNS, ControlD) tidak bisa dicocokkan lewat
//   daftar IP statis sama sekali, jadi TIDAK diikutkan (bukan lupa).
//   dns0.eu SENGAJA TIDAK diikutkan karena layanannya sudah dihentikan
//   (discontinued Oktober 2025, diverifikasi lewat pencarian web sebelum
//   file ini ditulis) — mengikutkan IP layanan yang sudah mati hanya
//   menambah kode tanpa nilai deteksi nyata.
// - Modul Magisk (nmod): heuristik pencocokan KATA KUNCI (bukan daftar ID
//   modul yang persis/lengkap) — modul baru bisa saja punya nama yang
//   tidak cocok kata kunci mana pun (false negative), dan secara teori
//   modul TIDAK terkait adblock tapi kebetulan namanya mengandung salah
//   satu kata kunci bisa salah terdeteksi (false positive, kecil
//   kemungkinannya mengingat kata kunci yang dipilih cukup spesifik).

#include <jni.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <cstring>
#include <strings.h>  // strcasestr (tersedia di Bionic/NDK)
#include <android/log.h>

#include "native_symbols.h"

#define LOG_TAG "AetherXAdBlock"
#ifdef AETHERX_DEBUG_LOG
#define GUARD_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define GUARD_LOG(...)
#endif

namespace {

// ---------------------------------------------------------------------
// 1) Deteksi interface VPN — murni baca daftar interface jaringan lewat
//    getifaddrs() (syscall biasa, TIDAK butuh permission Android apa pun
//    di luar yang app ini sudah punya, TIDAK butuh root/Shizuku).
// ---------------------------------------------------------------------

// Prefix nama interface yang UMUM dipakai VPN di Android (baik VPN
// bawaan sistem/VpnService maupun VPN native seperti WireGuard/OpenVPN).
// "tun" mencakup HAMPIR SEMUA app VPN Android modern (termasuk semua app
// adblock berbasis VPN lokal seperti Blokada/AdGuard/DNS66 — mereka semua
// memakai android.net.VpnService yang selalu memunculkan interface
// bernama tunN). "ppp" disertakan untuk kelengkapan historis (PPP-based
// VPN lama), jarang dipakai lagi di Android modern.
constexpr const char* kVpnInterfacePrefixes[] = {"tun", "ppp"};
constexpr size_t kVpnInterfacePrefixCount =
    sizeof(kVpnInterfacePrefixes) / sizeof(kVpnInterfacePrefixes[0]);

bool isVpnLikeInterfaceName(const char* name) {
    for (size_t i = 0; i < kVpnInterfacePrefixCount; i++) {
        const size_t prefixLen = strlen(kVpnInterfacePrefixes[i]);
        if (strncmp(name, kVpnInterfacePrefixes[i], prefixLen) == 0) {
            return true;
        }
    }
    return false;
}

bool detectVpnInterface() {
    ifaddrs* addrs = nullptr;
    if (getifaddrs(&addrs) != 0) {
        // Gagal membaca daftar interface (jarang terjadi) — anggap TIDAK
        // ada VPN terdeteksi (fail-safe: sinyal produk, bukan boundary
        // keamanan, jadi kegagalan cek ini TIDAK PERNAH boleh dianggap
        // "adblock terdeteksi" secara keliru).
        GUARD_LOG("getifaddrs gagal, anggap tidak ada VPN terdeteksi");
        return false;
    }

    bool found = false;
    for (ifaddrs* it = addrs; it != nullptr; it = it->ifa_next) {
        if (it->ifa_name == nullptr) continue;
        // Hanya hitung interface yang sedang UP — interface tun yang
        // terdaftar tapi mati/tidak aktif tidak relevan (mis. sisa dari
        // VPN yang baru saja diputus tapi belum dibersihkan sepenuhnya
        // oleh sistem).
        if (it->ifa_flags != 0 && !(it->ifa_flags & IFF_UP)) continue;
        if (isVpnLikeInterfaceName(it->ifa_name)) {
            GUARD_LOG("interface mirip VPN ditemukan: %s", it->ifa_name);
            found = true;
            break;
        }
    }

    freeifaddrs(addrs);
    return found;
}

// ---------------------------------------------------------------------
// 2) Pencocokan DNS terhadap penyedia DNS pemblokir iklan yang dikenal.
//    Daftar IP di bawah SEMUANYA diverifikasi ulang dari sumber resmi
//    masing-masing penyedia sebelum file ini ditulis — bukan dari
//    ingatan/tebakan (lihat catatan lingkup file di atas soal dns0.eu
//    yang sengaja dikeluarkan karena sudah discontinued).
// ---------------------------------------------------------------------

constexpr const char* kAdBlockDnsIps[] = {
    // --- AdGuard DNS — "Default" (blokir iklan+tracker) ---
    "94.140.14.14", "94.140.15.15",
    "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
    // --- AdGuard DNS — "Family protection" (+ konten dewasa) ---
    "94.140.14.15", "94.140.15.16",
    "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff",
    // SENGAJA TIDAK diikutkan: varian "Non-filtering" AdGuard
    // (94.140.14.140/94.140.14.141) — varian itu EKSPLISIT TIDAK
    // memblokir iklan sama sekali, mengikutkannya justru salah.

    // --- Mullvad DNS — varian yang mengikutkan pemblokiran iklan
    //     (adblock/base/extended/family/all — SEMUANYA mengikutkan
    //     ad-blocking sebagai bagian dari daftar filter masing-masing) ---
    "194.242.2.3",  // adblock.dns.mullvad.net — iklan+tracker saja
    "194.242.2.4",  // base.dns.mullvad.net — + malware
    "194.242.2.5",  // extended.dns.mullvad.net — + lebih luas
    "194.242.2.6",  // family.dns.mullvad.net — + dewasa/judi/medsos
    "194.242.2.9",  // all.dns.mullvad.net — semua kategori
    "2a07:e340::3", "2a07:e340::4",
    // IPv6 untuk extended/family/all Mullvad SENGAJA TIDAK diikutkan —
    // pola penomoran IPv6-nya konsisten dengan IPv4 (::5/::6/::9) tapi
    // tidak ditemukan konfirmasi eksplisit dari sumber terpisah saat
    // verifikasi, jadi tidak dihardcode daripada menebak.
    // SENGAJA TIDAK diikutkan: 194.242.2.2/2a07:e340::2 ("main"/
    // dns.mullvad.net) — varian itu TIDAK memfilter apa pun.
};
constexpr size_t kAdBlockDnsIpCount = sizeof(kAdBlockDnsIps) / sizeof(kAdBlockDnsIps[0]);

// BUG FIX: sebelumnya daftar ini TIDAK ADA sama sekali — hanya IP resolver
// yang bisa dicocokkan. Begitu sisi Kotlin (AdBlockDetector.kt) diperbaiki
// untuk juga mengirim LinkProperties.privateDnsServerName (mis. string
// hostname persis "dns.adguard.com" saat pengguna set Private DNS mode
// "Hostname" di Settings -> Network), string HOSTNAME itu tetap tidak akan
// pernah cocok dengan array kAdBlockDnsIps di atas karena isinya murni IP
// — inilah sebab pasti kenapa laporan pengguna yang test dengan
// "dns.adguard.com" tidak terdeteksi walau providernya sudah ada di daftar
// IP. Daftar hostname RESMI provider di bawah (dicocokkan case-insensitive
// dan sebagai substring — lihat containsHostname — karena hostname Private
// DNS valid secara RFC tidak case-sensitive dan beberapa OS/launcher bisa
// menambahkan trailing dot atau varian penulisan).
constexpr const char* kAdBlockDnsHostnames[] = {
    "dns.adguard.com",         // AdGuard DNS — Default
    "dns-family.adguard.com",  // AdGuard DNS — Family protection
    "adblock.dns.mullvad.net",
    "base.dns.mullvad.net",
    "extended.dns.mullvad.net",
    "family.dns.mullvad.net",
    "all.dns.mullvad.net",
    // SENGAJA TIDAK diikutkan: dns.mullvad.net ("main"/unfiltered) —
    // sama seperti pengecualian IP 194.242.2.2 di atas, varian itu tidak
    // memfilter apa pun.
};
constexpr size_t kAdBlockDnsHostnameCount =
    sizeof(kAdBlockDnsHostnames) / sizeof(kAdBlockDnsHostnames[0]);

bool isKnownAdBlockDnsIp(const char* ip) {
    for (size_t i = 0; i < kAdBlockDnsIpCount; i++) {
        if (strcmp(ip, kAdBlockDnsIps[i]) == 0) return true;
    }
    return false;
}

bool isKnownAdBlockDnsHostname(const char* value) {
    for (size_t i = 0; i < kAdBlockDnsHostnameCount; i++) {
        // strcasestr (bukan strcasecmp) SENGAJA dipakai: privateDnsServerName
        // di beberapa OEM/OS build bisa punya trailing dot FQDN
        // ("dns.adguard.com.") atau, secara teori, prefix — substring
        // case-insensitive menangkap semua varian itu tanpa perlu daftar
        // varian eksplisit per hostname.
        if (strcasestr(value, kAdBlockDnsHostnames[i]) != nullptr) return true;
    }
    return false;
}

// ---------------------------------------------------------------------
// 3) Pencocokan KATA KUNCI pada listing direktori modul Magisk (bukan
//    daftar ID modul yang presisi/lengkap — lihat catatan batasan di
//    atas). Case-insensitive karena penamaan folder modul Magisk tidak
//    punya konvensi huruf besar/kecil yang konsisten antar developer.
// ---------------------------------------------------------------------

constexpr const char* kAdBlockModuleKeywords[] = {
    "adaway",             // AdAway — hosts-based adblocker paling dikenal
    "systemless-hosts",   // Framework hosts-based generik yang sering dipakai adblock
    "systemlesshosts",
    "adblock",
    "ad-block",
    "ad_block",
    "youtube_adaway",     // Modul spesifik hapus iklan YouTube via hosts
    "noads",
    "no-ads",
    "no_ads",
};
constexpr size_t kAdBlockModuleKeywordCount =
    sizeof(kAdBlockModuleKeywords) / sizeof(kAdBlockModuleKeywords[0]);

bool containsKnownAdBlockModuleKeyword(const char* listing) {
    for (size_t i = 0; i < kAdBlockModuleKeywordCount; i++) {
        if (strcasestr(listing, kAdBlockModuleKeywords[i]) != nullptr) {
            GUARD_LOG("kata kunci modul adblock ditemukan: %s", kAdBlockModuleKeywords[i]);
            return true;
        }
    }
    return false;
}

}  // namespace

// Dipanggil dari AdBlockDetector.kt (didaftarkan sebagai
// "nativeDetectVpnInterface" lewat RegisterNatives di jni_onload.cpp).
// Tidak butuh argumen — murni baca state sistem sendiri.
extern "C" JNIEXPORT jboolean JNICALL
nvpn(JNIEnv* /* env */, jobject /* thiz */) {
    return detectVpnInterface() ? JNI_TRUE : JNI_FALSE;
}

// Dipanggil dari AdBlockDetector.kt (didaftarkan sebagai
// "nativeMatchAdBlockDns"). [dnsServers] adalah String[] hasil
// ConnectivityManager.getLinkProperties(...).dnsServers DIGABUNG dengan
// getLinkProperties(...).privateDnsServerName kalau ada (lihat
// AdBlockDetector.kt) — jadi elemen array ini BISA berupa IP resolver
// ATAU hostname Private DNS, dicek terhadap DUA daftar terpisah di bawah
// (bukan cuma satu seperti sebelumnya) — file ini HANYA mencocokkan,
// tidak pernah membaca konfigurasi jaringan sendiri secara langsung.
extern "C" JNIEXPORT jboolean JNICALL
ndns(JNIEnv* env, jobject /* thiz */, jobjectArray dnsServers) {
    if (dnsServers == nullptr) return JNI_FALSE;

    const jsize count = env->GetArrayLength(dnsServers);
    for (jsize i = 0; i < count; i++) {
        auto element = static_cast<jstring>(env->GetObjectArrayElement(dnsServers, i));
        if (element == nullptr) continue;

        const char* value = env->GetStringUTFChars(element, nullptr);
        const bool match = (value != nullptr) &&
            (isKnownAdBlockDnsIp(value) || isKnownAdBlockDnsHostname(value));
        if (value != nullptr) env->ReleaseStringUTFChars(element, value);
        env->DeleteLocalRef(element);

        if (match) {
            GUARD_LOG("DNS server cocok dengan penyedia adblock yang dikenal");
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

// Dipanggil dari AdBlockDetector.kt (didaftarkan sebagai
// "nativeMatchAdBlockModule"). [moduleListing] adalah HASIL MENTAH
// perintah shell `ls /data/adb/modules/` (satu string, nama-nama modul
// dipisah baris baru) yang sudah dijalankan lewat ShellExecutor root
// yang SUDAH ADA di sisi Kotlin — file ini TIDAK melakukan akses
// file/shell apa pun sendiri (proses app biasa, bahkan dengan Magisk
// terpasang di device, TIDAK otomatis punya izin baca
// /data/adb/modules/ tanpa lewat su — lihat AdBlockDetector.kt untuk
// penjelasan lengkap kenapa listing-nya harus datang dari Kotlin).
extern "C" JNIEXPORT jboolean JNICALL
nmod(JNIEnv* env, jobject /* thiz */, jstring moduleListing) {
    if (moduleListing == nullptr) return JNI_FALSE;

    const char* listing = env->GetStringUTFChars(moduleListing, nullptr);
    if (listing == nullptr) return JNI_FALSE;

    const bool match = containsKnownAdBlockModuleKeyword(listing);
    env->ReleaseStringUTFChars(moduleListing, listing);
    return match ? JNI_TRUE : JNI_FALSE;
}
