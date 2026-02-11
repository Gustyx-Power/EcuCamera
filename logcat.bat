@echo off
echo EcuCamera Phase 3 - Live Engine Monitoring
echo ==========================================
echo.
echo Watching for:
echo [ECU_PROBE] Found Camera 0 (Back)
echo [ECU_PROBE] Max Zoom: 10.0x  
echo [ECU_ENGINE] Camera 0 Opened Successfully
echo.

REM Try to find adb in common locations
set ADB_PATH=
if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else (
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo ❌ ADB not found! Please:
        echo    1. Install Android SDK
        echo    2. Add platform-tools to PATH, or
        echo    3. Set ANDROID_HOME environment variable
        echo.
        echo Common locations:
        echo    %LOCALAPPDATA%\Android\Sdk\platform-tools\
        echo    C:\Android\Sdk\platform-tools\
        pause
        exit /b 1
    )
)

echo ✅ Using ADB: %ADB_PATH%
echo.
echo Press Ctrl+C to stop monitoring...
echo.

REM Clear logcat buffer first
"%ADB_PATH%" logcat -c

REM Monitor ECU logs with filtering
"%ADB_PATH%" logcat -s ECU_MAIN:D ECU_ENGINE:D ECU_PROBE:D

pause