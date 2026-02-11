use jni::JNIEnv;
use jni::objects::{JClass, JByteBuffer};
use jni::sys::{jint, jstring};

mod processors;
use processors::{luma, focus};

/// Validate image dimensions and buffer size
fn validate_image_params(width: i32, height: i32, stride: i32, buffer_len: usize) -> Result<(usize, usize, usize), String> {
    if width <= 0 || height <= 0 || stride <= 0 {
        return Err("Error: Invalid dimensions".to_string());
    }
    
    let width = width as usize;
    let height = height as usize;
    let stride = stride as usize;
    
    if stride < width {
        return Err("Error: Stride smaller than width".to_string());
    }
    
    let required_size = height * stride;
    if buffer_len < required_size {
        return Err(format!("Error: Buffer too small. Len: {}, Expected: {}", buffer_len, required_size));
    }
    
    Ok((width, height, stride))
}

/// Process image frame data and calculate luminance histogram
fn process_image_frame(data: &[u8], width: i32, height: i32, stride: i32) -> String {
    match validate_image_params(width, height, stride, data.len()) {
        Ok((w, h, s)) => luma::calculate_histogram(data, s, w, h),
        Err(error) => error,
    }
}

/// Process image frame for focus peaking using simplified Sobel edge detection
fn process_focus_peaking(data: &[u8], width: i32, height: i32, stride: i32) -> String {
    match validate_image_params(width, height, stride, data.len()) {
        Ok((w, h, s)) => focus::detect_peaks(data, s, w, h),
        Err(error) => error,
    }
}

/// Basic Rust connection test
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine: Rust V8 Connected [Optimized]")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Engine status check
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("Rust Engine: Ready for ECU Communication")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Engine initialization
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine initialized successfully")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Analyze frame using DirectByteBuffer - CLEAN IMPLEMENTATION
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_analyzeFrame(
    mut env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    length: jint,
    width: jint,
    height: jint,
    stride: jint,
) -> jstring {
    // Validate length parameter first
    if length < 0 {
        return env.new_string("Error: Invalid buffer length").unwrap().into_raw();
    }
    
    // Get raw pointer from DirectByteBuffer using high-level API
    let buf_ptr: *mut u8 = match env.get_direct_buffer_address(&buffer) {
        Ok(ptr) => ptr,
        Err(_) => {
            return env.new_string("Error: Failed to get direct buffer address").unwrap().into_raw();
        }
    };
    
    // SAFETY: Create a slice from the raw pointer and validated length
    // This is safe because:
    // 1. We validated length is non-negative
    // 2. The pointer comes from a valid DirectByteBuffer
    // 3. The JVM guarantees the buffer remains valid during this call
    let data: &[u8] = unsafe {
        std::slice::from_raw_parts(buf_ptr, length as usize)
    };
    
    // Validate buffer size against expected frame dimensions
    let expected_size = (stride * height) as usize;
    if data.len() < expected_size {
        let error_msg = format!(
            "Error: Buffer too small. Len: {}, Expected: {}", 
            data.len(), 
            expected_size
        );
        return env.new_string(error_msg).unwrap().into_raw();
    }
    
    // Process the frame and calculate histogram
    let result = process_image_frame(data, width, height, stride);
    
    // Return result as Java string
    let output = env.new_string(result).expect("Couldn't create java string!");
    output.into_raw()
}

/// Analyze frame for focus peaking using DirectByteBuffer
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_analyzeFocusPeaking(
    mut env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    length: jint,
    width: jint,
    height: jint,
    stride: jint,
) -> jstring {
    // Validate length parameter first
    if length < 0 {
        return env.new_string("Error: Invalid buffer length").unwrap().into_raw();
    }
    
    // Get raw pointer from DirectByteBuffer using high-level API
    let buf_ptr: *mut u8 = match env.get_direct_buffer_address(&buffer) {
        Ok(ptr) => ptr,
        Err(_) => {
            return env.new_string("Error: Failed to get direct buffer address").unwrap().into_raw();
        }
    };
    
    // SAFETY: Create a slice from the raw pointer and validated length
    let data: &[u8] = unsafe {
        std::slice::from_raw_parts(buf_ptr, length as usize)
    };
    
    // Validate buffer size against expected frame dimensions
    let expected_size = (stride * height) as usize;
    if data.len() < expected_size {
        let error_msg = format!(
            "Error: Buffer too small. Len: {}, Expected: {}", 
            data.len(), 
            expected_size
        );
        return env.new_string(error_msg).unwrap().into_raw();
    }
    
    // Process the frame for focus peaking
    let result = process_focus_peaking(data, width, height, stride);
    
    // Return result as Java string
    let output = env.new_string(result).expect("Couldn't create java string!");
    output.into_raw()
}