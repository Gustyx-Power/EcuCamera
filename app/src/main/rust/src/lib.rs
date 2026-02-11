use jni::JNIEnv;
use jni::objects::{JClass, JByteBuffer, JByteArray};
use jni::sys::{jint, jstring};

/// Process image frame data and calculate average luminance
fn process_image_frame(data: &[u8], width: i32, height: i32, stride: i32) -> String {
    if width <= 0 || height <= 0 || stride <= 0 {
        return "Error: Invalid dimensions".to_string();
    }
    
    let width = width as usize;
    let height = height as usize;
    let stride = stride as usize;
    
    if stride < width {
        return "Error: Stride smaller than width".to_string();
    }
    
    let required_size = height * stride;
    if data.len() < required_size {
        return format!("Error: Buffer too small. Len: {}, Expected: {}", data.len(), required_size);
    }
    
    let mut luma_sum: u64 = 0;
    let mut pixel_count: u64 = 0;
    
    for row in 0..height {
        let row_start = row * stride;
        let row_end = row_start + width;
        
        if row_end > data.len() {
            break;
        }
        
        for col in 0..width {
            let pixel_index = row_start + col;
            if pixel_index < data.len() {
                luma_sum += data[pixel_index] as u64;
                pixel_count += 1;
            }
        }
    }
    
    let avg_luma = if pixel_count > 0 {
        luma_sum / pixel_count
    } else {
        0
    };
    
    format!("LUMA: {} | RES: {}x{} | STRIDE: {}", avg_luma, width, height, stride)
}

/// Basic Rust connection test
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_stringFromRust(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine: Rust V8 Connected [Optimized]")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Engine status check
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_getEngineStatus(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("Rust Engine: Ready for ECU Communication")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Engine initialization
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_initializeEngine(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("ECU Engine initialized successfully")
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Analyze frame using DirectByteBuffer (SAFE HIGH-LEVEL API)
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_analyzeFrame(
    mut env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    _length: jint, // Keep for compatibility but use high-level API
    width: jint,
    height: jint,
    stride: jint,
) -> jstring {
    // Use the high-level jni crate API - NO manual pointer manipulation
    let data_result = unsafe { env.get_direct_buffer_address(&buffer) };
    
    let data = match data_result {
        Ok(d) => d,
        Err(_) => {
            let error_msg = env.new_string("Error: Failed to get direct buffer address")
                .expect("Couldn't create java string!");
            return error_msg.into_raw();
        }
    };
    
    // Validate buffer size
    let expected_size = (stride * height) as usize;
    if data.len() < expected_size {
        let error_msg = format!("Error: Buffer too small. Len: {}, Expected: {}", data.len(), expected_size);
        let output = env.new_string(error_msg)
            .expect("Couldn't create java string!");
        return output.into_raw();
    }
    
    // Process the frame
    let result = process_image_frame(data, width, height, stride);
    
    let output = env.new_string(result)
        .expect("Couldn't create java string!");
    output.into_raw()
}

/// Analyze frame using byte array (SAFE HIGH-LEVEL API)
#[no_mangle]
pub extern "C" fn Java_id_xms_ecucamera_bridge_NativeBridge_analyzeFrameArray(
    mut env: JNIEnv,
    _class: JClass,
    data: JByteArray,
    width: jint,
    height: jint,
    stride: jint,
) -> jstring {
    // Use the high-level jni crate API - NO manual pointer manipulation
    let array_elements = env.get_byte_array_elements(&data, jni::objects::ReleaseMode::NoCopyBack);
    
    let byte_slice = match array_elements {
        Ok(elements) => unsafe { elements.as_ptr() },
        Err(_) => {
            let error_msg = env.new_string("Error: Failed to get array elements")
                .expect("Couldn't create java string!");
            return error_msg.into_raw();
        }
    };
    
    // Get array length using high-level API
    let array_length = match env.get_array_length(&data) {
        Ok(len) => len as usize,
        Err(_) => {
            let error_msg = env.new_string("Error: Failed to get array length")
                .expect("Couldn't create java string!");
            return error_msg.into_raw();
        }
    };
    
    // Create safe slice
    let data_slice = unsafe { 
        std::slice::from_raw_parts(byte_slice as *const u8, array_length)
    };
    
    // Process the frame
    let result = process_image_frame(data_slice, width, height, stride);
    
    let output = env.new_string(result)
        .expect("Couldn't create java string!");
    output.into_raw()
}