cmake_minimum_required(VERSION 3.22.1)
project("aetherX")

# Native code standards.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Allow standalone C sources as well.
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

# Automatically include every supported native source/header in this directory.
# Supported source formats: .cpp, .CPP, .c, .cc
# Supported header formats: .h, .hpp, .ipp
file(GLOB_RECURSE AETHERX_NATIVE_SOURCES CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/*.cc"
)

file(GLOB_RECURSE AETHERX_NATIVE_HEADERS CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/*.h"
    "${CMAKE_CURRENT_SOURCE_DIR}/*.hpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/*.ipp"
)

# .CPP is explicitly supported as a C++ source suffix on case-sensitive filesystems.
file(GLOB_RECURSE AETHERX_NATIVE_UPPER_CPP CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/*.CPP"
)
if(AETHERX_NATIVE_UPPER_CPP)
    set_source_files_properties(${AETHERX_NATIVE_UPPER_CPP} PROPERTIES LANGUAGE CXX)
    list(APPEND AETHERX_NATIVE_SOURCES ${AETHERX_NATIVE_UPPER_CPP})
    list(REMOVE_DUPLICATES AETHERX_NATIVE_SOURCES)
endif()

# Release hardening/size flags. Keep these centralized so new native files
# receive the same build configuration automatically.
set(CMAKE_CXX_FLAGS_RELEASE "-Oz -flto -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong -fno-rtti -fno-exceptions -ffunction-sections -fdata-sections -fmerge-all-constants")
set(CMAKE_C_FLAGS_RELEASE "-Oz -flto -fvisibility=hidden -fstack-protector-strong -ffunction-sections -fdata-sections -fmerge-all-constants")

add_library(${CMAKE_PROJECT_NAME} SHARED
    ${AETHERX_NATIVE_SOURCES}
    ${AETHERX_NATIVE_HEADERS}
)

# Linker hardening and dead-code elimination.
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -flto
    -Wl,-z,now
    -Wl,-z,relro
    -Wl,--build-id=sha1
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--icf=all
)

# Consistent warnings for both C and C++ sources. Do not turn warnings into
# errors so a new optional native source cannot break the whole application.
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-Wall -Wextra -Wno-unused-parameter>
    # ANDROID_STL=none is intentional for this C-style native layer. Disable
    # C++ exception/RTTI generation for every build type (Debug/Release) so
    # Clang does not emit references to __cxa_begin_catch, std::terminate,
    # or __gxx_personality_v0 that require a C++ runtime library.
    $<$<COMPILE_LANGUAGE:CXX>:-Wall -Wextra -Wno-unused-parameter -fno-exceptions -fno-rtti>
)

add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
)
