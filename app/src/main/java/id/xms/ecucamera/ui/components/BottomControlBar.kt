package id.xms.ecucamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomControlBar(
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    selectedMode: String,
    onModeChange: (String) -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = 50.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(1.0f, 2.0f, 5.0f).forEach { zoom ->
                    Text(
                        text = "${zoom.toInt()}x",
                        color = if (currentZoom == zoom) Color(0xFFFFC107) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (currentZoom == zoom) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onZoomChange(zoom) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf("Malam", "Potret", "Foto", "Video")) { mode ->
                    Text(
                        text = mode,
                        color = if (mode == selectedMode) Color(0xFFFFC107) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onModeChange(mode) }
                            .background(
                                if (mode == selectedMode) Color(0xFFFFC107).copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onGalleryClick) {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                ShutterButton(onClick = onShutterClick)
                
                IconButton(onClick = onSwitchCamera) {
                    Icon(
                        imageVector = Icons.Filled.CameraFront,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}