package id.xms.ecucamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.ecucamera.R
import id.xms.ecucamera.ui.model.CamAspectRatio

@Composable
fun TopControlBar(
    currentRatio: CamAspectRatio,
    onRatioChanged: () -> Unit,
    isHistogramVisible: Boolean,
    onToggleHistogram: () -> Unit,
    flashMode: Int,
    onFlashToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Aspect Ratio selector
        Text(
            text = currentRatio.displayName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onRatioChanged)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        // Right side: Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Histogram toggle
            IconButton(onClick = onToggleHistogram) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = stringResource(R.string.cd_toggle_histogram),
                    tint = if (isHistogramVisible) Color(0xFFFFC107) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Flash toggle
            IconButton(onClick = onFlashToggle) {
                Icon(
                    imageVector = if (flashMode == 0) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = stringResource(if (flashMode == 0) R.string.cd_flash_off else R.string.cd_flash_on),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Settings
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}