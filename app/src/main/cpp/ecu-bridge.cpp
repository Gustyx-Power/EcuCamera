#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "EcuBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations for Rust functions
extern "C" {
    jstring Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(JNIEnv* env, jclass clazz);
    jstring Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(JNIEnv* env, jclass clazz);
    jstring Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(JNIEnv* env, jclass clazz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(JNIEnv *env, jclass clazz) {
    LOGI("C++ Bridge: Calling Rust stringFromRust function");
    
    // This will be handled directly by Rust
    // The Rust function with the same JNI signature will be called instead
    return env->NewStringUTF("C++ Bridge: Rust function should override this");
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(JNIEnv *env, jclass clazz) {
    LOGI("C++ Bridge: Calling Rust getEngineStatus function");
    
    // This will be handled directly by Rust
    return env->NewStringUTF("C++ Bridge: Rust function should override this");
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(JNIEnv *env, jclass clazz) {
    LOGI("C++ Bridge: Calling Rust initializeEngine function");
    
    // This will be handled directly by Rust
    return env->NewStringUTF("C++ Bridge: Rust function should override this");
}

// Additional C++ bridge functions can be added here
extern "C" JNIEXPORT jstring JNICALL
Java_id_xms_ecucamera_bridge_NativeBridge_getCppBridgeInfo(JNIEnv *env, jclass clazz) {
    LOGI("C++ Bridge: Providing bridge information");
    return env->NewStringUTF("C++ Bridge: Active and ready for ECU communication");
}
