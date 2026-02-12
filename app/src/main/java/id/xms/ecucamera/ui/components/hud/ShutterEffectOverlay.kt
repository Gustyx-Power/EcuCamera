package id.xms.ecucamera.ui.components.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * ShutterEffectOverlay provides instant visual feedback when a photo is taken.
 * Creates a black flash effect that instantly appears and quickly fades out.
 * 
 * @param trigger When true, triggers the shutter animation
 * @param onFinished Callback invoked when animation completes
 */
@Composable
fun ShutterEffectOverlay(
    trigger: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            // Instantly set to black (no animation)
            alpha.snapTo(1f)
            
            // Animate back to transparent over 120ms
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 120)
            )
            
            // Notify completion
            onFinished()
        }
    }
    
    // Black overlay with animated alpha
    if (alpha.value > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha.value))
        )
    }
}
