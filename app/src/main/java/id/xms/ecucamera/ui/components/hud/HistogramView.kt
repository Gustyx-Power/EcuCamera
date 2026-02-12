package id.xms.ecucamera.ui.components.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun HistogramView(
    data: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.size == 256) {
            drawHistogram(data)
        }
    }
}

private fun DrawScope.drawHistogram(data: List<Int>) {
    val maxValue = data.maxOrNull() ?: 1
    if (maxValue == 0) return
    
    val canvasWidth = size.width
    val canvasHeight = size.height
    val barWidth = canvasWidth / 256f
    
    // Draw 256 vertical lines representing histogram bins
    for (i in 0 until 256) {
        val value = data[i]
        val normalizedHeight = (value.toFloat() / maxValue.toFloat()) * canvasHeight
        
        if (normalizedHeight > 0) {
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(
                    x = i * barWidth,
                    y = canvasHeight
                ),
                end = androidx.compose.ui.geometry.Offset(
                    x = i * barWidth,
                    y = canvasHeight - normalizedHeight
                ),
                strokeWidth = barWidth
            )
        }
    }
}