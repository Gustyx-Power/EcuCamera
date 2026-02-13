package id.xms.ecucamera.engine.controller

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FocusController {
    
    companion object {
        private const val TAG = "ECU_FOCUS"
        private const val METERING_WEIGHT = 1000
        private const val FOCUS_LOCK_DURATION_MS = 4000L
    }
    
    private var minimumFocusDistance: Float = 0.0f
    private var isManualFocusSupported = false
    private var availableAfModes: IntArray = intArrayOf()
    private var supportsContinuousAf = false
    private var autoResetJob: Job? = null
    private val focusScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Initialize focus capabilities from camera characteristics
     */
    fun setCapabilities(characteristics: CameraCharacteristics) {
        try {
            // Get minimum focus distance (0.0 means fixed focus, > 0.0 means variable focus)
            minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
            
            // Check if manual focus is supported
            isManualFocusSupported = minimumFocusDistance > 0.0f
            
            // Get available AF modes
            availableAfModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            
            // Check if continuous auto focus is supported
            supportsContinuousAf = availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            
            Log.d(TAG, "Focus capabilities - Min distance: $minimumFocusDistance, Manual supported: $isManualFocusSupported")
            Log.d(TAG, "Available AF modes: ${availableAfModes.joinToString()}, Continuous AF: $supportsContinuousAf")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize focus capabilities", e)
            isManualFocusSupported = false
            minimumFocusDistance = 0.0f
            supportsContinuousAf = false
        }
    }
    
    /**
     * Calculate focus distance from slider value (0.0 - 1.0)
     * @param sliderValue 0.0 = Infinity focus, 1.0 = Macro (closest) focus
     * @return Focus distance in diopters
     */
    fun calculateFocusDistance(sliderValue: Float): Float {
        if (!isManualFocusSupported || minimumFocusDistance <= 0.0f) {
            return 0.0f // Fixed focus or unsupported
        }
        
        val clampedValue = sliderValue.coerceIn(0.0f, 1.0f)
        
        // Map slider value to focus distance
        // 0.0 (infinity) -> 0.0 diopters
        // 1.0 (macro) -> minimumFocusDistance diopters
        val focusDistance = clampedValue * minimumFocusDistance
        
        Log.d(TAG, "Focus slider: $sliderValue -> Distance: $focusDistance diopters")
        return focusDistance
    }
    
    /**
     * Apply manual focus settings to capture request builder
     */
    fun applyManualFocus(builder: CaptureRequest.Builder, focusDistance: Float) {
        if (!isManualFocusSupported) {
            Log.w(TAG, "Manual focus not supported, skipping")
            return
        }
        
        try {
            // Set AF mode to OFF for manual control
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            
            // Set the focus distance
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            
            Log.d(TAG, "Applied manual focus: distance=$focusDistance")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply manual focus settings", e)
        }
    }
    
    /**
     * Apply auto focus settings to capture request builder
     * Uses CONTINUOUS_PICTURE mode for best preview experience (constantly adjusts focus)
     * Falls back to AUTO mode if continuous AF is not supported
     */
    fun applyAutoFocus(builder: CaptureRequest.Builder) {
        try {
            // Prefer continuous auto focus for smooth preview experience
            if (supportsContinuousAf) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                Log.d(TAG, "Applied continuous auto focus (CONTINUOUS_PICTURE)")
            } else if (availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                Log.d(TAG, "Applied single-shot auto focus (AUTO) - continuous AF not supported")
            } else {
                // Fixed focus camera
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                Log.d(TAG, "Fixed focus camera - AF disabled")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply auto focus settings", e)
        }
    }
    
    /**
     * Check if manual focus is supported by the camera
     */
    fun isManualFocusSupported(): Boolean = isManualFocusSupported
    
    /**
     * Get minimum focus distance for display
     */
    fun getMinimumFocusDistance(): Float = minimumFocusDistance
    
    /**
     * Get focus range as human-readable string
     */
    fun getFocusRangeString(): String {
        return if (isManualFocusSupported) {
            "Focus: 0.0 - ${minimumFocusDistance} diopters"
        } else {
            "Focus: Fixed (Manual not supported)"
        }
    }
    
    /**
     * Focus on a specific point in the view
     * 
     * Coordinate Transformation Pipeline:
     * 1. Touch coordinates (x, y) in view space
     * 2. Rotate based on sensor orientation
     * 3. Map to sensor active array coordinates
     * 4. Create metering rectangle
     * 
     * @param x Touch X coordinate in view space
     * @param y Touch Y coordinate in view space
     * @param viewWidth Width of the preview view
     * @param viewHeight Height of the preview view
     * @param sensorOrientation Camera sensor orientation (0, 90, 180, 270)
     * @param activeArraySize Sensor active array size
     * @param builder CaptureRequest.Builder to apply focus settings
     */
    fun focusOnPoint(
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int,
        sensorOrientation: Int,
        activeArraySize: Rect,
        builder: CaptureRequest.Builder
    ) {
        try {
            // Cancel any existing auto-reset job
            autoResetJob?.cancel()
            
            // Step 1: Normalize touch coordinates to [0, 1]
            val normalizedX = (x / viewWidth).coerceIn(0f, 1f)
            val normalizedY = (y / viewHeight).coerceIn(0f, 1f)
            
            Log.d(TAG, "Touch: ($x, $y) in view (${viewWidth}x${viewHeight})")
            Log.d(TAG, "Normalized: ($normalizedX, $normalizedY)")
            
            // Step 2: Rotate coordinates based on sensor orientation
            // The sensor coordinate system may be rotated relative to the view
            val (rotatedX, rotatedY) = when (sensorOrientation) {
                0 -> Pair(normalizedX, normalizedY)
                90 -> Pair(normalizedY, 1f - normalizedX)
                180 -> Pair(1f - normalizedX, 1f - normalizedY)
                270 -> Pair(1f - normalizedY, normalizedX)
                else -> {
                    Log.w(TAG, "Unexpected sensor orientation: $sensorOrientation, using 0°")
                    Pair(normalizedX, normalizedY)
                }
            }
            
            Log.d(TAG, "Rotated (${sensorOrientation}°): ($rotatedX, $rotatedY)")
            
            // Step 3: Map to sensor active array coordinates
            val sensorWidth = activeArraySize.width()
            val sensorHeight = activeArraySize.height()
            
            val sensorX = (rotatedX * sensorWidth).roundToInt()
            val sensorY = (rotatedY * sensorHeight).roundToInt()
            
            Log.d(TAG, "Sensor coords: ($sensorX, $sensorY) in array (${sensorWidth}x${sensorHeight})")
            
            // Step 4: Create metering rectangle (150x150 region centered on touch point)
            val meteringSize = 150
            val halfSize = meteringSize / 2
            
            val left = (sensorX - halfSize).coerceIn(0, sensorWidth - meteringSize)
            val top = (sensorY - halfSize).coerceIn(0, sensorHeight - meteringSize)
            val right = (left + meteringSize).coerceAtMost(sensorWidth)
            val bottom = (top + meteringSize).coerceAtMost(sensorHeight)
            
            val meteringRect = MeteringRectangle(
                left, top,
                right - left, bottom - top,
                METERING_WEIGHT
            )
            
            Log.d(TAG, "Metering rect: ($left, $top, $right, $bottom)")
            
            // Step 5: Apply AF/AE regions and trigger
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            
            Log.d(TAG, "Tap-to-focus triggered at sensor ($sensorX, $sensorY)")
            
            // Step 6: Schedule auto-reset to continuous AF after delay
            autoResetJob = focusScope.launch {
                delay(FOCUS_LOCK_DURATION_MS)
                Log.d(TAG, "Auto-resetting to continuous AF mode")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to focus on point", e)
        }
    }
    
    /**
     * Cancel tap-to-focus and return to continuous AF mode
     * 
     * @param builder CaptureRequest.Builder to apply reset settings
     */
    fun cancelFocus(builder: CaptureRequest.Builder) {
        try {
            autoResetJob?.cancel()
            
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
            
            if (supportsContinuousAf) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else if (availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
            
            Log.d(TAG, "Focus cancelled, returned to continuous AF")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel focus", e)
        }
    }
    
    fun triggerAeAfLock(
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int,
        sensorOrientation: Int,
        activeArraySize: Rect,
        builder: CaptureRequest.Builder
    ) {
        try {
            autoResetJob?.cancel()
            
            val normalizedX = (x / viewWidth).coerceIn(0f, 1f)
            val normalizedY = (y / viewHeight).coerceIn(0f, 1f)
            
            val (rotatedX, rotatedY) = when (sensorOrientation) {
                0 -> Pair(normalizedX, normalizedY)
                90 -> Pair(normalizedY, 1f - normalizedX)
                180 -> Pair(1f - normalizedX, 1f - normalizedY)
                270 -> Pair(1f - normalizedY, normalizedX)
                else -> Pair(normalizedX, normalizedY)
            }
            
            val sensorWidth = activeArraySize.width()
            val sensorHeight = activeArraySize.height()
            val sensorX = (rotatedX * sensorWidth).roundToInt()
            val sensorY = (rotatedY * sensorHeight).roundToInt()
            
            val meteringSize = 150
            val halfSize = meteringSize / 2
            val left = (sensorX - halfSize).coerceIn(0, sensorWidth - meteringSize)
            val top = (sensorY - halfSize).coerceIn(0, sensorHeight - meteringSize)
            val right = (left + meteringSize).coerceAtMost(sensorWidth)
            val bottom = (top + meteringSize).coerceAtMost(sensorHeight)
            
            val meteringRect = MeteringRectangle(
                left, top,
                right - left, bottom - top,
                METERING_WEIGHT
            )
            
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
            
            Log.d(TAG, "AE/AF Lock triggered at sensor ($sensorX, $sensorY)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger AE/AF lock", e)
        }
    }
    
    fun unlockAeAf(builder: CaptureRequest.Builder) {
        try {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, null)
            
            if (supportsContinuousAf) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            
            Log.d(TAG, "AE/AF unlocked")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unlock AE/AF", e)
        }
    }
}