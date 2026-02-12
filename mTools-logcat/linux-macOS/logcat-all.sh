#!/bin/bash

echo "========================================"
echo "  EcuCamera Full Logcat Viewer"
echo "========================================"
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "[ERROR] ADB not found in PATH"
    echo "Please ensure Android SDK platform-tools is installed and in PATH"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "[ERROR] No Android device connected"
    echo "Please connect your device and enable USB debugging"
    exit 1
fi

echo "[INFO] Device connected"
echo "[INFO] Getting app PID..."

# Get the PID of the app
APP_PID=$(adb shell pidof -s id.xms.ecucamera 2>/dev/null | tr -d '\r')

if [ -z "$APP_PID" ]; then
    echo "[WARNING] App is not running"
    echo "[INFO] Starting full logcat with package filter..."
    echo ""
    echo "========================================"
    echo "  Press Ctrl+C to stop"
    echo "========================================"
    echo ""
    
    # Clear logcat buffer first
    adb logcat -c
    
    # Show all logs, will include app logs when it starts
    adb logcat -v time | grep -iE "ecucamera|ECU_|NativeBridge"
else
    echo "[INFO] App PID: $APP_PID"
    echo "[INFO] Starting full logcat for EcuCamera app..."
    echo ""
    echo "========================================"
    echo "  Press Ctrl+C to stop"
    echo "========================================"
    echo ""
    
    # Clear logcat buffer first
    adb logcat -c
    
    # Start logcat filtered by PID
    adb logcat -v time --pid="$APP_PID"
fi
