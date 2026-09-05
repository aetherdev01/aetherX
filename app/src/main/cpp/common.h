#pragma once

// common.h — util kecil yang dipakai bersama oleh beberapa modul native
// (mis. devicefingerprint.cpp). Dipisah ke header supaya tidak
// ada duplikasi implementasi antar file .cpp.
//
// PENTING: fungsi di sini SENGAJA didefinisikan `inline` di dalam
// namespace aetherx::common, BUKAN di namespace anonim, karena header ini
// bisa di-include dari lebih dari satu translation unit. Kalau didefinisikan
// tanpa `inline` di header yang di-include berkali-kali, linker akan
// menganggapnya multiple-definition. Fungsi-fungsi ini TETAP tidak exported
// keluar dari libaetherX.so (lihat -fvisibility=hidden di CMakeLists.txt) —
// hanya visible antar translation unit di dalam library yang sama.

#include <cstddef>
#include <cstdint>

namespace aetherx::common {

// Perbandingan byte-per-byte dalam waktu konstan, tidak short-circuit,
// supaya durasi eksekusi tidak bocor informasi soal di mana perbedaan
// pertama terjadi (mencegah timing attack terhadap hash signature/checksum).
inline bool constantTimeEquals(const uint8_t* a, const uint8_t* b, size_t len) {
    uint8_t diff = 0;
    for (size_t i = 0; i < len; i++) {
        diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    }
    return diff == 0;
}

// XOR decode generik: out[i] = encoded[i] ^ key[i]. Dipakai untuk membongkar
// nilai (hash/checksum) yang disimpan ter-obfuscate secara XOR supaya tidak
// muncul sebagai konstanta plain di DEX/native bytecode.
inline void xorDecode(const uint8_t* encoded, const uint8_t* key, uint8_t* out, size_t len) {
    for (size_t i = 0; i < len; i++) {
        out[i] = encoded[i] ^ key[i];
    }
}

}  // namespace aetherx::common
