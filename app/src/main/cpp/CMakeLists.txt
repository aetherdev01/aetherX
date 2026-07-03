cmake_minimum_required(VERSION 3.22.1)
project("aetherxsig")

# -O2 + strip symbols di release: binary lebih kecil dan lebih sulit dibaca
# lewat disassembler dibanding build dengan simbol debug lengkap.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -fvisibility=hidden -fvisibility-inlines-hidden")

add_library(${CMAKE_PROJECT_NAME} SHARED
    sigcheck.cpp
)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
)
