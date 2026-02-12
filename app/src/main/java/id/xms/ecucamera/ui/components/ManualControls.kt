package id.xms.ecucamera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.ecucamera.R
import id.xms.ecucamera.ui.model.ManualTarget

@Composable
fun ManualControls(
    isoValue: Float,
    onIsoChange: (Float) -> Unit,
    shutterValue: Float,
    onShutterChange: (Float) -> Unit,
    focusValue: Float,
    onFocusChange: (Float) -> Unit,
    isoDisplayValue: String,
    shutterDisplayValue: String,
    focusDisplayValue: String,
    modifier: Modifier = Modifier
) {
    var activeTarget by remember { mutableStateOf(ManualTarget.NONE) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        AnimatedVisibility(
            visible = activeTarget != ManualTarget.NONE,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column {
                when (activeTarget) {
                    ManualTarget.ISO -> {
                        Slider(
                            value = isoValue,
                            onValueChange = onIsoChange,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFC107),
                                activeTrackColor = Color(0xFFFFC107),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                    ManualTarget.SHUTTER -> {
                        Slider(
                            value = shutterValue,
                            onValueChange = onShutterChange,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFC107),
                                activeTrackColor = Color(0xFFFFC107),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                    ManualTarget.FOCUS -> {
                        Slider(
                            value = focusValue,
                            onValueChange = onFocusChange,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFC107),
                                activeTrackColor = Color(0xFFFFC107),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                    ManualTarget.NONE -> { /* No slider */ }
                }
            }
        }
        
        if (activeTarget != ManualTarget.NONE) {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ManualControlButton(
                label = stringResource(R.string.label_iso),
                value = isoDisplayValue,
                isActive = activeTarget == ManualTarget.ISO,
                onClick = {
                    activeTarget = if (activeTarget == ManualTarget.ISO) {
                        ManualTarget.NONE
                    } else {
                        ManualTarget.ISO
                    }
                }
            )
            
            ManualControlButton(
                label = stringResource(R.string.label_shutter),
                value = shutterDisplayValue,
                isActive = activeTarget == ManualTarget.SHUTTER,
                onClick = {
                    activeTarget = if (activeTarget == ManualTarget.SHUTTER) {
                        ManualTarget.NONE
                    } else {
                        ManualTarget.SHUTTER
                    }
                }
            )
            
            ManualControlButton(
                label = stringResource(R.string.label_focus),
                value = focusDisplayValue,
                isActive = activeTarget == ManualTarget.FOCUS,
                onClick = {
                    activeTarget = if (activeTarget == ManualTarget.FOCUS) {
                        ManualTarget.NONE
                    } else {
                        ManualTarget.FOCUS
                    }
                }
            )
        }
    }
}

@Composable
private fun ManualControlButton(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (isActive) Color(0xFFFFC107) else Color.White
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = value,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}