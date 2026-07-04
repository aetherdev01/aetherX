cmake_minimum_required(VERSION 3.22.1)
project("aetherxsig")

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
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -fvisibility=hidden -fvisibility-inlines-hidden")

add_library(${CMAKE_PROJECT_NAME} SHARED
    sigcheck.cpp
    integrityguard.cpp
    jni_onload.cpp
)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
)
