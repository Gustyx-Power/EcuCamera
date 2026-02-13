package id.xms.ecucamera.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.xms.ecucamera.ui.model.CameraMode

@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraMode: CameraMode = CameraMode.PHOTO,
    isRecording: Boolean = false
) {
    // Animate color transition between Photo (Yellow) and Video (Red)
    val fillColor by animateColorAsState(
        targetValue = when {
            cameraMode == CameraMode.VIDEO && isRecording -> Color(0xFFFF0000) // Red when recording
            cameraMode == CameraMode.VIDEO -> Color(0xFFFF0000) // Red for video mode
            else -> Color(0xFFFFC107) // Yellow for photo mode
        },
        animationSpec = tween(durationMillis = 300),
        label = "shutterButtonColor"
    )
    
    Box(
        modifier = modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.Black, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(fillColor, CircleShape)
            )
        }
    }
}