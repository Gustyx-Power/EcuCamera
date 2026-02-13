package id.xms.ecucamera.ui.components

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import id.xms.ecucamera.R
import id.xms.ecucamera.ui.components.gallery.GalleryButton
import id.xms.ecucamera.ui.model.CameraMode
import id.xms.ecucamera.ui.model.ManualTarget
import kotlinx.coroutines.launch

@Composable
fun BottomControlBar(
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    selectedMode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: suspend () -> Unit,
    activeManualTarget: ManualTarget = ManualTarget.NONE,
    onManualTargetChange: (ManualTarget) -> Unit = {},
    isoDisplayValue: String = "100",
    shutterDisplayValue: String = "1/60",
    focusDisplayValue: String = "2.0m",
    galleryThumbnail: Bitmap? = null,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var switchRotation by remember { mutableStateOf(0f) }
    
    val animatedRotation by animateFloatAsState(
        targetValue = switchRotation,
        animationSpec = tween(durationMillis = 300),
        label = "switchCameraRotation"
    )
    val modes = listOf(
        CameraMode.NIGHT,
        CameraMode.PORTRAIT,
        CameraMode.PHOTO,
        CameraMode.VIDEO,
        CameraMode.PRO
    )
    
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
            if (selectedMode == CameraMode.PRO) {
                ManualControlsSelector(
                    activeTarget = activeManualTarget,
                    onTargetChange = onManualTargetChange,
                    isoDisplayValue = isoDisplayValue,
                    shutterDisplayValue = shutterDisplayValue,
                    focusDisplayValue = focusDisplayValue
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf(
                        1.0f to R.string.zoom_1x,
                        2.0f to R.string.zoom_2x,
                        5.0f to R.string.zoom_5x
                    ).forEach { (zoom, stringRes) ->
                        Text(
                            text = stringResource(stringRes),
                            color = if (currentZoom == zoom) Color(0xFFFFC107) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (currentZoom == zoom) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable { onZoomChange(zoom) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(modes) { mode ->
                    Text(
                        text = stringResource(mode.labelRes),
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
                GalleryButton(
                    thumbnail = galleryThumbnail,
                    onClick = onGalleryClick
                )
                
                ShutterButton(
                    onClick = onShutterClick,
                    cameraMode = selectedMode,
                    isRecording = isRecording
                )
                
                IconButton(
                    onClick = {
                        switchRotation += 180f
                        scope.launch {
                            onSwitchCamera()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlipCameraIos,
                        contentDescription = stringResource(R.string.cd_switch_camera),
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(animatedRotation)
                    )
                }
            }
        }
    }
}