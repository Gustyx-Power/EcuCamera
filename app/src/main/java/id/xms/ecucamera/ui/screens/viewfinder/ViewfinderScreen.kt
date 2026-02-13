package id.xms.ecucamera.ui.screens.viewfinder

import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import id.xms.ecucamera.ui.components.AutoFitSurfaceView
import kotlin.math.abs
import kotlin.math.max

/**
 * Displays the camera preview with GCam-style virtual crop.
 *
 * Architecture:
 * ┌──────────────────── Screen (e.g. 1080×2400, 20:9) ──────────────────┐
 * │                                                                      │
 * │  ┌──── clipToBounds Box ──────────────────────────────────────────┐  │
 * │  │                                                                │  │
 * │  │   ┌── AutoFitSurfaceView (FIT-INSIDE, always 3:4 portrait) ─┐ │  │
 * │  │   │                                                          │ │  │
 * │  │   │       graphicsLayer { scaleX = s; scaleY = s }           │ │  │
 * │  │   │       ↑ uniform scale to simulate crop ratios            │ │  │
 * │  │   │                                                          │ │  │
 * │  │   └──────────────────────────────────────────────────────────┘ │  │
 * │  │                                                                │  │
 * │  │   + Black mask overlays for 1:1 mode                          │  │
 * │  │                                                                │  │
 * │  └────────────────────────────────────────────────────────────────┘  │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * Scale factors (portrait, 4:3 buffer → 3:4 on-screen view):
 *
 *   viewWidth  = screenWidth
 *   viewHeight = screenWidth × 4/3   (e.g. 1080 × 1.333 = 1440)
 *
 *   4:3  → scale = 1.0   (native, no scaling)
 *   16:9 → scale = max(screenW/viewW, targetH/viewH) where targetH = screenW × 16/9
 *                 = max(1, (16/9)/(4/3)) = max(1, 1.333) = 1.333
 *   Full → scale = max(screenW/viewW, screenH/viewH)   (center-crop to fill screen)
 *   1:1  → scale = 1.0   (same base view, plus mask overlays)
 *
 * @param aspectRatio     The hardware buffer ratio (always 4/3 landscape)
 * @param targetCropRatio The user's selected virtual crop ratio (1:1, 4:3, 16:9, Full)
 * @param onSurfaceReady  Called when the Surface is created
 * @param onSurfaceDestroyed Called when the Surface is destroyed
 * @param onSurfaceChanged  Called when the Surface dimensions change
 * @param onTouchEvent     Touch handler for gestures
 */
