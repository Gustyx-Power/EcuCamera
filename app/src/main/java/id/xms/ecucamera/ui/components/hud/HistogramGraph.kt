package id.xms.ecucamera.ui.components.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun HistogramGraph(
    dataCsv: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(120.dp)
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (dataCsv.isEmpty()) return@Canvas
            
            // Parse CSV to list of integers
            val values = try {
                dataCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            } catch (e: Exception) {
                return@Canvas
            }
            
            if (values.isEmpty()) return@Canvas
            val maxValue = values.maxOrNull() ?: 1
            if (maxValue == 0) return@Canvas
            
            val width = size.width
            val height = size.height
            val stepX = width / values.size.toFloat()
            
            // Create path for the histogram
            val path = Path().apply {
                // Start at bottom-left
                moveTo(0f, height)
                
                // Draw histogram bars
                values.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalizedHeight = (value.toFloat() / maxValue) * height
                    val y = height - normalizedHeight
                    
                    lineTo(x, y)
                }
                
                // Complete the path back to bottom-right
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            
            // Draw filled histogram
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.5f),
                style = Fill
            )
            
            // Draw stroke border for better visibility
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 1f)
            )
        }
    }
}
