@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   EcuCamera Logcat Saver
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

REM Create logs directory if it doesn't exist
if not exist "logs" mkdir logs

REM Generate timestamp for filename
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set timestamp=%datetime:~0,8%_%datetime:~8,6%
set logfile=logs\ecucamera_%timestamp%.log

echo [INFO] Device connected
echo [INFO] Saving logs to: %logfile%
echo [INFO] Press Ctrl+C to stop logging
echo.

REM Clear logcat buffer first
adb logcat -c

REM Start logcat and save to file
adb logcat -v time ^
    ECU_MAIN:V ^
    ECU_ENGINE:V ^
    ECU_DEBUG:V ^
    ECU_RUST:V ^
    ECU_ERROR:E ^
    ECU_LENS:V ^
    NativeBridge:V ^
    AndroidRuntime:E ^
    System.err:E ^
    *:S > "%logfile%"

echo.
echo [SUCCESS] Logs saved to: %logfile%
pause
