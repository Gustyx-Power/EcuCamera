@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   EcuCamera Rust Processing Logs
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ADB not found in PATH
    echo Please ensure Android SDK platform-tools is installed and in PATH
    pause
    exit /b 1
)

REM Check if device is connected
adb devices | findstr "device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No Android device connected
    echo Please connect your device and enable USB debugging
    pause
    exit /b 1
)

echo [INFO] Device connected
echo [INFO] Monitoring Rust image processing logs...
echo.
echo ========================================
echo   Press Ctrl+C to stop
echo ========================================
echo.

REM Clear logcat buffer first
adb logcat -c

REM Start logcat with Rust-specific filters
adb logcat -v time ^
    ECU_DEBUG:V ^
    ECU_RUST:V ^
    ECU_ERROR:E ^
    NativeBridge:V ^
    *:S

pause
