/// Calculate average luminance from image data
pub fn calculate_average_luma(data: &[u8], stride: usize, width: usize, height: usize) -> u32 {
    let mut luma_sum: u64 = 0;
    let mut pixel_count: u64 = 0;
    
    // Process frame row by row, respecting stride
    for row in 0..height {
        let row_start = row * stride;
        let row_end = row_start + width;
        
        if row_end > data.len() {
            break;
        }
        
        // Process pixels in this row
        for col in 0..width {
            let pixel_index = row_start + col;
            if pixel_index < data.len() {
                luma_sum += data[pixel_index] as u64;
                pixel_count += 1;
            }
        }
    }
    
    if pixel_count > 0 {
        (luma_sum / pixel_count) as u32
    } else {
        0
    }
}

/// Calculate luminance histogram from image data
pub fn calculate_histogram(data: &[u8], stride: usize, width: usize, height: usize) -> String {
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