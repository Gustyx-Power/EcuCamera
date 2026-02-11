use jni::JNIEnv;
use jni::objects::{JClass, JByteBuffer};
use jni::sys::{jint, jstring};

mod processors;
use processors::{luma, focus};

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

fn process_image_frame(data: &[u8], width: i32, height: i32, stride: i32) -> String {
    match validate_image_params(width, height, stride, data.len()) {
        Ok((w, h, s)) => luma::calculate_histogram(data, s, w, h),
        Err(error) => error,
    }
}

fn process_focus_peaking(data: &[u8], width: i32, height: i32, stride: i32) -> String {
    match validate_image_params(width, height, stride, data.len()) {
        Ok((w, h, s)) => focus::detect_peaks(data, s, w, h),
        Err(error) => error,
    }
}

#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine: Rust V8 Connected [Optimized]")
        .expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("Rust Engine: Ready for ECU Communication")
        .expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine initialized successfully")
        .expect("Couldn't create java string!");
    output.into_raw()
}

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
    if length < 0 {
        return env.new_string("Error: Invalid buffer length").unwrap().into_raw();
    }
    
    let buf_ptr: *mut u8 = match env.get_direct_buffer_address(&buffer) {
        Ok(ptr) => ptr,
        Err(_) => {
            return env.new_string("Error: Failed to get direct buffer address").unwrap().into_raw();
        }
    };
    
    let data: &[u8] = unsafe {
        std::slice::from_raw_parts(buf_ptr, length as usize)
    };
    
    let expected_size = (stride * height) as usize;
    if data.len() < expected_size {
        let error_msg = format!("Error: Buffer too small. Len: {}, Expected: {}", data.len(), expected_size);
        return env.new_string(error_msg).unwrap().into_raw();
    }
    
    let result = process_image_frame(data, width, height, stride);
    
    let output = env.new_string(result).expect("Couldn't create java string!");
    output.into_raw()
}

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
    if length < 0 {
        return env.new_string("Error: Invalid buffer length").unwrap().into_raw();
    }
    
    let buf_ptr: *mut u8 = match env.get_direct_buffer_address(&buffer) {
        Ok(ptr) => ptr,
        Err(_) => {
            return env.new_string("Error: Failed to get direct buffer address").unwrap().into_raw();
        }
    };
    
    let data: &[u8] = unsafe {
        std::slice::from_raw_parts(buf_ptr, length as usize)
    };
    
    let expected_size = (stride * height) as usize;
    if data.len() < expected_size {
        let error_msg = format!("Error: Buffer too small. Len: {}, Expected: {}", data.len(), expected_size);
        return env.new_string(error_msg).unwrap().into_raw();
    }
    
    let result = process_focus_peaking(data, width, height, stride);
    
    let output = env.new_string(result).expect("Couldn't create java string!");
    output.into_raw()
}
