package id.xms.ecucamera.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun ManualControlsSelector(
    activeTarget: ManualTarget,
    onTargetChange: (ManualTarget) -> Unit,
    isoDisplayValue: String,
    shutterDisplayValue: String,
    focusDisplayValue: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ManualControlButton(
            label = stringResource(R.string.label_iso),
            value = isoDisplayValue,
            isActive = activeTarget == ManualTarget.ISO,
            onClick = {
                onTargetChange(
                    if (activeTarget == ManualTarget.ISO) ManualTarget.NONE else ManualTarget.ISO
                )
            }
        )
        
        ManualControlButton(
            label = stringResource(R.string.label_shutter),
            value = shutterDisplayValue,
            isActive = activeTarget == ManualTarget.SHUTTER,
            onClick = {
                onTargetChange(
                    if (activeTarget == ManualTarget.SHUTTER) ManualTarget.NONE else ManualTarget.SHUTTER
                )
            }
        )
        
        ManualControlButton(
            label = stringResource(R.string.label_focus),
            value = focusDisplayValue,
            isActive = activeTarget == ManualTarget.FOCUS,
            onClick = {
                onTargetChange(
                    if (activeTarget == ManualTarget.FOCUS) ManualTarget.NONE else ManualTarget.FOCUS
                )
            }
        )
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
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