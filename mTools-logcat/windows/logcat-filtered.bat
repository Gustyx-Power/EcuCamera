@echo off
echo EcuCamera Phase 3 - Filtered Engine Monitoring
echo ===============================================
echo.
echo Looking for key engine events:
echo - Camera discovery and capabilities
echo - Camera opening success
echo - Hardware probe results
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
        echo ❌ ADB not found! Please install Android SDK and add platform-tools to PATH
        pause
        exit /b 1
    )
)

echo ✅ Using ADB: %ADB_PATH%
echo Press Ctrl+C to stop...
echo.

REM Clear logcat buffer
"%ADB_PATH%" logcat -c

REM Monitor ECU logs and filter for key messages
"%ADB_PATH%" logcat -s ECU_MAIN:D ECU_ENGINE:D ECU_PROBE:D | findstr /C:"Found Camera" /C:"Max Zoom" /C:"Opened Successfully" /C:"Lens Facing" /C:"Hardware Level" /C:"Camera 0" /C:"Camera 1" /C:"Camera 2" /C:"OPENED" /C:"CONFIGURED" /C:"ERROR"