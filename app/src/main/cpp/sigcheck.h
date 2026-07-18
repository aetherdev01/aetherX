#pragma once

// sigcheck.h — kontrak untuk sigcheck.cpp.
//
// Modul ini memverifikasi hash signing certificate APK yang dikirim dari
// Kotlin (SignatureGuard.kt) terhadap hash yang di-hardcode (dan
// di-XOR-obfuscate) di sisi native, supaya nilai hash asli tidak muncul
// sebagai string/byte-array plain yang mudah dibaca lewat disassembler
// DEX/Kotlin bytecode.
//
// Deklarasi JNIEXPORT aktual (nvfy, nvfy2) tetap tinggal di
// native_symbols.h karena itu yang dipakai jni_onload.cpp untuk
// RegisterNatives — header ini HANYA mendokumentasikan kontrak & konstanta
// modul supaya tidak perlu buka sigcheck.cpp untuk tahu bentuknya.

#include <cstddef>
#include <cstdint>

namespace aetherx::sigcheck {

// Panjang hash signing certificate yang dibandingkan (SHA-256 = 32 byte).
inline constexpr int kHashLen = 32;

// Kontrak nvfy / nvfy2 (lihat native_symbols.h untuk deklarasi JNIEXPORT
// sebenarnya, dan SignatureGuard.kt untuk sisi pemanggil):
//
//   jboolean nvfy(JNIEnv*, jobject, jbyteArray actualHashBytes)
//   jboolean nvfy2(JNIEnv*, jobject, jbyteArray actualHashBytes)
//
// - actualHashBytes: hash signing certificate APK yang berjalan saat ini
//   (dihitung dari PackageManager.GET_SIGNATURES di sisi Kotlin), harus
//   tepat kHashLen byte.
// - Return JNI_TRUE hanya jika actualHashBytes, setelah dibandingkan dalam
//   waktu konstan, sama persis dengan hash yang di-decode dari
//   kEncodedHash ^ kXorKey di sigcheck.cpp.
// - nvfy2 adalah re-check dengan kontrak identik ke nvfy (dipanggil di
//   titik berbeda dari alur verifikasi Kotlin sebagai lapisan pertahanan
//   kedua, lihat komentar "recheck" di SignatureGuard.kt).
// - Kedua fungsi TIDAK punya efek samping dan aman dipanggil berkali-kali.

}  // namespace aetherx::sigcheck
