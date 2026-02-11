cmake_minimum_required(VERSION 3.18.1)

project("ecucamera")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Define the directory where Rust libs are located
set(RUST_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# 2. Tell linker to look in this directory
link_directories(${RUST_LIB_DIR})

add_library(ecu-bridge
    SHARED
    ecu-bridge.cpp
)

find_library(log-lib log)

# 3. Link using the FILENAME ONLY (no path)
# The "-l" prefix tells the linker: "Record dependency as 'libecucamera_engine.so', not the full path"
target_link_libraries(ecu-bridge
    android
    ${log-lib}
    -lecucamera_engine
)

target_include_directories(ecu-bridge PRIVATE
    include/
)
