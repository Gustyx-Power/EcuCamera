#!/bin/bash

clear
echo ""
echo "  ███████╗ ██████╗██╗   ██╗     ███████╗███╗   ██╗ ██████╗ ██╗███╗   ██╗███████╗"
echo "  ██╔════╝██╔════╝██║   ██║     ██╔════╝████╗  ██║██╔════╝ ██║████╗  ██║██╔════╝"
echo "  █████╗  ██║     ██║   ██║     █████╗  ██╔██╗ ██║██║  ███╗██║██╔██╗ ██║█████╗  "
echo "  ██╔══╝  ██║     ██║   ██║     ██╔══╝  ██║╚██╗██║██║   ██║██║██║╚██╗██║██╔══╝  "
echo "  ███████╗╚██████╗╚██████╔╝     ███████╗██║ ╚████║╚██████╔╝██║██║ ╚████║███████╗"
echo "  ╚══════╝ ╚═════╝ ╚═════╝      ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝╚═╝  ╚═══╝╚══════╝"
echo ""
echo "  Phase 3: The Silent Engine - Live Monitor"
echo "  =========================================="
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "  ❌ ADB not found!"
    echo ""
    echo "  Please install Android SDK and ensure adb is available:"
    echo "  • Download Android SDK from developer.android.com"
    echo "  • Add platform-tools directory to your PATH"
    echo "  • Or set ANDROID_HOME environment variable"
    echo ""
    echo "  Common SDK locations:"
    echo "  • \$HOME/Android/Sdk/platform-tools/"
    echo "  • \$HOME/Library/Android/sdk/platform-tools/ (macOS)"
    echo ""
    exit 1
fi

echo "  ✅ ADB Found: $(which adb)"
echo ""
echo "  Watching for key events:"
echo "  • Camera discovery and hardware probe"
echo "  • Camera opening and state changes"
echo "  • Engine lifecycle events"
echo ""
echo "  Press Ctrl+C to stop monitoring"
echo ""

adb logcat -c
adb logcat ECU_MAIN:D ECU_ENGINE:D ECU_PROBE:D '*:S'