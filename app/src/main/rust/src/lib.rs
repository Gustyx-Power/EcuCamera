use jni::JNIEnv;
use jni::objects::{JClass, JByteBuffer};
use jni::sys::{jint, jstring};

/// Process image frame data and calculate luminance histogram
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
    
    // Initialize histogram counter array (256 bins for 0-255 luminance values)
    let mut histogram = [0u32; 256];
    
    // Process frame row by row, respecting stride
    // Use step of 4 for performance optimization (skip 4 pixels)
    for row in (0..height).step_by(4) {
        let row_start = row * stride;
        let row_end = row_start + width;
        
        if row_end > data.len() {
            break;
        }
        
        // Process pixels in this row with step of 4
        for col in (0..width).step_by(4) {
            let pixel_index = row_start + col;
            if pixel_index < data.len() {
                let pixel_value = data[pixel_index];
                histogram[pixel_value as usize] += 1;
            }
        }
    }
    
    // Convert histogram to CSV string format
    let csv_values: Vec<String> = histogram.iter().map(|&count| count.to_string()).collect();
    csv_values.join(",")
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