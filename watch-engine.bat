@echo off
title EcuCamera Engine Monitor
cls
echo.
echo  ███████╗ ██████╗██╗   ██╗     ███████╗███╗   ██╗ ██████╗ ██╗███╗   ██╗███████╗
echo  ██╔════╝██╔════╝██║   ██║     ██╔════╝████╗  ██║██╔════╝ ██║████╗  ██║██╔════╝
echo  █████╗  ██║     ██║   ██║     █████╗  ██╔██╗ ██║██║  ███╗██║██╔██╗ ██║█████╗  
echo  ██╔══╝  ██║     ██║   ██║     ██╔══╝  ██║╚██╗██║██║   ██║██║██║╚██╗██║██╔══╝  
echo  ███████╗╚██████╗╚██████╔╝     ███████╗██║ ╚████║╚██████╔╝██║██║ ╚████║███████╗
echo  ╚══════╝ ╚═════╝ ╚═════╝      ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝╚═╝  ╚═══╝╚══════╝
echo.
echo  Phase 3: The Silent Engine - Live Monitor
echo  ==========================================
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
        echo  ❌ ADB not found!
        echo.
        echo  Please install Android SDK and ensure adb is available:
        echo  • Download Android SDK from developer.android.com
        echo  • Add platform-tools directory to your PATH
        echo  • Or set ANDROID_HOME environment variable
        echo.
        echo  Common SDK locations:
        echo  • %LOCALAPPDATA%\Android\Sdk\platform-tools\
        echo  • C:\Android\Sdk\platform-tools\
        echo.
        pause
        exit /b 1
    )
)

echo  ✅ ADB Found: %ADB_PATH%
echo.
echo  Watching for key events:
echo  • Camera discovery and hardware probe
echo  • Camera opening and state changes  
echo  • Engine lifecycle events
echo.
echo  Press Ctrl+C to stop monitoring
echo.

"%ADB_PATH%" logcat -c
"%ADB_PATH%" logcat ECU_MAIN:D ECU_ENGINE:D ECU_PROBE:D *:S