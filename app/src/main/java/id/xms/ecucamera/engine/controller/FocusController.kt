package id.xms.ecucamera.engine.controller

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

class FocusController {
    
    companion object {
        private const val TAG = "ECU_FOCUS"
    }
    
    private var minimumFocusDistance: Float = 0.0f
    private var isManualFocusSupported = false
    
    /**
     * Initialize focus capabilities from camera characteristics
     */
    fun setCapabilities(characteristics: CameraCharacteristics) {
        try {
            // Get minimum focus distance (0.0 means fixed focus, > 0.0 means variable focus)
            minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
            
            // Check if manual focus is supported
            isManualFocusSupported = minimumFocusDistance > 0.0f
            
            Log.d(TAG, "Focus capabilities - Min distance: $minimumFocusDistance, Manual supported: $isManualFocusSupported")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize focus capabilities", e)
            isManualFocusSupported = false
            minimumFocusDistance = 0.0f
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
     */
    fun applyAutoFocus(builder: CaptureRequest.Builder) {
        try {
            // Set AF mode to AUTO
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            
            Log.d(TAG, "Applied auto focus")
            
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
}