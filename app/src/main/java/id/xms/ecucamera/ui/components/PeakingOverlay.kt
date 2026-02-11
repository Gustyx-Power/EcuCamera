package id.xms.ecucamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun PeakingOverlay(
    focusBlocks: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (focusBlocks.isNotEmpty()) {
            drawFocusBlocks(focusBlocks)
        }
    }
}

private fun DrawScope.drawFocusBlocks(focusBlocks: List<Int>) {
    val gridSize = 10 // 10x10 grid as defined in Rust
    val blockWidth = size.width / gridSize
    val blockHeight = size.height / gridSize
    
    // Draw green rectangles over in-focus blocks
    focusBlocks.forEach { blockIndex ->
        val gridX = blockIndex % gridSize
        val gridY = blockIndex / gridSize
        
        val left = gridX * blockWidth
        val top = gridY * blockHeight
        val right = left + blockWidth
        val bottom = top + blockHeight
        
        // Draw a green outline rectangle
        drawRect(
            color = Color.Green,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(blockWidth, blockHeight),
            style = Stroke(width = 3.0f)
        )
    }
}