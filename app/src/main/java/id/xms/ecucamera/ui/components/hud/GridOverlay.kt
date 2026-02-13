package id.xms.ecucamera.ui.components.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Grid Overlay for camera composition guides.
 * 
 * @param modifier Modifier for the canvas
 * @param gridMode Grid mode: 0 = OFF, 1 = Rule of Thirds (3x3), 2 = Golden Ratio (Phi Grid)
 */
@Composable
fun GridOverlay(
    modifier: Modifier = Modifier,
    gridMode: Int
) {
    if (gridMode == 0) return // OFF mode - draw nothing
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val strokeWidth = 2.dp.toPx()
        val lineColor = Color.White.copy(alpha = 0.5f)
        
        when (gridMode) {
            1 -> {
                // Rule of Thirds (3x3 grid)
                // Vertical lines at 33.33% and 66.67%
                for (i in 1..2) {
                    val x = width * i / 3f
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = strokeWidth
                    )
                }
                
                // Horizontal lines at 33.33% and 66.67%
                for (i in 1..2) {
                    val y = height * i / 3f
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = strokeWidth
                    )
                }
            }
            
            2 -> {
                // Golden Ratio (Phi Grid)
                // Phi = 1.618, so the ratios are approximately 38.2% and 61.8%
                val phi = 1.618f
                val ratio1 = 1f / phi  // ≈ 0.618 (61.8%)
                val ratio2 = 1f - ratio1  // ≈ 0.382 (38.2%)
                
                // Vertical lines at 38.2% and 61.8%
                drawLine(
                    color = lineColor,
                    start = Offset(width * ratio2, 0f),
                    end = Offset(width * ratio2, height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(width * ratio1, 0f),
                    end = Offset(width * ratio1, height),
                    strokeWidth = strokeWidth
                )
                
                // Horizontal lines at 38.2% and 61.8%
                drawLine(
                    color = lineColor,
                    start = Offset(0f, height * ratio2),
                    end = Offset(width, height * ratio2),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, height * ratio1),
                    end = Offset(width, height * ratio1),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}
