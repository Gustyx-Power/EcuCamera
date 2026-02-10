use std::ffi::CString;
use std::os::raw::{c_char, c_void};

// Basic JNI types
pub type jstring = *mut c_void;

#[repr(C)]
pub struct JNIEnv {
    _private: [u8; 0],
}

#[repr(C)]
pub struct JClass {
    _private: [u8; 0],
}

// JNI NewStringUTF function type
type NewStringUTFFn = unsafe extern "C" fn(*mut JNIEnv, *const c_char) -> jstring;

// Get NewStringUTF function from JNI environment vtable
unsafe fn get_new_string_utf(env: *mut JNIEnv) -> NewStringUTFFn {
    let vtable = *(env as *const *const *const c_void);
    let func_ptr = *((vtable as *const *const c_void).offset(167)); // NewStringUTF is at index 167
    std::mem::transmute(func_ptr)
}

// Helper to create Java string from Rust string
unsafe fn create_java_string(env: *mut JNIEnv, s: &str) -> jstring {
    let c_string = CString::new(s).unwrap_or_else(|_| CString::new("Error creating string").unwrap());
    let new_string_utf = get_new_string_utf(env);
    new_string_utf(env, c_string.as_ptr())
}

/// # Safety
/// This function is called from Java via JNI.
#[no_mangle]
pub unsafe extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(
    env: *mut JNIEnv,
    _class: *mut JClass,
) -> jstring {
    create_java_string(env, "ECU Engine: Rust V8 Connected [Optimized]")
}

/// # Safety
/// This function is called from Java via JNI.
#[no_mangle]
pub unsafe extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(
    env: *mut JNIEnv,
    _class: *mut JClass,
) -> jstring {
    create_java_string(env, "Rust Engine: Ready for ECU Communication")
}

/// # Safety
/// This function is called from Java via JNI.
#[no_mangle]
pub unsafe extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(
    env: *mut JNIEnv,
    _class: *mut JClass,
) -> jstring {
    create_java_string(env, "ECU Engine initialized successfully")
}