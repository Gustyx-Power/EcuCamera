package id.xms.ecucamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun HistogramGraph(
    dataCsv: String,
    modifier: Modifier = Modifier
) {
    // Parse CSV data with safe handling
    val histogramData = remember(dataCsv) {
        if (dataCsv.isBlank()) {
            emptyList()
        } else {
            try {
                dataCsv.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .takeIf { it.size == 256 } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // Reusable Path object to avoid allocations in onDraw
    val path = remember { Path() }
    
    Box(
        modifier = modifier
            .size(100.dp, 60.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Canvas(
            modifier = Modifier.size(100.dp, 60.dp)
        ) {
            if (histogramData.isNotEmpty()) {
                drawSmoothHistogram(histogramData, path)
            }
        }
    }
}

private fun DrawScope.drawSmoothHistogram(data: List<Int>, reusablePath: Path) {
    val maxValue = data.maxOrNull() ?: 1
    if (maxValue == 0) return
    
    val canvasWidth = size.width
    val canvasHeight = size.height
    val stepX = canvasWidth / (data.size - 1).toFloat()
    
    // Clear and reset the reusable path
    reusablePath.reset()
    
    // Start from bottom-left
    reusablePath.moveTo(0f, canvasHeight)
    
    // Create smooth curve through histogram points
    for (i in data.indices) {
        val x = i * stepX
        val normalizedValue = data[i].toFloat() / maxValue.toFloat()
        val y = canvasHeight - (normalizedValue * canvasHeight)
        
        if (i == 0) {
            reusablePath.lineTo(x, y)
        } else {
            // Use quadratic bezier for smooth curves
            val prevX = (i - 1) * stepX
            val prevNormalizedValue = data[i - 1].toFloat() / maxValue.toFloat()
            val prevY = canvasHeight - (prevNormalizedValue * canvasHeight)
            
            val controlX = (prevX + x) / 2f
            val controlY = (prevY + y) / 2f
            
            reusablePath.quadraticBezierTo(controlX, controlY, x, y)
        }
    }
    
    // Close the path to create a filled area
    reusablePath.lineTo(canvasWidth, canvasHeight)
    reusablePath.close()
    
    // Draw the filled histogram with white 50% opacity
    drawPath(
        path = reusablePath,
        color = Color(0x80FFFFFF), // White with 50% opacity
        style = androidx.compose.ui.graphics.drawscope.Fill
    )
}