cmake_minimum_required(VERSION 3.22.1)
project("aetherX")

# -O2 + strip symbols di release: binary lebih kecil dan lebih sulit dibaca
# lewat disassembler dibanding build dengan simbol debug lengkap.
#
# CATATAN soal -fvisibility=hidden + RegisterNatives (lihat jni_onload.cpp):
# flag ini menyembunyikan SEMUA simbol secara default KECUALI yang eksplisit
# ditandai __attribute__((visibility("default"))) — makro JNIEXPORT dari NDK
# sudah melakukan itu otomatis untuk JNI_OnLoad, nvfy, nvfy2, dan nvint
# (semuanya dideklarasikan pakai JNIEXPORT), jadi tetap bisa dipanggil
# JVM/RegisterNatives walau visibility default project ini hidden. Fungsi
# non-JNI lain (constantTimeEquals, fnv1a64, dkk di dalam namespace anonim)
# TETAP tersembunyi seperti sebelumnya — tidak ada regresi di situ.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong")

add_library(${CMAKE_PROJECT_NAME} SHARED
    sigcheck.cpp
    integrityguard.cpp
    jni_onload.cpp
)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
)

# --- Hardening linker, konsisten di semua ABI (arm64-v8a termasuk) ---
# BIND_NOW + full RELRO terverifikasi ada di build armeabi-v7a sebelumnya
# (readelf -d -> FLAGS: BIND_NOW, FLAGS_1: NOW). --gc-sections + --icf
# memangkas kode/simbol duplikat supaya output arm64 tidak jadi jauh lebih
# besar dibanding v7a walau instruksi 64-bit umumnya lebih boros byte.
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -Wl,-z,now
    -Wl,-z,relro
    -Wl,--build-id=sha1
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--icf=all
)

# Strip simbol non-dynamic setelah link, konsisten di semua ABI supaya hasil
# arm64-v8a tetap fully stripped seperti build v7a (nm hanya menyisakan
# JNI_OnLoad, nvfy, nvfy2, nvint).
add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
)
