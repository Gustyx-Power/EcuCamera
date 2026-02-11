/// Calculate luminance histogram from image data
/// Uses step-by-4 sampling for performance optimization
pub fn calculate_histogram(data: &[u8], stride: usize, width: usize, height: usize) -> String {
    let mut histogram = [0u32; 256];
    
    for row in (0..height).step_by(4) {
        let row_start = row * stride;
        let row_end = row_start + width;
        
        if row_end > data.len() {
            break;
        }
        
        for col in (0..width).step_by(4) {
            let pixel_index = row_start + col;
            if pixel_index < data.len() {
                let pixel_value = data[pixel_index];
                histogram[pixel_value as usize] += 1;
            }
        }
    }
    
    let csv_values: Vec<String> = histogram.iter().map(|&count| count.to_string()).collect();
    csv_values.join(",")
}
