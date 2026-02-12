#!/bin/bash

echo "========================================"
echo "  EcuCamera Logcat Saver"
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

# Create logs directory if it doesn't exist
mkdir -p logs

# Generate timestamp for filename
timestamp=$(date +"%Y%m%d_%H%M%S")
logfile="logs/ecucamera_${timestamp}.log"

echo "[INFO] Device connected"
echo "[INFO] Saving logs to: $logfile"
echo "[INFO] Press Ctrl+C to stop logging"
echo ""

# Clear logcat buffer first
adb logcat -c

# Start logcat and save to file
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
    '*:S' > "$logfile"

echo ""
echo "[SUCCESS] Logs saved to: $logfile"