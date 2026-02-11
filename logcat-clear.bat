@echo off

echo ========================================
echo   Clear Logcat Buffer
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ADB not found in PATH
    pause
    exit /b 1
)

REM Check if device is connected
adb devices | findstr "device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No Android device connected
    pause
    exit /b 1
)

echo [INFO] Clearing logcat buffer...
adb logcat -c

echo [SUCCESS] Logcat buffer cleared
echo.
pause
