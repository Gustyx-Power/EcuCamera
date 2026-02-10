# Contributing to EcuCamera

## Build Environment Setup

EcuCamera uses a multi-language architecture (Kotlin + C++ + Rust) targeting Android devices. Follow the platform-specific setup instructions below.

## Prerequisites

### All Platforms

1. **Git** - Version control
2. **Android Studio** - Latest stable version
3. **Android SDK** - API level 29+ (installed via Android Studio)
4. **Android NDK** - Version 28.2.13676358+ (installed via Android Studio SDK Manager)
5. **Rust** - Latest stable version from [rustup.rs](https://rustup.rs/)
6. **Java** - JDK 17+ (bundled with Android Studio)

### Platform-Specific Requirements

#### Windows
- **Visual Studio Build Tools** (for Rust dependencies)
  ```cmd
  # Install via Visual Studio Installer or standalone
  # Select "C++ build tools" workload
  ```

#### Linux (Ubuntu/Debian)
```bash
# Install build essentials
sudo apt update
sudo apt install build-essential pkg-config

# Install additional dependencies
sudo apt install libc6-dev-i386
```

#### macOS
```bash
# Install Xcode command line tools
xcode-select --install

# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

## Environment Setup

### 1. Install Android Studio and SDK

1. Download and install [Android Studio](https://developer.android.com/studio)
2. Open Android Studio and complete the setup wizard
3. Install required SDK components:
   - Go to **Tools > SDK Manager**
   - Install **Android SDK Platform 29+**
   - Install **Android NDK** (version 28.2.13676358 or later)
   - Install **CMake** (version 3.22.1+)

### 2. Set Environment Variables

#### Windows
```cmd
# Add to system environment variables or create setenv.bat:
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%
```

#### Linux/macOS
```bash
# Add to ~/.bashrc, ~/.zshrc, or ~/.profile:
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# For macOS, Android SDK might be in:
export ANDROID_HOME=$HOME/Library/Android/sdk
```

### 3. Install Rust and Android Target

```bash
# Install Rust (all platforms)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env

# Add Android target
rustup target add aarch64-linux-android
```

## Building the Project

### Method 1: Automated Debug Build (Recommended for Development)

The debug build scripts handle the complete pipeline: Rust (release) + C++ + Kotlin (debug) + automatic installation.

#### Windows
```cmd
# Clone the repository
git clone https://github.com/your-org/EcuCamera.git
cd EcuCamera

# Complete debug build with auto-install
build_debug.bat
```

#### Linux/macOS
```bash
# Clone the repository
git clone https://github.com/your-org/EcuCamera.git
cd EcuCamera

# Make scripts executable and run complete debug build
chmod +x build_debug.sh gradlew
./build_debug.sh
```

### Method 2: Manual Component Build

#### Windows
```cmd
# Build Rust components only
build-rust.bat

# Build Android project only
gradlew.bat assembleDebug
```

#### Linux/macOS
```bash
# Build Rust components only
./build-rust.sh

# Build Android project only
./gradlew assembleDebug
```

### Method 3: Manual Build Steps

#### Step 1: Build Rust Library

**Windows:**
```cmd
cd app\src\main\rust
set CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=%ANDROID_HOME%\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android29-clang.cmd
cargo build --release --target aarch64-linux-android

# Copy library to jniLibs
mkdir ..\jniLibs\arm64-v8a
copy target\aarch64-linux-android\release\libecucamera_engine.so ..\jniLibs\arm64-v8a\
cd ..\..\..\..
```

**Linux:**
```bash
cd app/src/main/rust
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang
cargo build --release --target aarch64-linux-android

# Copy library to jniLibs
mkdir -p ../jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libecucamera_engine.so ../jniLibs/arm64-v8a/
cd ../../../..
```

**macOS:**
```bash
cd app/src/main/rust
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android29-clang
cargo build --release --target aarch64-linux-android

# Copy library to jniLibs
mkdir -p ../jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libecucamera_engine.so ../jniLibs/arm64-v8a/
cd ../../../..
```

#### Step 2: Build Android Project

**All Platforms:**
```bash
# Using Gradle wrapper (recommended)
./gradlew assembleDebug    # Linux/macOS
gradlew.bat assembleDebug  # Windows

# Or using system Gradle (if installed)
gradle assembleDebug
```

## Project Structure
Work In Progress

## Development Workflow

### Debug Build Features

The debug build scripts (`build_debug.bat` / `build_debug.sh`) provide a complete development pipeline:

**Automated Pipeline:**
1. **Environment Verification** - Check all prerequisites
2. **Rust Build** - Compile engine in release mode (optimized)
3. **Android Build** - Compile C++ bridge and Kotlin app in debug mode
4. **Device Detection** - Automatically detect USB debugging devices
5. **Installation** - Install APK and launch app if device connected

**Key Features:**
- **Auto-detection** of Android SDK and NDK paths
- **Cross-compilation** setup for Rust → Android
- **USB debugging detection** with automatic installation
- **Error handling** with clear diagnostic messages
- **Build verification** with file size reporting
- **App launching** after successful installation

**Usage:**
```bash
# Windows
build_debug.bat

# Linux/macOS  
./build_debug.sh
```

### Making Changes

### Making Changes

1. **Any Code Changes (Recommended):**
   ```bash
   # Complete rebuild and install
   ./build_debug.sh        # Linux/macOS
   build_debug.bat         # Windows
   ```

2. **Rust Engine Changes Only:**
   ```bash
   # Rebuild Rust library only
   ./build-rust.sh        # Linux/macOS
   build-rust.bat         # Windows
   
   # Then rebuild Android project
   ./gradlew assembleDebug
   ```

3. **Kotlin/Java Changes Only:**
   ```bash
   # Only rebuild Android project
   ./gradlew assembleDebug
   ```

4. **C++ Bridge Changes:**
   ```bash
   # Clean and rebuild everything
   ./gradlew clean assembleDebug
   ```

### Testing

1. **Install APK:**
   ```bash
   # Connect Android device via USB with debugging enabled
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **View Logs:**
   ```bash
   # Monitor application logs
   adb logcat | grep -E "(EcuBridge|NativeBridge|MainActivity)"
   ```

### Debugging

1. **Native Code Debugging:**
   - Use Android Studio's native debugger
   - Set breakpoints in C++ code
   - Use `__android_log_print` for logging

2. **Rust Code Debugging:**
   - Use `println!` macros (output visible in logcat)
   - Add debug symbols: `cargo build --target aarch64-linux-android`

## Common Issues and Solutions

### Build Failures

**"NDK not found"**
- Ensure NDK is installed via Android Studio SDK Manager
- Verify `ANDROID_HOME` environment variable is set
- Check NDK version matches project configuration

**"Rust target not found"**
```bash
rustup target add aarch64-linux-android
```

**"linker `link.exe` not found" (Windows)**
- Install Visual Studio Build Tools with C++ workload
- Or install Visual Studio Community with C++ development tools

**"Permission denied" (Linux/macOS)**
```bash
chmod +x build-rust.sh
chmod +x gradlew
```

### Runtime Issues

**"UnsatisfiedLinkError"**
- Ensure Rust library was built and copied to `jniLibs/arm64-v8a/`
- Check that device architecture matches (arm64-v8a)
- Verify JNI function signatures match between Rust and Kotlin

**"Native method not found"**
- Ensure Rust function names match JNI naming convention
- Check that library is properly loaded in `NativeBridge.kt`

## Architecture Notes

### Multi-Language Communication Flow

```
Android App (Kotlin)
       ↕ JNI
C++ Bridge (ecu-bridge)
       ↕ FFI  
Rust Engine (ecucamera_engine)
```

### Target Architecture
- **Primary:** `arm64-v8a` (64-bit ARM, modern Android devices)
- **Minimum API:** Android 10 (API level 29)
- **Optimization:** Release builds with LTO for maximum performance

### Performance Considerations
- Rust engine handles compute-intensive ECU communication
- C++ bridge provides minimal JNI overhead
- Kotlin manages UI and Android system integration
- Zero-copy data transfer where possible

## Contributing Guidelines

1. **Fork the repository** and create a feature branch
2. **Follow the existing code style** and architecture patterns
3. **Test thoroughly** on physical Android devices
4. **Update documentation** for any API changes
5. **Submit a pull request** with clear description of changes

### Code Style

- **Rust:** Follow `rustfmt` formatting
- **Kotlin:** Follow Android Kotlin style guide
- **C++:** Follow Google C++ style guide
- **Commit messages:** Use conventional commit format

### Pull Request Process

1. Ensure all builds pass on your platform
2. Test on at least one physical Android device
3. Update relevant documentation
4. Add tests for new functionality
5. Request review from maintainers

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.