plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "2.3.5"
}

android {
    namespace = "id.xms.ecucamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.xms.ecucamera"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-29"
                )
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildFeatures {
        compose = true
    }
    
   compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        lint.disable.add("NullSafeMutableLiveData")
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Configure NDK version (auto-detect or use specific version)
    ndkVersion = "28.2.13676358"
    
}

// Simple approach: Let users build Rust manually first
// Run: build-rust.bat before running gradlew assembleDebug

dependencies {
    implementation(libs.androidx.core.ktx)
    
    // AppCompat for theme support
    implementation("androidx.appcompat:appcompat:1.7.0")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    
    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Activity Compose
    implementation("androidx.activity:activity-compose:1.12.3")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.7")
    
    // Coroutines for camera engine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
