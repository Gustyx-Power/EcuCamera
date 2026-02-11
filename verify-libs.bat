@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   EcuCamera Library Verification
echo ========================================
echo.

set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
set EXTRACT_DIR=build\apk-extract

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found: %APK_PATH%
    echo Please build the APK first using: gradlew assembleDebug
    pause
    exit /b 1
)

echo [INFO] Extracting APK...
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
mkdir "%EXTRACT_DIR%"

REM Extract APK (it's just a ZIP file)
powershell -command "Expand-Archive -Path '%APK_PATH%' -DestinationPath '%EXTRACT_DIR%' -Force"

echo.
echo [INFO] Checking native libraries...
echo.

set LIB_DIR=%EXTRACT_DIR%\lib\arm64-v8a

if not exist "%LIB_DIR%" (
    echo [ERROR] arm64-v8a libraries not found
    pause
    exit /b 1
)

echo Libraries found in APK:
echo ----------------------------------------
dir /b "%LIB_DIR%\*.so"
echo ----------------------------------------
echo.

REM Check if both libraries exist
if exist "%LIB_DIR%\libecucamera_engine.so" (
    echo [OK] libecucamera_engine.so found
) else (
    echo [ERROR] libecucamera_engine.so MISSING
)

if exist "%LIB_DIR%\libecu-bridge.so" (
    echo [OK] libecu-bridge.so found
) else (
    echo [ERROR] libecu-bridge.so MISSING
)

echo.
echo [INFO] Checking library sizes...
echo ----------------------------------------
for %%f in ("%LIB_DIR%\*.so") do (
    echo %%~nxf: %%~zf bytes
)
echo ----------------------------------------
echo.

REM Check if readelf is available (from NDK)
where readelf >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Checking library dependencies with readelf...
    echo.
    echo Dependencies of libecu-bridge.so:
    echo ----------------------------------------
    readelf -d "%LIB_DIR%\libecu-bridge.so" | findstr "NEEDED"
    echo ----------------------------------------
    echo.
    echo NOTE: Should show "libecucamera_engine.so" without absolute paths
) else (
    echo [WARNING] readelf not found in PATH
    echo Cannot verify library dependencies
    echo Install NDK and add to PATH to enable this check
)

echo.
echo [SUCCESS] Verification complete
echo.
pause
