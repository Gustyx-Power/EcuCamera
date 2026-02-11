@echo off
echo ========================================
echo   EcuCamera Clean Build
echo ========================================
echo.

echo [STEP 1] Building Rust library...
call build-rust.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Rust build failed
    pause
    exit /b 1
)

echo.
echo [STEP 2] Cleaning Gradle build...
call gradlew clean
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle clean failed
    pause
    exit /b 1
)

echo.
echo [STEP 3] Building APK...
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] APK build failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo   BUILD SUCCESSFUL
echo ========================================
echo.
echo APK Location: app\build\outputs\apk\debug\app-debug.apk
echo.
pause
