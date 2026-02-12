#!/bin/bash

echo "========================================"
echo "  EcuCamera App Logcat (Simple)"
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
echo "[INFO] Starting logcat with app package filter..."
echo ""
echo "========================================"
echo "  Press Ctrl+C to stop"
echo "========================================"
echo ""

# Clear logcat buffer first
adb logcat -c

# Start logcat with grep-style filtering for the app package
adb logcat -v time | grep -iE "id.xms.ecucamera|ECU_|NativeBridge|AndroidRuntime"
