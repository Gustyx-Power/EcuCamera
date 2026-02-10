cmake_minimum_required(VERSION 3.22.1)

project("ecucamera")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add the ecu-bridge library
add_library(ecu-bridge
    SHARED
    ecu-bridge.cpp
)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Import the pre-built Rust library
add_library(ecucamera_engine SHARED IMPORTED)
set_target_properties(ecucamera_engine PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libecucamera_engine.so
)

# Link libraries
target_link_libraries(ecu-bridge
    ${log-lib}
    ${android-lib}
    ecucamera_engine
)

# Include directories
target_include_directories(ecu-bridge PRIVATE
    include/
)
