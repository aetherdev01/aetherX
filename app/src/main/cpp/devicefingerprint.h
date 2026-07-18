#pragma once

// devicefingerprint.h — kontrak untuk devicefingerprint.cpp.
//
// LATAR BELAKANG: sebelumnya, deviceId yang dipakai mengunci lisensi
// (LicenseRepository.kt) dan mendata perangkat (DeviceRegistry.kt) adalah
// ANDROID_ID mentah (Settings.Secure.ANDROID_ID), dibaca & dikirim ke
// Firestore APA ADANYA dari Kotlin (lihat DeviceId.kt). Masalahnya, nilai
// ini trivial diambil siapa pun lewat:
//   adb shell settings get secure android_id
// atau dibaca ulang dari APK hasil decompile yang tahu field mana yang
// dipakai. Siapa pun yang tahu ANDROID_ID device korban (mis. dari backup/
// screenshot debug/log yang bocor) bisa mem-forge device lain supaya
// Firestore mengiranya device yang sama, berpotensi membajak binding
// lisensi device tersebut.
//
// SOLUSI: bukan ANDROID_ID mentah yang dikirim ke Firestore, tapi HASH-nya
// yang dihitung di NATIVE dengan kunci HMAC yang di-XOR-obfuscate (pola
// sama dengan sigcheck.cpp) — supaya orang yang cuma tahu ANDROID_ID device
// korban TETAP TIDAK BISA reproduksi fingerprint hash yang benar tanpa
// reverse-engineer kunci HMAC dari binary native (jauh lebih sulit
// dibanding baca field dari kode Kotlin/APK yang dekompilasi).
//
// CATATAN JUJUR SOAL BATASAN (sama semangatnya dengan SignatureGuard.kt):
// ini MENAIKKAN EFFORT untuk clone/forge device casual, BUKAN proteksi
// sempurna. Siapa pun yang berhasil dump memori/hook fungsi nfgp lewat
// Frida tetap bisa membaca hash yang dihasilkan untuk device tertentu
// (walau begitu, mereka tetap tidak dapat kunci HMAC-nya, sehingga tidak
// bisa menghitung hash untuk device LAIN secara offline). Validasi
// binding sebenarnya tetap di server (Firestore rules mengunci
// licenses/{key}.deviceId ke SATU nilai) — modul ini hanya membuat nilai
// yang dikunci itu tidak lagi trivial diprediksi/di-forge dari luar.

#include <cstddef>
#include <cstdint>

namespace aetherx::devicefingerprint {

// Ukuran output HMAC-SHA256 (32 byte / 64 hex char).
inline constexpr int kDigestLen = 32;

// Batas ukuran input mentah (gabungan ANDROID_ID + Build.FINGERPRINT +
// Build.MODEL dari sisi Kotlin, lihat DeviceFingerprint.kt) yang diterima
// modul ini. Input lebih panjang dari ini akan DIPOTONG (truncate), bukan
// ditolak — cukup longgar untuk kombinasi field yang dipakai saat ini.
inline constexpr size_t kMaxInputLen = 512;

// Kontrak nfgp (lihat native_symbols.h untuk deklarasi JNIEXPORT
// sebenarnya, dan DeviceFingerprint.kt untuk sisi pemanggil):
//
//   jbyteArray nfgp(JNIEnv*, jobject, jbyteArray rawInput)
//
// - rawInput: byte UTF-8 dari string gabungan identifier perangkat mentah
//   (ANDROID_ID + Build.FINGERPRINT + Build.MODEL, digabung dengan
//   separator tetap di sisi Kotlin — lihat DeviceFingerprint.kt).
// - Return: array kDigestLen (32) byte, hasil HMAC-SHA256(rawInput, key)
//   dengan `key` yang di-decode dari konstanta XOR-obfuscate di
//   devicefingerprint.cpp saat runtime (tidak pernah muncul sebagai
//   plaintext).
// - rawInput == nullptr atau kosong -> return nullptr (jbyteArray null),
//   BUKAN array kosong/nol, supaya pemanggil di Kotlin tidak keliru
//   menganggap hash-nol sebagai fingerprint valid.
// - Fungsi ini TIDAK menyimpan state, tidak melakukan I/O, dan aman
//   dipanggil berkali-kali (idempotent: input sama -> output sama).

}  // namespace aetherx::devicefingerprint
