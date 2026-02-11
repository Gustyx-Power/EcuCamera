@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   EcuCamera Full Logcat Viewer
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
echo [INFO] Getting app PID...

REM Get the PID of the app
for /f "tokens=*" %%i in ('adb shell pidof -s id.xms.ecucamera 2^>nul') do set APP_PID=%%i

if "%APP_PID%"=="" (
    echo [WARNING] App is not running
    echo [INFO] Starting full logcat with package filter...
    echo.
    echo ========================================
    echo   Press Ctrl+C to stop
    echo ========================================
    echo.
    
    REM Clear logcat buffer first
    adb logcat -c
    
    REM Show all logs, will include app logs when it starts
    adb logcat -v time | findstr /i "ecucamera ECU_ NativeBridge"
) else (
    echo [INFO] App PID: %APP_PID%
    echo [INFO] Starting full logcat for EcuCamera app...
    echo.
    echo ========================================
    echo   Press Ctrl+C to stop
    echo ========================================
    echo.
    
    REM Clear logcat buffer first
    adb logcat -c
    
    REM Start logcat filtered by PID
    adb logcat -v time --pid=%APP_PID%
)

pause
