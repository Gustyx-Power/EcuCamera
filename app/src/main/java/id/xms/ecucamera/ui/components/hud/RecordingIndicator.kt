package id.xms.ecucamera.ui.components.hud

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun RecordingIndicator(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isRecording) return
    
    // Blinking red dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "recording_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_alpha"
    )
    
    // Recording timer
    var recordingTime by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingTime = System.currentTimeMillis() - startTime
                delay(100)
            }
        } else {
            recordingTime = 0L
        }
    }
    
    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Blinking red dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.Red.copy(alpha = alpha), CircleShape)
        )
        
        // Timer text
        Text(
            text = formatRecordingTime(recordingTime),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatRecordingTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}
