cmake_minimum_required(VERSION 3.18.1)
project(stockfishjni)

find_package(JNI REQUIRED)

set(NATIVE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../cpp")

file(GLOB_RECURSE STOCKFISH_SOURCES "${NATIVE_ROOT}/stockfish/*.cpp")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*main\\.cpp$")

add_library(stockfishjni SHARED
    "${NATIVE_ROOT}/bridge.cpp"
    ${STOCKFISH_SOURCES}
)

set_target_properties(stockfishjni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_compile_definitions(stockfishjni PRIVATE
    NNUE_EMBEDDING_OFF
)

if(MSVC)
    target_compile_options(stockfishjni PRIVATE /O2 /EHsc)
else()
    target_compile_options(stockfishjni PRIVATE -O3 -fexceptions)
endif()

target_include_directories(stockfishjni PRIVATE
    "${NATIVE_ROOT}/stockfish"
    ${JNI_INCLUDE_DIRS}
)

if(WIN32)
    target_link_libraries(stockfishjni ${JNI_LIBRARIES})
else()
    target_link_libraries(stockfishjni ${JNI_LIBRARIES} pthread)
endif()