@Composable
fun ViewfinderScreen(
    aspectRatio: Float = 4f / 3f,
    targetCropRatio: Float = 4f / 3f,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onSurfaceChanged: (Surface) -> Unit = {},
    onTouchEvent: ((MotionEvent) -> Boolean)? = null
) {
    var surfaceView by remember { mutableStateOf<AutoFitSurfaceView?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val isPortrait = screenH > screenW

        // ── The 4:3 SurfaceView on-screen dimensions (FIT-INSIDE) ──
        // In portrait: the camera 4:3 buffer is rotated → on-screen ratio = 3:4.
        // viewWidth = screenWidth, viewHeight = screenWidth × 4/3
        val onScreenRatio = if (isPortrait) 1f / aspectRatio else aspectRatio
        val viewW: Float
        val viewH: Float
        val fitH = screenW / onScreenRatio
        if (fitH <= screenH) {
            viewW = screenW
            viewH = fitH
        } else {
            viewH = screenH
            viewW = screenH * onScreenRatio
        }

        // ── Calculate scale factor ──
        //
        // We want to scale the 4:3 view (center-crop style) so that the
        // visible area matches the user-selected ratio.
        //
        // For a target ratio T (in portrait, on-screen = 1/T for landscape ratios):
        //   targetOnScreenW  = screenW  (always fill width)
        //   targetOnScreenH  = screenW / (1/T) = screenW × T   (for landscape T like 16/9)
        //
        // Wait — let's think in terms of the desired VISIBLE area:
        //
        //   4:3 → show the full 3:4 view as-is                  → scale = 1.0
        //   16:9 → show a 9:16 region (taller than 3:4)         → need to ZOOM IN
        //   Full → show a region matching screen ratio           → need to ZOOM IN more
        //   1:1  → show a 1:1 square region                     → scale = 1.0 + masks
        //
        // The CENTER-CROP formula to fill a target rect:
        //   scale = max(targetW / viewW, targetH / viewH)
        //
        // For 16:9 in portrait, the target rect is screenW × min(screenW*16/9, screenH):
        //   targetW = screenW
        //   targetH = min(screenW * 16/9, screenH)
        //   scale = max(screenW/viewW, targetH/viewH)
        //         = max(1.0, targetH/viewH)              since viewW == screenW
        //
        // For Full:
        //   targetW = screenW, targetH = screenH
        //   scale = max(1.0, screenH/viewH)
        //
        // For 1:1:
        //   We DON'T scale — we overlay masks instead.
        //   The 1:1 capture region from the 4:3 buffer is H×H centered.
        //   On screen the FIT-INSIDE view is (viewW × viewH).
        //   The 1:1 region on screen is (viewH/aspectRatio)² — well, let's just
        //   show the full view and mask the top/bottom to make it look square.
        //   Actually: 3:4 view (W × W*4/3). A square = W × W.
        //   Mask = excess height = (viewH - viewW) / 2 on top and bottom.

        val targetScale = when {
            // 4:3 — native, no scaling
            abs(targetCropRatio - 4f / 3f) < 0.05f -> 1f

            // 1:1 — no scaling, masks handle it
            abs(targetCropRatio - 1f) < 0.05f -> 1f

            // 16:9 — center-crop to fill a 9:16 (portrait) area
            abs(targetCropRatio - 16f / 9f) < 0.05f -> {
                if (isPortrait) {
                    val targetH = minOf(screenW * 16f / 9f, screenH)
                    max(screenW / viewW, targetH / viewH)
                } else {
                    val targetW = minOf(screenH * 16f / 9f, screenW)
                    max(targetW / viewW, screenH / viewH)
                }
            }

            // Full screen — center-crop to fill the entire screen
            else -> {
                max(screenW / viewW, screenH / viewH)
            }
        }

        // Animate the scale for smooth GCam-style transition
        val animatedScale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = tween(durationMillis = 300),
            label = "viewfinderScale"
        )

        // ── 1:1 Mask Calculations ──
        // In portrait with 3:4 view: viewW × viewH where viewH = viewW * 4/3.
        // A 1:1 square = viewW × viewW.
        // Excess height per side = (viewH - viewW) / 2.
        val is1to1 = abs(targetCropRatio - 1f) < 0.05f

        val maskBarHeight = if (is1to1 && isPortrait) {
            (viewH - viewW) / 2f
        } else if (is1to1 && !isPortrait) {
            // Landscape 1:1: mask left/right
            0f // Will use width-based masking instead
        } else {
            0f
        }

        val maskBarWidth = if (is1to1 && !isPortrait) {
            (viewW - viewH) / 2f
        } else {
            0f
        }

        val animatedMaskH by animateFloatAsState(
            targetValue = maskBarHeight,
            animationSpec = tween(durationMillis = 300),
            label = "maskHeight"
        )

        val animatedMaskW by animateFloatAsState(
            targetValue = maskBarWidth,
            animationSpec = tween(durationMillis = 300),
            label = "maskWidth"
        )

        // ── SurfaceView with scale animation ──
        AndroidView(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                },
            factory = { context ->
                AutoFitSurfaceView(context).apply {
                    surfaceView = this

                    // Set the camera buffer ratio (always 4:3 landscape)
                    setAspectRatio(4, 3)

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d("ViewfinderScreen", "surfaceCreated: valid=${holder.surface.isValid}")
                            onSurfaceReady(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.d("ViewfinderScreen", "surfaceDestroyed")
                            onSurfaceDestroyed()
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            Log.d("ViewfinderScreen", "surfaceChanged: ${width}x${height}, valid=${holder.surface.isValid}")
                            if (holder.surface.isValid) {
                                onSurfaceChanged(holder.surface)
                            }
                        }
                    })

                    setOnTouchListener { _, event ->
                        onTouchEvent?.invoke(event) ?: false
                    }
                }
            }
        )

        // ── 1:1 Black Mask Overlays ──
        if (isPortrait && animatedMaskH > 1f) {
            // Top mask
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(with(density) { viewW.toDp() })
                    .height(with(density) { animatedMaskH.toDp() })
                    .background(Color.Black)
            )
            // Bottom mask
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(with(density) { viewW.toDp() })
                    .height(with(density) { animatedMaskH.toDp() })
                    .background(Color.Black)
            )
        }

        if (!isPortrait && animatedMaskW > 1f) {
            // Left mask
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(with(density) { animatedMaskW.toDp() })
                    .height(with(density) { viewH.toDp() })
                    .background(Color.Black)
            )
            // Right mask
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(density) { animatedMaskW.toDp() })
                    .height(with(density) { viewH.toDp() })
                    .background(Color.Black)
            )
        }
    }
}
