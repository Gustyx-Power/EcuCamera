package id.xms.ecucamera.ui.components.hud

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun FocusReticle(
    targetOffset: androidx.compose.ui.geometry.Offset,
    isLocked: Boolean = false,
    exposureLevel: Float = 0.5f,
    showSlider: Boolean = false
) {
    val density = LocalDensity.current
    val reticleSize = 60.dp
    val reticleSizePx = with(density) { reticleSize.toPx() }
    val sliderHeight = 120.dp
    val sliderHeightPx = with(density) { sliderHeight.toPx() }
    
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(targetOffset) {
        if (targetOffset != androidx.compose.ui.geometry.Offset.Unspecified) {
            Log.d("ECU_UI", "FocusReticle triggered at (${targetOffset.x}, ${targetOffset.y})")
            visible = true
            
            if (!isLocked) {
                delay(2000)
                visible = false
                Log.d("ECU_UI", "FocusReticle auto-hidden")
            }
        }
    }
    
    LaunchedEffect(isLocked) {
        if (isLocked) {
            visible = true
        }
    }
    
    val targetScale = if (visible) 1f else 1.5f
    val targetAlpha = if (visible) 1f else 0f
    
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reticleScale"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "reticleAlpha"
    )
    
    if (targetOffset != androidx.compose.ui.geometry.Offset.Unspecified && 
        (visible || animatedAlpha > 0.01f)) {
        
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { (targetOffset.x - reticleSizePx / 2).toDp() },
                    y = with(density) { (targetOffset.y - reticleSizePx / 2).toDp() }
                )
                .size(reticleSize)
                .scale(animatedScale)
                .alpha(animatedAlpha)
        ) {
            Box(
                modifier = Modifier
                    .size(reticleSize)
                    .border(
                        width = 3.dp,
                        color = if (isLocked) Color.Red else Color.Yellow,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            if (isLocked) {
                Text(
                    text = "AE/AF LOCKED",
                    color = Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 24.dp)
                )
            }
        }
        
        if (showSlider && visible) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (targetOffset.x + reticleSizePx / 2 + 20).toDp() },
                        y = with(density) { (targetOffset.y - sliderHeightPx / 2).toDp() }
                    )
                    .size(width = 40.dp, height = sliderHeight)
                    .alpha(animatedAlpha)
            ) {
                Canvas(modifier = Modifier.size(width = 40.dp, height = sliderHeight)) {
                    val centerX = size.width / 2
                    val trackTop = 10f
                    val trackBottom = size.height - 10f
                    
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX, trackTop),
                        end = Offset(centerX, trackBottom),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    val thumbY = trackTop + (1f - exposureLevel) * (trackBottom - trackTop)
                    
                    drawCircle(
                        color = Color.Yellow,
                        radius = 12.dp.toPx(),
                        center = Offset(centerX, thumbY)
                    )
                    
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(centerX, thumbY)
                    )
                }
                
                Text(
                    text = "☀",
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-8).dp)
                )
            }
        }
    }
}
