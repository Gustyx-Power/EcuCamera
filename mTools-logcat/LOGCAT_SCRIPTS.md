# EcuCamera Logcat Scripts

Collection of batch scripts for viewing Android logs from the EcuCamera app.

## Prerequisites

1. **Android SDK Platform Tools** must be installed
2. **ADB** must be in your system PATH
3. **USB Debugging** enabled on your Android device
4. Device connected via USB

## Available Scripts

### 1. `logcat.bat` - Filtered ECU Logs
**Purpose:** View only EcuCamera-related logs with ECU tags

**Usage:**
```cmd
logcat.bat
```

**Shows:**
- ECU_MAIN - Main activity logs
- ECU_ENGINE - Camera engine logs
- ECU_DEBUG - Debug frame logs
- ECU_RUST - Rust processing results
- ECU_ERROR - Error logs
- ECU_LENS - Lens switching logs
- NativeBridge - JNI bridge logs
- AndroidRuntime errors
- System errors

**Best for:** Normal development and debugging

---

### 2. `logcat-all.bat` - Full App Logs
**Purpose:** View ALL logs from the EcuCamera app process

**Usage:**
```cmd
logcat-all.bat
```

**Shows:** Everything logged by the app, including system logs

**Best for:** Deep debugging when you need complete context

---

### 3. `logcat-rust.bat` - Rust Processing Logs
**Purpose:** Monitor Rust image processing specifically

**Usage:**
```cmd
logcat-rust.bat
```

**Shows:**
- ECU_DEBUG - Frame arrival notifications
- ECU_RUST - Luminance analysis results
- ECU_ERROR - Processing errors
- NativeBridge - Native library status

**Best for:** Debugging Rust/JNI integration and image processing

---

### 4. `logcat-save.bat` - Save Logs to File
**Purpose:** Capture logs to a timestamped file

**Usage:**
```cmd
logcat-save.bat
```

**Output:** `logs/ecucamera_YYYYMMDD_HHMMSS.log`

**Best for:** 
- Bug reports
- Performance analysis
- Sharing logs with team

---

### 5. `logcat-clear.bat` - Clear Log Buffer
**Purpose:** Clear the logcat buffer before starting fresh

**Usage:**
```cmd
logcat-clear.bat
```

**Best for:** Starting a clean logging session

---

## Quick Start

1. Connect your Android device via USB
2. Enable USB debugging on the device
3. Run any script:
   ```cmd
   logcat.bat
   ```
4. Press `Ctrl+C` to stop logging

## Troubleshooting

### "ADB not found in PATH"
**Solution:** Add Android SDK platform-tools to your PATH:
```
C:\Users\<YourName>\AppData\Local\Android\Sdk\platform-tools
```

### "No Android device connected"
**Solution:** 
1. Check USB cable connection
2. Enable USB debugging in Developer Options
3. Accept USB debugging prompt on device
4. Run `adb devices` to verify connection

### No logs appearing
**Solution:**
1. Make sure the app is running
2. Clear logcat buffer: `logcat-clear.bat`
3. Restart the app
4. Run logcat script again

## Log Tags Reference

| Tag | Purpose | Example |
|-----|---------|---------|
| ECU_MAIN | MainActivity lifecycle | "Camera permission GRANTED" |
| ECU_ENGINE | Camera operations | "Preview Running (Dual Output)" |
| ECU_DEBUG | Frame debugging | "Frame received! Timestamp: 123456" |
| ECU_RUST | Rust analysis | "LUMA: 128 \| RES: 640x480 \| STRIDE: 640" |
| ECU_ERROR | Error tracking | "Frame processing failed" |
| ECU_LENS | Lens switching | "Switching from ID 0 to 2" |
| NativeBridge | JNI status | "Rust library loaded successfully" |

## Advanced Usage

### Filter by specific tag
```cmd
adb logcat -v time ECU_RUST:V *:S
```

### Save specific duration
```cmd
timeout /t 60 /nobreak > nul & taskkill /f /im adb.exe
```

### View saved logs
```cmd
type logs\ecucamera_20260211_143022.log
```

### Search in logs
```cmd
findstr "LUMA" logs\ecucamera_20260211_143022.log
```

## Tips

1. **Clear before testing:** Run `logcat-clear.bat` before each test session
2. **Save important sessions:** Use `logcat-save.bat` for bug reports
3. **Monitor Rust:** Use `logcat-rust.bat` to verify frame processing
4. **Check errors first:** Look for ECU_ERROR tags when debugging crashes

## Integration with Development

### Before building:
```cmd
logcat-clear.bat
```

### During testing:
```cmd
logcat-rust.bat
```

### After crash:
```cmd
logcat-save.bat
```

Then check the saved log file in the `logs/` directory.
