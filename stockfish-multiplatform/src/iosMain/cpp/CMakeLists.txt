cmake_minimum_required(VERSION 3.18.1)
project(stockfish_ios)

set(NATIVE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../cpp")

file(GLOB_RECURSE STOCKFISH_SOURCES "${NATIVE_ROOT}/stockfish/*.cpp")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*main\\.cpp$")

add_library(stockfish_ios STATIC
    "${NATIVE_ROOT}/ios_bridge.cpp"
    ${STOCKFISH_SOURCES}
)

set_target_properties(stockfish_ios PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# Architecture-specific SIMD definitions
if(CMAKE_OSX_ARCHITECTURES STREQUAL "arm64")
    target_compile_definitions(stockfish_ios PRIVATE USE_NEON=8)
elseif(CMAKE_OSX_ARCHITECTURES STREQUAL "x86_64")
    target_compile_definitions(stockfish_ios PRIVATE USE_SSE2 USE_POPCNT USE_SSSE3 USE_SSE41)
    target_compile_options(stockfish_ios PRIVATE -msse -msse2 -mssse3 -msse4.1 -mpopcnt)
endif()

target_compile_definitions(stockfish_ios PRIVATE
    NDEBUG
    USE_PTHREADS
)

target_compile_options(stockfish_ios PRIVATE -O3 -fexceptions)

target_include_directories(stockfish_ios PRIVATE
    "${NATIVE_ROOT}/stockfish"
)

# NNUE embedding: if NNUE_DIR is provided, add it to include path so incbin can find the .nnue files.
# If not provided, NNUE_EMBEDDING_OFF is defined to skip embedding.
if(NNUE_DIR AND EXISTS "${NNUE_DIR}")
    target_include_directories(stockfish_ios PRIVATE "${NNUE_DIR}")
else()
    target_compile_definitions(stockfish_ios PRIVATE NNUE_EMBEDDING_OFF)
endif()
