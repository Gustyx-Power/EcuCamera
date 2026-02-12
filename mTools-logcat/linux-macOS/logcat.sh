#!/bin/bash

echo "========================================"
echo "  EcuCamera Logcat Viewer"
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
echo "[INFO] Starting logcat with ECU filters..."
echo ""
echo "========================================"
echo "  Press Ctrl+C to stop"
echo "========================================"
echo ""

# Clear logcat buffer first
adb logcat -c

# Start logcat with filters for ECU tags
# -v time: Show timestamps
# -s: Silent mode (only show specified tags)
adb logcat -v time \
    ECU_MAIN:V \
    ECU_ENGINE:V \
    ECU_DEBUG:V \
    ECU_RUST:V \
    ECU_ERROR:E \
    ECU_LENS:V \
    NativeBridge:V \
    AndroidRuntime:E \
    System.err:E \
    '*:S'
