cmake_minimum_required(VERSION 3.18.1)
project(stockfishjni)

set(NATIVE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../cpp")

file(GLOB_RECURSE STOCKFISH_SOURCES "${NATIVE_ROOT}/stockfish/*.cpp")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*main\\.cpp$")

add_library(stockfishjni SHARED
    "${NATIVE_ROOT}/bridge.cpp"
    ${STOCKFISH_SOURCES}
)

target_compile_definitions(stockfishjni PRIVATE
    NNUE_EMBEDDING_OFF
)

target_compile_options(stockfishjni PRIVATE
    -O3
    -std=c++17
    -fexceptions
)

target_include_directories(stockfishjni PRIVATE
    "${NATIVE_ROOT}/stockfish"
)

target_link_libraries(stockfishjni android log)
