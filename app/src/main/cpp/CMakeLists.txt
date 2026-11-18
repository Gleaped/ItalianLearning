cmake_minimum_required(VERSION 3.22.1)
project(italiano_whisper LANGUAGES C CXX)

include(FetchContent)

# whisper.cpp (MIT) is fetched at configure time, pinned to a release tag.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whispercpp
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.7.5.tar.gz
)
FetchContent_MakeAvailable(whispercpp)

add_library(italiano_whisper SHARED whisper_jni.cpp)
target_include_directories(italiano_whisper PRIVATE ${whispercpp_SOURCE_DIR}/include ${whispercpp_SOURCE_DIR}/ggml/include)
target_compile_options(italiano_whisper PRIVATE -O3)
# Android 15+ devices may use 16 KB memory pages.
target_link_options(italiano_whisper PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(italiano_whisper PRIVATE whisper log)
