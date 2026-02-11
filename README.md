# EcuCamera

A high-performance Android application for ECU (Enhance Camera Utility) communication and camera integration, built with a multi-language architecture combining Kotlin, C++, and Rust.

## Features

- **Multi-Language Architecture**: Kotlin UI + C++ Bridge + Rust Engine
- **High Performance**: Optimized for modern ARM64 Android devices
- **ECU Communication**: Real-time automotive diagnostic protocols
- **Camera Integration**: Advanced image processing capabilities
- **Cross-Platform Build**: Support for Windows, Linux, and macOS development

## Quick Start

### Prerequisites
- Android Studio with NDK
- Rust toolchain
- Git

### Build Instructions

**Complete Debug Build (Recommended for Development):**
```cmd
# Windows
git clone https://github.com/Gustyx-Power/EcuCamera.git
cd EcuCamera
build_debug.bat

# Linux/macOS
git clone https://github.com/Gustyx-Power/EcuCamera.git
cd EcuCamera
chmod +x build_debug.sh gradlew
./build_debug.sh
```

**Manual Component Build:**
```cmd
# Windows
build-rust.bat
gradlew.bat assembleDebug

# Linux/macOS
./build-rust.sh
./gradlew assembleDebug
```

### Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
Android App (Kotlin) ←→ C++ Bridge ←→ Rust Engine
     │                      │              │
   UI Logic            JNI Interface   ECU Protocols
  Navigation           System Calls    Performance
   Compose              Logging        Algorithms
```

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed build instructions, development workflow, and contribution guidelines.

## Project Status

- ✅ **Phase 1**: Project structure and architecture design
- ✅ **Phase 2**: Build system configuration and JNI bridge
- 🚧 **Phase 3**: ECU communication protocols (in progress)
- 📋 **Phase 4**: Camera integration and processing
- 📋 **Phase 5**: User interface and controls

## Requirements

- **Android**: API level 29+ (Android 10+)
- **Architecture**: ARM64-v8a (64-bit ARM)
- **Memory**: 4GB+ RAM recommended
- **Storage**: 100MB+ available space

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

We welcome contributions! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to submit pull requests, report issues, and contribute to the project.
