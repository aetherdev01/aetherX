#pragma once

// adblockguard.h — kontrak untuk adblockguard.cpp.
//
// Modul ini mencocokkan (1) daftar server DNS aktif perangkat terhadap
// daftar IP/hostname DNS publik yang diketahui memblokir iklan (AdGuard,
// Mullvad, Control D), dan (2) listing modul root (Magisk/KernelSU/APatch)
// terhadap keyword modul adblock/no-ads yang diketahui. Dipanggil dari
// AdBlockDetector.kt untuk menampilkan dialog ke pengguna yang memblokir
// iklan rewarded (lihat core/ads/RewardedAdManager.kt).
//
// CATATAN: deteksi VPN (fungsi nvpn lama) SUDAH DIHAPUS dari alur ini —
// lihat komentar di adblockguard.cpp dan AdBlockDetector.kt. Deteksi VPN
// sekarang sepenuhnya di Kotlin lewat ConnectivityManager.

#include <cstddef>

namespace aetherx::adblockguard {

// Kontrak ndns (lihat native_symbols.h untuk deklarasi JNIEXPORT
// sebenarnya):
//
//   jboolean ndns(JNIEnv*, jobject, jobjectArray dnsServers)
//
// - dnsServers: array String berisi IP dan/atau hostname DNS aktif
//   perangkat (dari LinkProperties / private DNS setting di sisi Kotlin).
// - Return JNI_TRUE jika SALAH SATU entri cocok dengan IP (perbandingan
//   exact-case) atau hostname (perbandingan case-insensitive) di daftar
//   provider adblock yang diketahui (lihat kAdBlockDnsIps /
//   kAdBlockDnsHostnames di adblockguard.cpp).
// - dnsServers == nullptr atau array kosong -> JNI_FALSE.

// Kontrak nmod:
//
//   jboolean nmod(JNIEnv*, jobject, jstring moduleListing)
//
// - moduleListing: satu string gabungan berisi listing modul root yang
//   terpasang (mis. hasil `ls /data/adb/modules` atau sejenisnya,
//   digabung dari sisi Kotlin).
// - Return JNI_TRUE jika listing mengandung SALAH SATU keyword di
//   kAdBlockModuleKeywords (pencocokan substring, case-insensitive),
//   mis. "adaway", "systemless-hosts", "adblock", "no-ads", dst.
// - moduleListing == nullptr -> JNI_FALSE.
//
// Kedua fungsi read-only, tidak menyimpan state antar pemanggilan, dan
// aman dipanggil berulang (mis. tiap kali AdBlockDetector melakukan
// pengecekan periodik).

}  // namespace aetherx::adblockguard
