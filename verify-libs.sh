#!/bin/bash

echo "========================================"
echo "  EcuCamera Library Verification"
echo "========================================"
echo ""

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
EXTRACT_DIR="build/apk-extract"

if [ ! -f "$APK_PATH" ]; then
    echo "[ERROR] APK not found: $APK_PATH"
    echo "Please build the APK first using: ./gradlew assembleDebug"
    exit 1
fi

echo "[INFO] Extracting APK..."
if [ -d "$EXTRACT_DIR" ]; then
    rm -rf "$EXTRACT_DIR"
fi
mkdir -p "$EXTRACT_DIR"

# Extract APK (it's just a ZIP file)
unzip -q "$APK_PATH" -d "$EXTRACT_DIR"

echo ""
echo "[INFO] Checking native libraries..."
echo ""

LIB_DIR="$EXTRACT_DIR/lib/arm64-v8a"

if [ ! -d "$LIB_DIR" ]; then
    echo "[ERROR] arm64-v8a libraries not found"
    exit 1
fi

echo "Libraries found in APK:"
echo "----------------------------------------"
ls -1 "$LIB_DIR"/*.so 2>/dev/null | xargs -n1 basename
echo "----------------------------------------"
echo ""

# Check if both libraries exist
if [ -f "$LIB_DIR/libecucamera_engine.so" ]; then
    echo "[OK] libecucamera_engine.so found"
else
    echo "[ERROR] libecucamera_engine.so MISSING"
fi

if [ -f "$LIB_DIR/libecu-bridge.so" ]; then
    echo "[OK] libecu-bridge.so found"
else
    echo "[ERROR] libecu-bridge.so MISSING"
fi

echo ""
echo "[INFO] Checking library sizes..."
echo "----------------------------------------"
for lib in "$LIB_DIR"/*.so; do
    if [ -f "$lib" ]; then
        size=$(stat -c%s "$lib" 2>/dev/null || stat -f%z "$lib" 2>/dev/null)
        echo "$(basename "$lib"): $size bytes"
    fi
done
echo "----------------------------------------"
echo ""

# Check if readelf is available (from NDK)
if command -v readelf &> /dev/null; then
    echo "[INFO] Checking library dependencies with readelf..."
    echo ""
    echo "Dependencies of libecu-bridge.so:"
    echo "----------------------------------------"
    readelf -d "$LIB_DIR/libecu-bridge.so" | grep "NEEDED"
    echo "----------------------------------------"
    echo ""
    echo "NOTE: Should show \"libecucamera_engine.so\" without absolute paths"
else
    echo "[WARNING] readelf not found in PATH"
    echo "Cannot verify library dependencies"
    echo "Install NDK and add to PATH to enable this check"
fi

echo ""
echo "[SUCCESS] Verification complete"
echo ""