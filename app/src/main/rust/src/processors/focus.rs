/// Detect focus peaks using simplified Sobel edge detection
pub fn detect_peaks(data: &[u8], stride: usize, width: usize, height: usize) -> String {
    // Define grid size (10x10 blocks)
    const GRID_SIZE: usize = 10;
    let block_width = width / GRID_SIZE;
    let block_height = height / GRID_SIZE;
    
    if block_width == 0 || block_height == 0 {
        return "Error: Image too small for grid".to_string();
    }
    
    // Edge detection threshold
    const EDGE_THRESHOLD: u8 = 50;
    const MIN_EDGES_PER_BLOCK: usize = 10; // Minimum edges to consider block "in focus"
    
    let mut focus_blocks = Vec::new();
    
    // Process each grid block
    for grid_y in 0..GRID_SIZE {
        for grid_x in 0..GRID_SIZE {
            let start_x = grid_x * block_width;
            let start_y = grid_y * block_height;
            let end_x = ((grid_x + 1) * block_width).min(width - 1);
            let end_y = ((grid_y + 1) * block_height).min(height - 1);
            
            let mut edge_count = 0;
            
            // Simplified Sobel edge detection within this block
            // Skip edges of the block to avoid boundary issues
            for y in (start_y + 1)..(end_y - 1) {
                let row_start = y * stride;
                
                for x in (start_x + 1)..(end_x - 1) {
                    let pixel_index = row_start + x;
                    
                    if pixel_index + stride + 1 < data.len() && pixel_index >= stride + 1 {
                        // Horizontal gradient: |right - left|
                        let left = data[pixel_index - 1] as i32;
                        let right = data[pixel_index + 1] as i32;
                        let gx = (right - left).abs();
                        
                        // Vertical gradient: |bottom - top|
                        let top = data[pixel_index - stride] as i32;
                        let bottom = data[pixel_index + stride] as i32;
                        let gy = (bottom - top).abs();
                        
                        // Combined gradient magnitude
                        let gradient = gx + gy;
                        
                        if gradient > EDGE_THRESHOLD as i32 {
                            edge_count += 1;
                        }
                    }
                }
            }
            
            // If this block has enough edges, consider it "in focus"
            if edge_count >= MIN_EDGES_PER_BLOCK {
                let block_index = grid_y * GRID_SIZE + grid_x;
                focus_blocks.push(block_index.to_string());
            }
        }
    }
    
    // Return CSV of in-focus block indices
    if focus_blocks.is_empty() {
        "".to_string()
    } else {
        focus_blocks.join(",")
    }
}