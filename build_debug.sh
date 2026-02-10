#!/bin/bash

set -e

echo "========================================"
echo "EcuCamera Debug Build Script (Unix/Linux/macOS)"
echo "========================================"
echo

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_status "Starting complete debug build pipeline..."

# Check prerequisites
print_status "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    print_error "Java not found. Please install Java or Android Studio"
    exit 1
fi
print_success "Java found"

if ! command -v cargo &> /dev/null; then
    print_error "Rust/Cargo not found. Please install Rust from https://rustup.rs/"
    exit 1
fi
print_success "Rust/Cargo found"

ADB_FOUND=false
if command -v adb &> /dev/null; then
    print_success "ADB found"
    ADB_FOUND=true
    ADB_PATH="adb"
else
    print_warning "ADB not found in PATH. Will try to use Android SDK version"
fi

# Auto-detect Android SDK if not set
if [ -z "$ANDROID_HOME" ]; then
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

if [ ! -d "$ANDROID_HOME" ]; then
    print_error "Android SDK not found at $ANDROID_HOME"
    print_error "Please install Android Studio and SDK"
    exit 1
fi
print_success "Android SDK found: $ANDROID_HOME"

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
print_success "NDK found: $ANDROID_NDK_ROOT"

# Set up ADB path if not in system PATH
if [ "$ADB_FOUND" = false ]; then
    if [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB_PATH="$ANDROID_HOME/platform-tools/adb"
        print_status "Using ADB from Android SDK"
        ADB_FOUND=true
    else
        print_warning "ADB not found. Device detection and installation will be skipped"
        ADB_PATH=""
    fi
fi

echo
print_status "=== PHASE 1: Building Rust Library (Release) ==="

# Check if Rust Android target is installed
if ! rustup target list --installed | grep -q "aarch64-linux-android"; then
    print_status "Adding Android target for Rust..."
    rustup target add aarch64-linux-android
    if [ $? -ne 0 ]; then
        print_error "Failed to add Android target"
        exit 1
    fi
fi
print_success "Android target ready"

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

# Set environment variables for cross-compilation
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang"
export CC_aarch64_linux_android="$TOOLCHAIN_PATH/bin/aarch64-linux-android29-clang"
export AR_aarch64_linux_android="$TOOLCHAIN_PATH/bin/llvm-ar"
export PATH="$TOOLCHAIN_PATH/bin:$PATH"

print_status "Cross-compilation environment configured"

# Build Rust library
print_status "Building Rust library (release mode)..."
cd app/src/main/rust

if cargo build --release --target aarch64-linux-android; then
    print_success "Rust library built successfully"
else
    print_error "Rust build failed!"
    exit 1
fi

# Create jniLibs directory and copy library
mkdir -p ../jniLibs/arm64-v8a
if cp target/aarch64-linux-android/release/libecucamera_engine.so ../jniLibs/arm64-v8a/; then
    print_success "Rust library copied to jniLibs"
else
    print_error "Failed to copy Rust library"
    exit 1
fi

cd ../../../..

echo
print_status "=== PHASE 2: Building Android Project (Debug) ==="

# Clean previous build
print_status "Cleaning previous build..."
if ./gradlew clean; then
    print_success "Clean completed"
else
    print_warning "Clean failed, continuing anyway..."
fi

# Build debug APK
print_status "Building debug APK (includes C++ compilation)..."
if ./gradlew assembleDebug; then
    print_success "Debug APK built successfully"
else
    print_error "Android build failed!"
    exit 1
fi

# Verify APK exists
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    print_error "APK not found at expected location"
    exit 1
fi

APK_SIZE=$(stat -f%z "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null || stat -c%s "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null || echo "unknown")
print_success "APK created: app/build/outputs/apk/debug/app-debug.apk ($APK_SIZE bytes)"

echo
print_status "=== PHASE 3: Device Detection and Installation ==="

if [ -z "$ADB_PATH" ]; then
    print_warning "ADB not available. Skipping device detection and installation"
else
    # Check for connected devices
    print_status "Checking for connected Android devices..."
    
    if ! "$ADB_PATH" devices &> /dev/null; then
        print_warning "Failed to run ADB. Skipping device detection"
    else
        # Count devices (excluding header line and unauthorized devices)
        DEVICE_COUNT=$("$ADB_PATH" devices | grep -c "device$" || echo "0")
        
        if [ "$DEVICE_COUNT" -eq 0 ]; then
            print_warning "No Android devices connected via USB"
            echo
            echo "To install manually:"
            echo "1. Enable USB debugging on your Android device"
            echo "2. Connect device via USB"
            echo "3. Run: adb install -r app/build/outputs/apk/debug/app-debug.apk"
        else
            print_success "Found $DEVICE_COUNT connected Android device(s) with USB debugging enabled"
            
            # Check if any device is unauthorized
            if "$ADB_PATH" devices | grep -q "unauthorized"; then
                print_warning "Device found but not authorized. Please check your device for USB debugging authorization dialog"
            else
                # Install APK automatically
                print_status "Installing APK on connected device(s)..."
                if "$ADB_PATH" install -r "app/build/outputs/apk/debug/app-debug.apk"; then
                    print_success "APK installed successfully!"
                    echo
                    print_status "You can now launch EcuCamera on your device"
                    
                    # Try to launch the app
                    print_status "Attempting to launch EcuCamera..."
                    if "$ADB_PATH" shell am start -n id.xms.ecucamera/.MainActivity &> /dev/null; then
                        print_success "EcuCamera launched successfully!"
                    else
                        print_warning "Could not auto-launch app. Please launch manually from device"
                    fi
                else
                    print_error "Failed to install APK. Please check device connection and try manual installation"
                fi
            fi
        fi
    fi
fi

echo
echo "========================================"
print_success "BUILD COMPLETE!"
echo "========================================"
echo
echo "Build Summary:"
echo "  Rust Engine: BUILT (Release mode, optimized)"
echo "  C++ Bridge:  BUILT (Debug mode, with symbols)"
echo "  Android App: BUILT (Debug mode, debuggable)"
echo "  APK Size:    $APK_SIZE bytes"
echo "  Location:    app/build/outputs/apk/debug/app-debug.apk"
echo

if [ "$DEVICE_COUNT" -gt 0 ] 2>/dev/null; then
    echo "Device Status: INSTALLED and READY"
else
    echo "Device Status: Manual installation required"
fi

echo
echo "Next steps:"
echo "  - Launch EcuCamera on your device"
echo "  - Check native bridge status in the app"
echo "  - View logs: adb logcat | grep -E \"EcuBridge|NativeBridge|MainActivity\""
echo