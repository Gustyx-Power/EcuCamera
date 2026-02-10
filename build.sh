#!/bin/bash

set -e

echo "EcuCamera Debug Build Script for Linux/macOS"
echo "============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check for required tools
print_status "Checking for required tools..."

# Check for Java
if ! command -v java &> /dev/null; then
    print_error "Java not found. Please install Java 17 or later."
    exit 1
fi

# Check for Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    # Try common locations
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
    else
        print_error "Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT environment variable."
        exit 1
    fi
fi

print_success "Android SDK found at: $ANDROID_HOME"

# Add Android SDK tools to PATH
export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools:$PATH"

# Check for Rust/Cargo
if command -v cargo &> /dev/null; then
    print_success "Cargo found, building Rust components..."
    
    # Build Rust components
    cd app/src/main/rust
    if cargo build --release; then
        print_success "Rust build completed successfully."
    else
        print_warning "Rust build failed, continuing with Android build..."
    fi
    cd ../../../..
else
    print_warning "Cargo not found in PATH. Rust components may not build properly."
    print_warning "Please install Rust from https://rustup.rs/"
fi

# Check for Gradle wrapper
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
    chmod +x ./gradlew
else
    # Check for system Gradle
    if command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
    else
        print_error "Neither Gradle wrapper nor system Gradle found."
        exit 1
    fi
fi

print_success "Using Gradle: $GRADLE_CMD"

# Build the APK
print_status "Building Debug APK..."
if $GRADLE_CMD assembleDebug; then
    print_success "Build completed successfully!"
else
    print_error "Build failed!"
    exit 1
fi

# Check for connected devices
print_status "Checking for connected Android devices..."
if command -v adb &> /dev/null; then
    DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    
    if [ "$DEVICES" -gt 0 ]; then
        print_success "Found $DEVICES connected Android device(s)!"
        echo
        read -p "Do you want to install the APK on connected device? (y/n): " install_choice
        
        if [[ $install_choice =~ ^[Yy]$ ]]; then
            print_status "Installing APK..."
            if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
                print_success "APK installed successfully!"
                print_success "You can now launch the app on your device."
            else
                print_error "Failed to install APK. Make sure USB debugging is enabled."
            fi
        fi
    else
        print_warning "No Android devices connected via USB."
        echo "To install on device:"
        echo "1. Enable USB debugging on your Android device"
        echo "2. Connect device via USB"
        echo "3. Run: adb install -r app/build/outputs/apk/debug/app-debug.apk"
    fi
else
    print_warning "ADB not found in PATH. Cannot check for connected devices."
fi

echo
print_success "APK location: app/build/outputs/apk/debug/app-debug.apk"

# Additional information
echo
echo "Additional commands:"
echo "  Clean build:     $GRADLE_CMD clean"
echo "  Release build:   $GRADLE_CMD assembleRelease"
echo "  Run tests:       $GRADLE_CMD test"
echo "  List devices:    adb devices"
echo "  View logs:       adb logcat"