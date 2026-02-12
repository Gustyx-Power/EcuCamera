package id.xms.ecucamera.ui.screens

import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import id.xms.ecucamera.ui.components.BottomControlBar
import id.xms.ecucamera.ui.components.TopControlBar
import id.xms.ecucamera.ui.screens.viewfinder.ViewfinderScreen

@Composable
fun CameraScreen(
    histogramData: String,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTouchEvent: (android.view.MotionEvent) -> Boolean,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var flashMode by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf("Foto") }
    
    Box(modifier = modifier.fillMaxSize()) {
        ViewfinderScreen(
            onSurfaceReady = onSurfaceReady,
            onSurfaceDestroyed = onSurfaceDestroyed,
            onTouchEvent = onTouchEvent
        )
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            for (i in 1..2) {
                val x = width * i / 3
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }
            
            for (i in 1..2) {
                val y = height * i / 3
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
        }
        
        TopControlBar(
            histogramData = histogramData,
            flashMode = flashMode,
            onFlashToggle = {
                flashMode = if (flashMode == 0) 1 else 0
                onFlashToggle()
            },
            onSettingsClick = { },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        BottomControlBar(
            currentZoom = currentZoom,
            onZoomChange = { zoom ->
                currentZoom = zoom
                onZoomChange(zoom)
            },
            selectedMode = selectedMode,
            onModeChange = { mode ->
                selectedMode = mode
            },
            onGalleryClick = { },
            onShutterClick = onShutterClick,
            onSwitchCamera = onSwitchCamera,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}