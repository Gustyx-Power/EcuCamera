package id.xms.ecucamera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.xms.ecucamera.R

@Composable
fun TopControlBar(
    histogramData: String,
    flashMode: Int,
    onFlashToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistogramGraph(
            dataCsv = histogramData,
            modifier = Modifier
        )
        
        IconButton(onClick = onFlashToggle) {
            Icon(
                imageVector = if (flashMode == 0) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                contentDescription = stringResource(if (flashMode == 0) R.string.cd_flash_off else R.string.cd_flash_on),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
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