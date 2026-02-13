package id.xms.ecucamera.ui.components

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AutoFitSurfaceView — a "dumb" aspect-ratio container for the camera preview.
 *
 * Phase 30: Strict FIT-INSIDE logic.
 *
 * This view does ONE thing: it sizes itself to preserve the given aspect ratio
 * while fitting INSIDE its parent (no stretching, no center-crop here).
 *
 * The [aspectRatio] is the **camera buffer ratio** in landscape orientation
 * (width > height), e.g. 4/3 = 1.333.  In portrait, the display pipeline
 * rotates the buffer 90°, so the on-screen shape becomes the inverse (3/4 = 0.75).
 * This class handles both orientations automatically.
 *
 * Scaling BEYOND this view (to fill the screen for 16:9 / Full) is handled
 * by the parent Composable via `graphicsLayer { scaleX/scaleY }`.
 */
class AutoFitSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle) {

    companion object {
        private const val TAG = "AutoFitSurfaceView"
    }

    /** Camera buffer aspect ratio in landscape (width/height), e.g. 1.333 for 4:3. */
    private var aspectRatio: Float = 0f

    /**
     * Sets the camera buffer aspect ratio (landscape orientation).
     *
     * @param width  Relative horizontal size of the camera buffer (e.g. 4)
     * @param height Relative vertical size of the camera buffer (e.g. 3)
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative." }

        val newAspectRatio = width.toFloat() / height.toFloat()

        if (abs(aspectRatio - newAspectRatio) > 0.01f) {
            aspectRatio = newAspectRatio
            Log.d(TAG, "Aspect ratio set to: $width:$height (${String.format("%.3f", aspectRatio)})")
            requestLayout()
        }
    }

    /**
     * Sets the preview buffer fixed size so the Surface consumer (camera)
     * writes at the correct resolution.
     *
     * @param previewWidth  Camera preview buffer width  (landscape, e.g. 1920)
     * @param previewHeight Camera preview buffer height (landscape, e.g. 1440)
     */
    fun setPreviewSize(previewWidth: Int, previewHeight: Int) {
        try {
            holder.setFixedSize(previewWidth, previewHeight)
            Log.d(TAG, "Preview buffer size set to: ${previewWidth}x${previewHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set preview buffer size", e)
        }
    }

    /**
     * Measures the view to FIT inside the parent while preserving [aspectRatio].
     *
     * In **portrait** the camera buffer is rotated 90° by the display pipeline,
     * so a 4:3 buffer appears as 3:4 on screen. We calculate the on-screen ratio
     * automatically based on container orientation.
     *
     * The resulting view may be smaller than the parent in one dimension,
     * with black space around it. That's intentional — scaling beyond happens
     * in ViewfinderScreen via graphicsLayer.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        val containerHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (aspectRatio == 0f) {
            // No ratio set yet — just use parent size
            setMeasuredDimension(containerWidth, containerHeight)
            return
        }

        val isPortrait = containerHeight > containerWidth

        // Compute on-screen ratio:
        // Camera buffer is landscape (W>H). In portrait the buffer is rotated,
        // so on-screen width/height = 1/aspectRatio.
        // Example: buffer 4:3 → portrait on-screen 3:4. screenRatio = 0.75
        val screenRatio = if (isPortrait) 1f / aspectRatio else aspectRatio

        // FIT-INSIDE: choose the limiting dimension
        val finalWidth: Int
        val finalHeight: Int

        val fitToWidthHeight = (containerWidth / screenRatio).roundToInt()

        if (fitToWidthHeight <= containerHeight) {
            // Width is the limiting dimension
            finalWidth = containerWidth
            finalHeight = fitToWidthHeight
        } else {
            // Height is the limiting dimension
            finalHeight = containerHeight
            finalWidth = (containerHeight * screenRatio).roundToInt()
        }

        Log.d(TAG, "onMeasure: container=${containerWidth}x${containerHeight}, " +
                "isPortrait=$isPortrait, " +
                "bufferRatio=${String.format("%.3f", aspectRatio)}, " +
                "screenRatio=${String.format("%.3f", screenRatio)}, " +
                "final=${finalWidth}x${finalHeight}")

        setMeasuredDimension(finalWidth, finalHeight)
    }
}
