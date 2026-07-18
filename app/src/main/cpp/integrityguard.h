#pragma once

// integrityguard.h — kontrak untuk integrityguard.cpp.
//
// Modul ini menghitung checksum ringan (FNV-1a 64-bit) atas byte kode dari
// fungsi nvfy/nvfy2 (lihat sigcheck.cpp) di memori saat runtime, lalu
// membandingkannya dengan checksum yang diharapkan. Tujuannya mendeteksi
// patching biner (mis. fungsi sigcheck di-hook/di-overwrite oleh Frida/
// Xposed/inline-hook lain) — bukan untuk verifikasi hash signing cert itu
// sendiri (itu tugas sigcheck.cpp).

#include <cstdint>

namespace aetherx::integrityguard {

// Jumlah byte kode yang di-scan per fungsi saat menghitung checksum live.
inline constexpr size_t kScanLen = 256;

// Kode return nvint (lihat native_symbols.h untuk deklarasi JNIEXPORT
// sebenarnya, dan NativeIntegrityGuard.kt untuk sisi pemanggil):
//
//   jint nvint(JNIEnv*, jobject)
//
//   0 -> checksum TIDAK cocok: kode nvfy/nvfy2 terindikasi telah diubah
//        di memori dibanding saat build (kemungkinan di-hook/di-patch).
//   1 -> checksum cocok: kode dianggap utuh/tidak dimodifikasi.
//   2 -> checksum pembanding BELUM dikonfigurasi (placeholder
//        kEncodedChecksum/kXorKeyChecksum di integrityguard.cpp masih
//        sama persis, artinya nilai asli belum diisi sebelum build
//        release). Perlakukan sebagai "tidak terverifikasi", BUKAN
//        sebagai "aman".
//
// CATATAN: nvint HARUS dipanggil setelah libaetherX.so selesai dimuat
// (di titik yang sama dengan pemanggilan nvfy/nvfy2 dari Kotlin), karena
// checksum live dihitung dari alamat fungsi yang sudah di-load ke memori.

enum class IntegrityResult : int {
    kMismatch = 0,
    kOk = 1,
    kNotConfigured = 2,
};

}  // namespace aetherx::integrityguard
