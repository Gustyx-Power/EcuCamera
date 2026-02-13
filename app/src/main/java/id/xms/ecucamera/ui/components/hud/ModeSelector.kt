package id.xms.ecucamera.ui.components.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.ecucamera.ui.model.CameraMode

@Composable
fun ModeSelector(
    selectedMode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PHOTO Mode
        Text(
            text = "PHOTO",
            color = if (selectedMode == CameraMode.PHOTO) {
                Color(0xFFFFC107) // Yellow for active
            } else {
                Color.White.copy(alpha = 0.5f) // Semi-transparent for inactive
            },
            fontSize = 14.sp,
            fontWeight = if (selectedMode == CameraMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clickable { onModeChange(CameraMode.PHOTO) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        // VIDEO Mode
        Text(
            text = "VIDEO",
            color = if (selectedMode == CameraMode.VIDEO) {
                Color(0xFFFFC107) // Yellow for active
            } else {
                Color.White.copy(alpha = 0.5f) // Semi-transparent for inactive
            },
            fontSize = 14.sp,
            fontWeight = if (selectedMode == CameraMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clickable { onModeChange(CameraMode.VIDEO) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
