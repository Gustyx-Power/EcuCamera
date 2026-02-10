#!/bin/bash

set -e

echo "Building Rust library for Android..."

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    print_error "Rust/Cargo not found. Please install Rust from https://rustup.rs/"
    exit 1
fi

# Auto-detect Android SDK if not set
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_NDK_ROOT" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        print_status "Auto-detected ANDROID_HOME: $ANDROID_HOME"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        print_status "Auto-detected ANDROID_HOME: $ANDROID_HOME"
    else
        print_error "Android SDK not found. Please set ANDROID_HOME or install Android Studio"
        exit 1
    fi
fi

# Check if Android SDK exists
if [ ! -d "$ANDROID_HOME" ]; then
    print_error "Android SDK not found at $ANDROID_HOME"
    print_error "Please install Android Studio and SDK"
    exit 1
fi

print_status "Using Android SDK: $ANDROID_HOME"

# Find NDK version
ANDROID_NDK_ROOT=""
for ndk_dir in "$ANDROID_HOME"/ndk/*; do
    if [ -d "$ndk_dir" ]; then
        ANDROID_NDK_ROOT="$ndk_dir"
        break
    fi
done

if [ -z "$ANDROID_NDK_ROOT" ]; then
    print_error "NDK not found in $ANDROID_HOME/ndk/"
    print_error "Please install NDK via Android Studio SDK Manager"
    exit 1
fi

print_status "Using NDK: $ANDROID_NDK_ROOT"

# Determine host OS for toolchain path
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    HOST_TAG="linux-x86_64"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_TAG="darwin-x86_64"
else
    print_warning "Unknown OS type: $OSTYPE, assuming linux-x86_64"
    HOST_TAG="linux-x86_64"
fi

TOOLCHAIN_PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
print_status "Using toolchain: $TOOLCHAIN_PATH"

# Verify toolchain exists
if [ ! -f "$TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang" ]; then
    print_error "NDK toolchain not found at expected location"
    print_error "Expected: $TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang"
    exit 1
fi

# Add Android target for Rust
print_status "Adding Android target for Rust..."
rustup target add aarch64-linux-android
print_status "Android target configured"

# Set environment variables for cross-compilation
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang"
export CC_aarch64_linux_android="$TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang"
export AR_aarch64_linux_android="$TOOLCHAIN_PATH/bin/llvm-ar"

# Add NDK toolchain to PATH for this session
export PATH="$TOOLCHAIN_PATH/bin:$PATH"

print_status "Cross-compilation environment configured"

# Build the Rust library
print_status "Building Rust library..."
cd app/src/main/rust

if cargo build --release --target aarch64-linux-android; then
    print_status "Rust build completed successfully!"
    
    # Create jniLibs directory if it doesn't exist
    mkdir -p ../jniLibs/arm64-v8a
    
    # Copy the built library
    cp target/aarch64-linux-android/release/libecucamera_engine.so ../jniLibs/arm64-v8a/
    
    print_status "Library copied to jniLibs/arm64-v8a/"
    print_status "✅ Rust build and setup complete!"
else
    print_error "Rust build failed!"
    print_error "Make sure you have the Android target installed: rustup target add aarch64-linux-android"
    exit 1
fi

cd ../../../..