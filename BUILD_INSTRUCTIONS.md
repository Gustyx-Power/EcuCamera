# EcuCamera Build Instructions

## Critical Fix: CMake Path Pollution

The CMakeLists.txt has been updated to prevent Windows absolute paths from being baked into the Android shared library. This fixes the `UnsatisfiedLinkError` where the app tries to load Rust library from `C:/Users/...` instead of the Android library path.

## What Changed

### Before (Problematic):
```cmake
add_library(ecucamera_engine SHARED IMPORTED)
set_target_properties(ecucamera_engine PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libecucamera_engine.so
)
target_link_libraries(ecu-bridge ... ecucamera_engine)
```
This caused CMake to embed the absolute Windows path in the `DT_NEEDED` tag.

### After (Fixed):
```cmake
set(RUST_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
link_directories(${RUST_LIB_DIR})
target_link_libraries(ecu-bridge ... -lecucamera_engine)
```
This tells the linker to search for `libecucamera_engine.so` in the library search paths at runtime.

## Build Process

### Option 1: Automated Clean Build (Recommended)

```cmd
clean-build.bat
```

This script:
1. Builds Rust library
2. Cleans Gradle build
3. Builds APK
4. Verifies success

### Option 2: Manual Build

```cmd
# Step 1: Build Rust library
build-rust.bat

# Step 2: Clean Gradle build
gradlew clean

# Step 3: Build APK
gradlew assembleDebug
```

## Verification

After building, verify the libraries are correctly packaged:

```cmd
verify-libs.bat
```

This will:
- Extract the APK
- List all native libraries
- Check library sizes
- Verify dependencies (if readelf is available)

### Expected Output:

```
Libraries found in APK:
----------------------------------------
libc++_shared.so
libecu-bridge.so
libecucamera_engine.so
----------------------------------------

[OK] libecucamera_engine.so found
[OK] libecu-bridge.so found

Dependencies of libecu-bridge.so:
----------------------------------------
NEEDED               libecucamera_engine.so
NEEDED               libandroid.so
NEEDED               liblog.so
NEEDED               libc++_shared.so
----------------------------------------
```

**IMPORTANT:** The `NEEDED` entry should show `libecucamera_engine.so` WITHOUT any path prefix. If you see `C:/Users/...`, the fix didn't work.

## Troubleshooting

### Issue: UnsatisfiedLinkError

**Symptoms:**
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libecucamera_engine.so" 
not found or cannot load library "C:/Users/..."
```

**Solution:**
1. Run `clean-build.bat` to rebuild everything
2. Verify with `verify-libs.bat`
3. Check that both libraries are in the APK
4. Ensure no absolute paths in dependencies

### Issue: Rust library not found in APK

**Solution:**
```cmd
# Rebuild Rust library
build-rust.bat

# Verify it's copied to jniLibs
dir app\src\main\jniLibs\arm64-v8a\libecucamera_engine.so

# Clean and rebuild
gradlew clean assembleDebug
```

### Issue: CMake cache issues

**Solution:**
```cmd
# Delete CMake cache
rmdir /s /q app\.cxx

# Clean build
gradlew clean
gradlew assembleDebug
```

### Issue: Library loading order

The libraries must load in this order:
1. `libecucamera_engine.so` (Rust - loaded first in NativeBridge)
2. `libecu-bridge.so` (C++ - loaded second, depends on Rust)

Check `NativeBridge.kt`:
```kotlin
init {
    System.loadLibrary("ecucamera_engine")  // FIRST
    System.loadLibrary("ecu-bridge")        // SECOND
}
```

## Android Studio Integration

### Clean Project
1. Build → Clean Project
2. File → Invalidate Caches → Invalidate and Restart

### Refresh C++ Projects
1. Build → Refresh Linked C++ Projects
2. This forces CMake to regenerate build files

### Rebuild
1. Build → Rebuild Project

## Deployment

### Install APK
```cmd
# Install to connected device
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### View Logs
```cmd
# View all ECU logs
logcat.bat

# View Rust processing logs
logcat-rust.bat

# Save logs to file
logcat-save.bat
```

## Build Artifacts

After successful build:

```
app/build/outputs/apk/debug/app-debug.apk          # Final APK
app/src/main/jniLibs/arm64-v8a/                    # Native libraries
├── libecucamera_engine.so                          # Rust library
app/.cxx/Debug/*/arm64-v8a/                        # CMake build output
├── libecu-bridge.so                                # C++ bridge
```

## Development Workflow

### Making Changes

1. **Rust changes:**
   ```cmd
   build-rust.bat
   gradlew assembleDebug
   ```

2. **C++ changes:**
   ```cmd
   gradlew clean
   gradlew assembleDebug
   ```

3. **Kotlin changes:**
   ```cmd
   gradlew assembleDebug
   ```

4. **CMake changes:**
   ```cmd
   rmdir /s /q app\.cxx
   gradlew clean
   gradlew assembleDebug
   ```

### Testing

1. Build and install:
   ```cmd
   clean-build.bat
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. Start logging:
   ```cmd
   logcat-rust.bat
   ```

3. Launch app on device

4. Verify logs show:
   ```
   NativeBridge: Rust library loaded successfully
   NativeBridge: Native bridge library loaded successfully
   ECU_DEBUG: Frame received! Timestamp: ...
   ECU_RUST: LUMA: 128 | RES: 640x480 | STRIDE: 640
   ```

## Performance Notes

### Zero-Copy Direct Buffer
The app now uses direct ByteBuffer passing from Kotlin to Rust:
- No memory copying
- No GC pressure
- Direct pointer access in Rust
- Stride-aware processing

### Expected Frame Rate
- 30 FPS camera preview
- Real-time luminance analysis
- Minimal processing overhead

## Common Errors

### Error: "Rust library loaded successfully" but no frame logs

**Cause:** ImageReader not receiving frames

**Solution:** Check that both surfaces are added to capture request:
```kotlin
builder.addTarget(viewFinderSurface)
builder.addTarget(imageReader!!.surface)
```

### Error: Native crash with no logs

**Cause:** Segfault in Rust code

**Solution:** Check stride handling in Rust:
```rust
// Ensure stride is used correctly
for row in 0..height {
    let row_start = row * stride;  // Use stride, not width
    // ...
}
```

### Error: "Buffer too small"

**Cause:** Stride calculation mismatch

**Solution:** Verify buffer size:
```kotlin
val plane = image.planes[0]
val stride = plane.rowStride
val buffer = plane.buffer  // Already correct size
```

## Support

For issues:
1. Run `verify-libs.bat` and check output
2. Run `logcat-save.bat` to capture logs
3. Check `logs/` directory for saved log files
4. Review error messages in logs

## References

- [Android NDK CMake Guide](https://developer.android.com/ndk/guides/cmake)
- [JNI Direct Buffers](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html#GetDirectBufferAddress)
- [Camera2 API](https://developer.android.com/training/camera2)
