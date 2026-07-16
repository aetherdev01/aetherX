cmake_minimum_required(VERSION 3.22.1)
project("aetherX")

# -O2 + strip symbols di release: binary lebih kecil dan lebih sulit dibaca
# lewat disassembler dibanding build dengan simbol debug lengkap.
#
# CATATAN soal -fvisibility=hidden + RegisterNatives (lihat jni_onload.cpp):
# flag ini menyembunyikan SEMUA simbol secara default KECUALI yang eksplisit
# ditandai __attribute__((visibility("default"))) — makro JNIEXPORT dari NDK
# sudah melakukan itu otomatis untuk JNI_OnLoad, nvfy, nvfy2, nvint, nvpn,
# ndns, dan nmod (semuanya dideklarasikan pakai JNIEXPORT — tiga yang
# terakhir ditambahkan di adblockguard.cpp, lihat file itu), jadi tetap bisa
# dipanggil JVM/RegisterNatives walau visibility default project ini hidden.
# Fungsi non-JNI lain (constantTimeEquals, fnv1a64, dkk di dalam namespace
# anonim) TETAP tersembunyi seperti sebelumnya — tidak ada regresi di situ.
#
# FITUR BARU — static-link + hardening tambahan (permintaan: "jadikan hasil
# .so lebih besar dan hard"): -static-libstdc++ (lihat target_link_options
# di bawah) menanamkan runtime C++ langsung ke dalam libaetherX.so (bukan
# cuma referensi ke libc++_shared.so terpisah) — .so jadi mandiri/self-
# contained dan ukurannya naik karena membawa isi runtime C++ sendiri,
# sekaligus menghilangkan satu file .so terpisah yang bisa jadi target
# patch/swap dari luar. -D_FORTIFY_SOURCE=2 + -fstack-clash-protection
# menambah lapis hardening compile-time di atas -fstack-protector-strong
# yang sudah ada sebelumnya.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fstack-clash-protection")

# Nama library HARUS "aetherX" (menghasilkan libaetherX.so) — cocok dengan
# System.loadLibrary("aetherX") di SignatureGuard.kt, NativeIntegrityGuard.kt,
# DAN AdBlockDetector.kt (tiga file, SEMUA memuat SATU library yang sama,
# bukan tiga library terpisah). ${CMAKE_PROJECT_NAME} di sini mengambil
# nilai dari project("aetherX") di baris 2 di atas, jadi KEDUANYA (nama
# project & nama library) sengaja sama persis "aetherX" — pastikan
# project() TIDAK diganti tanpa ikut mengganti System.loadLibrary() di
# KETIGA file Kotlin tersebut juga.
add_library(${CMAKE_PROJECT_NAME} SHARED
    sigcheck.cpp
    integrityguard.cpp
    adblockguard.cpp
    jni_onload.cpp
)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
)

# --- Hardening linker, konsisten di semua ABI (arm64-v8a termasuk) ---
# BIND_NOW + full RELRO terverifikasi ada di build armeabi-v7a sebelumnya
# (readelf -d -> FLAGS: BIND_NOW, FLAGS_1: NOW). --gc-sections + --icf TETAP
# dipertahankan (bukan cuma soal ukuran — ini juga memangkas gadget ROP yang
# berguna untuk exploit, jadi tetap bagian dari hardening walau .so jadi
# lebih besar dari static-link di bawah). -static-libstdc++ menanamkan
# runtime C++ langsung ke .so ini (lihat catatan di CXX_FLAGS_RELEASE di
# atas) — TIDAK ADA lagi libc++_shared.so terpisah yang perlu di-pack ke
# APK untuk ABI ini. -z noexecstack + -z separate-code menambah lapis
# hardening memori standar (stack tidak bisa dieksekusi, code segment
# dipisah dari data segment secara ketat di halaman memori).
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -static-libstdc++
    -Wl,-z,now
    -Wl,-z,relro
    -Wl,-z,noexecstack
    -Wl,-z,separate-code
    -Wl,--build-id=sha1
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--icf=all
)

# Strip simbol non-dynamic setelah link, konsisten di semua ABI supaya hasil
# arm64-v8a tetap fully stripped seperti build v7a (nm hanya menyisakan
# JNI_OnLoad, nvfy, nvfy2, nvint, nvpn, ndns, nmod). Simbol runtime C++ yang
# ikut ditanamkan lewat -static-libstdc++ di atas TETAP ikut di-strip di
# sini juga — statis bukan berarti dibiarkan telanjang tanpa strip.
add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
)
