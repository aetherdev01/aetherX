cmake_minimum_required(VERSION 3.22.1)
project("aetherX")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# GANTI -O2 menjadi -Oz (untuk ukuran terkecil) atau -Os.
# TAMBAHKAN -flto untuk Link-Time Optimization (membuat kode sangat sulit di-reverse dan lebih padat).
set(CMAKE_CXX_FLAGS_RELEASE "-Oz -flto -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong")

add_library(${CMAKE_PROJECT_NAME} SHARED
    sigcheck.cpp
    adblockguard.cpp
    devicefingerprint.cpp
    sysmonitor.cpp
    sysmonitor_jni.cpp
    jni_onload.cpp
)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
)

# Tambahkan -flto juga di bagian linker
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -flto
    -Wl,-z,now
    -Wl,-z,relro
    -Wl,--build-id=sha1
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--icf=all
)

add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
)
