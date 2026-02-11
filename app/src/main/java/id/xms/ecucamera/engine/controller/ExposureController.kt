package id.xms.ecucamera.engine.controller

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Range
import kotlin.math.pow

class ExposureController {
    
    companion object {
        private const val TAG = "ECU_EXPOSURE"
    }
    
    var isoRange: Range<Int>? = null
        private set
    
    var exposureTimeRange: Range<Long>? = null
        private set
    
    /**
     * Initialize exposure ranges from camera characteristics
     */
    fun setRanges(characteristics: CameraCharacteristics) {
        try {
            // Get ISO sensitivity range
            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            Log.d(TAG, "ISO Range: ${isoRange?.lower} - ${isoRange?.upper}")
            
            // Get exposure time range (in nanoseconds)
            exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            Log.d(TAG, "Exposure Time Range: ${exposureTimeRange?.lower}ns - ${exposureTimeRange?.upper}ns")
            
            // Log human-readable exposure times
            exposureTimeRange?.let { range ->
                val minSeconds = range.lower / 1_000_000_000.0
                val maxSeconds = range.upper / 1_000_000_000.0
                Log.d(TAG, "Exposure Time Range (seconds): ${minSeconds}s - ${maxSeconds}s")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize exposure ranges", e)
        }
    }
    
    /**
     * Calculate ISO value from slider position (0.0 - 1.0)
     * Uses linear mapping from min to max ISO
     */
    fun calculateISO(sliderValue: Float): Int {
        val range = isoRange ?: return 100 // Default fallback
        
        val clampedValue = sliderValue.coerceIn(0.0f, 1.0f)
        val minIso = range.lower
        val maxIso = range.upper
        
        val calculatedIso = (minIso + (maxIso - minIso) * clampedValue).toInt()
        
        Log.d(TAG, "ISO Slider: $sliderValue -> ISO: $calculatedIso")
        return calculatedIso
    }
    
    /**
     * Calculate exposure time from slider position (0.0 - 1.0)
     * Uses logarithmic mapping for better control over exposure range
     */
    fun calculateExposureTime(sliderValue: Float): Long {
        val range = exposureTimeRange ?: return 10_000_000L // Default 1/100s
        
        val clampedValue = sliderValue.coerceIn(0.0f, 1.0f)
        val minTime = range.lower.toDouble()
        val maxTime = range.upper.toDouble()
        
        // Use logarithmic scale for better exposure control
        val logMin = kotlin.math.ln(minTime)
        val logMax = kotlin.math.ln(maxTime)
        val logValue = logMin + (logMax - logMin) * clampedValue
        
        val calculatedTime = kotlin.math.exp(logValue).toLong()
        
        // Log human-readable values
        val seconds = calculatedTime / 1_000_000_000.0
        Log.d(TAG, "Exposure Slider: $sliderValue -> Time: ${calculatedTime}ns (${seconds}s)")
        
        return calculatedTime
    }
    
    /**
     * Get current ISO range as human-readable string
     */
    fun getISORangeString(): String {
        return isoRange?.let { "ISO ${it.lower} - ${it.upper}" } ?: "ISO Range Unknown"
    }
    
    /**
     * Get current exposure time range as human-readable string
     */
    fun getExposureRangeString(): String {
        return exposureTimeRange?.let { range ->
            val minSeconds = range.lower / 1_000_000_000.0
            val maxSeconds = range.upper / 1_000_000_000.0
            "Exposure ${minSeconds}s - ${maxSeconds}s"
        } ?: "Exposure Range Unknown"
    }
    
    /**
     * Check if manual exposure control is supported
     */
    fun isManualExposureSupported(): Boolean {
        return isoRange != null && exposureTimeRange != null
    }
    
    /**
     * Apply exposure settings to capture request builder
     * @param builder CaptureRequest.Builder to apply settings to
     * @param isManualMode Whether manual exposure mode is active
     * @param currentIso Current ISO value (used only in manual mode)
     * @param currentExpTime Current exposure time (used only in manual mode)
     */
    fun applyToBuilder(builder: android.hardware.camera2.CaptureRequest.Builder, isManualMode: Boolean, currentIso: Int, currentExpTime: Long) {
        try {
            if (isManualMode && isManualExposureSupported()) {
                // Set manual exposure mode
                builder.set(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                builder.set(android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME, currentExpTime)
                Log.d(TAG, "Applied manual exposure: ISO=$currentIso, ExpTime=${currentExpTime}ns")
            } else {
                // Set auto exposure mode
                builder.set(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON)
                Log.d(TAG, "Applied auto exposure mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply exposure settings", e)
            // Fallback to auto exposure
            builder.set(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON)
        }
    }
}