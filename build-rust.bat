@echo off
setlocal enabledelayedexpansion

echo Building Rust library for Android...

REM Check if Rust is installed
where cargo >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Rust/Cargo not found. Please install Rust from https://rustup.rs/
    exit /b 1
)

REM Set Android SDK path
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    echo [INFO] Auto-detected ANDROID_HOME: !ANDROID_HOME!
)

REM Check if Android SDK exists
if not exist "!ANDROID_HOME!" (
    echo [ERROR] Android SDK not found at !ANDROID_HOME!
    echo [ERROR] Please install Android Studio and SDK
    exit /b 1
)

echo [INFO] Using Android SDK: !ANDROID_HOME!

REM Find NDK version
set "ANDROID_NDK_ROOT="
for /d %%i in ("!ANDROID_HOME!\ndk\*") do (
    set "ANDROID_NDK_ROOT=%%i"
    goto :found_ndk
)

:found_ndk
if "%ANDROID_NDK_ROOT%"=="" (
    echo [ERROR] NDK not found in !ANDROID_HOME!\ndk\
    echo [ERROR] Please install NDK via Android Studio SDK Manager
    exit /b 1
)

echo [INFO] Using NDK: !ANDROID_NDK_ROOT!

set "TOOLCHAIN_PATH=!ANDROID_NDK_ROOT!\toolchains\llvm\prebuilt\windows-x86_64"
echo [INFO] Using toolchain: !TOOLCHAIN_PATH!

REM Verify toolchain exists
if not exist "!TOOLCHAIN_PATH!\bin\aarch64-linux-android29-clang.cmd" (
    echo [ERROR] NDK toolchain not found at expected location
    echo [ERROR] Expected: !TOOLCHAIN_PATH!\bin\aarch64-linux-android29-clang.cmd
    exit /b 1
)

REM Add Android target for Rust
echo [INFO] Adding Android target for Rust...
rustup target add aarch64-linux-android
echo [INFO] Android target configured

REM Set environment variables for cross-compilation
set "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=!TOOLCHAIN_PATH!\bin\aarch64-linux-android29-clang.cmd"
set "CC_aarch64_linux_android=!TOOLCHAIN_PATH!\bin\aarch64-linux-android29-clang.cmd"
set "AR_aarch64_linux_android=!TOOLCHAIN_PATH!\bin\llvm-ar.exe"

REM Add NDK toolchain to PATH for this session
set "PATH=!TOOLCHAIN_PATH!\bin;%PATH%"

echo [INFO] Cross-compilation environment configured

REM Build the Rust library
echo [INFO] Building Rust library...
cd app\src\main\rust

cargo build --release --target aarch64-linux-android
if %errorlevel% neq 0 (
    echo [ERROR] Rust build failed!
    echo [INFO] Make sure you have the Android target installed: rustup target add aarch64-linux-android
    exit /b 1
)

echo [INFO] Rust build completed successfully!

REM Create jniLibs directory if it doesn't exist
if not exist "..\jniLibs\arm64-v8a" mkdir "..\jniLibs\arm64-v8a"

REM Copy the built library
copy "target\aarch64-linux-android\release\libecucamera_engine.so" "..\jniLibs\arm64-v8a\"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy library
    exit /b 1
)

echo [INFO] Library copied to jniLibs/arm64-v8a/
echo [SUCCESS] ✅ Rust build and setup complete!

cd ..\..\..\..