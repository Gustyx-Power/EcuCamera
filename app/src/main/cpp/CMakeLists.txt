cmake_minimum_required(VERSION 3.18.1)

project("ecucamera")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(RUST_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

link_directories(${RUST_LIB_DIR})

add_library(ecu-bridge SHARED ecu-bridge.cpp)

find_library(log-lib log)

target_link_libraries(ecu-bridge
    android
    ${log-lib}
    -lecucamera_engine
)

target_include_directories(ecu-bridge PRIVATE include/)
