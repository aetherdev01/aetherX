# ==================== Delta for cpp/CMakeLists.txt ====================
# CHANGES:
#   1. CXX_FLAGS_RELEASE extended with:
#        -fno-exceptions -fno-rtti
#        -fno-unwind-tables -fno-asynchronous-unwind-tables
#        -fomit-frame-pointer
#   2. Linker options: --build-id=sha1 replaced by --build-id=none
#      (removes .note.gnu.build-id entirely); added -Wl,-z,noexecheap.
#
# REASON:
#   Removes debugging aids (unwind info, frame pointers, type info) so the
#   .so becomes much harder to analyse with standard tools.  The anti-debug
#   code in jni_onload.cpp complements the static hardening by blocking
#   runtime attach attempts.
# =====================================================================

cmake_minimum_required(VERSION 3.22.1)
project("aetherX")

# -O2 + strip symbols di release: binary lebih kecil dan lebih sulit dibaca
# lewat disassembler dibanding build dengan simbol debug lengkap.
#
# CATATAN soal -fvisibility=hidden + RegisterNatives (lihat jni_onload.cpp):
# flag ini menyembunyikan SEMUA simbol secara default KECUALI yang eksplisit
# ditandai __attribute__((visibility("default"))) — makro JNIEXPORT dari NDK
# sudah melakukan itu otomatis untuk JNI_OnLoad, nvfy, nvfy2, nvint, nvpn,
# ndns, dan nmod (semuanya dideklarasikan pakai JNIEXPORT), jadi tetap bisa
# dipanggil JVM/RegisterNatives walau visibility default project ini hidden.
# Fungsi non-JNI lain (constantTimeEquals, fnv1a64, dkk di dalam namespace
# anonim) TETAP tersembunyi seperti sebelumnya — tidak ada regresi di situ.
#
# --- HARDENING TAMBAHAN (flags baru) ---
# -fno-exceptions -fno-rtti: meniadakan exception-handling dan RTTI (tidak
#     digunakan di kode ini) → menghilangkan data .eh_frame_hdr, .gcc_except_table,
#     type_info, dsb. yang biasa dimanfaatkan tool reversing.
# -fno-unwind-tables -fno-asynchronous-unwind-tables: menghapus tabel
#     unwinding, menyulitkan backtrace saat crash dan membingungkan disassembler.
# -fomit-frame-pointer: tidak lagi menggunakan frame pointer (%rbp), menghemat
#     register dan membuat analisis stack frame lebih sulit.
# -static-libstdc++ (lihat target_link_options) menanamkan runtime C++ langsung
#     ke dalam libaetherX.so.  Bersama strip --strip-unneeded ukuran akhir
#     tetap besar tapi tetap stripped — tidak ada libc++_shared.so terpisah.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fstack-clash-protection -fno-exceptions -fno-rtti -fno-unwind-tables -fno-asynchronous-unwind-tables -fomit-frame-pointer")

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

# --- Hardening linker, konsisten di semua ABI ---
# BIND_NOW + full RELRO, non-executable stack, code separate dari data,
# no build-id (tidak ada .note.gnu.build-id) → menyulitkan identifikasi.
# -static-libstdc++: tidak perlu libc++_shared.so terpisah.
# -z noexecheap: tandai heap sebagai non-executable (mengurangi path eksploit).
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -static-libstdc++
    -Wl,-z,now
    -Wl,-z,relro
    -Wl,-z,noexecstack
    -Wl,-z,separate-code
    -Wl,-z,noexecheap
    -Wl,--build-id=none
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--icf=all
)

# Strip setelah link.  Simbol non-dynamic dihapus – hanya JNI_OnLoad dan
# fungsi JNI yang tetap sebagai exported dynamic symbol.
add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
)