#!/bin/bash

echo "EcuCamera Phase 3 - Filtered Engine Monitoring"
echo "==============================================="
echo ""
echo "Looking for key engine events:"
echo "- Camera discovery and capabilities"
echo "- Camera opening success"
echo "- Hardware probe results"
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found! Please install Android SDK and add platform-tools to PATH"
    exit 1
fi

echo "✅ Using ADB: $(which adb)"
echo "Press Ctrl+C to stop..."
echo ""

# Clear logcat buffer
adb logcat -c

# Monitor ECU logs and filter for key messages
adb logcat -s ECU_MAIN:D ECU_ENGINE:D ECU_PROBE:D | grep -E "Found Camera|Max Zoom|Opened Successfully|Lens Facing|Hardware Level|Camera 0|Camera 1|Camera 2|OPENED|CONFIGURED|ERROR"
