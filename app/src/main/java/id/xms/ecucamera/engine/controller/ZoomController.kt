package id.xms.ecucamera.engine.controller

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import kotlin.math.max
import kotlin.math.min

class ZoomController {
    
    companion object {
        private const val TAG = "ECU_ZOOM"
    }
    
    var maxZoom: Float = 1.0f
        private set
    
    var sensorRect: Rect = Rect()
        private set
    
    var currentZoom: Float = 1.0f
        private set
    
    /**
     * Initialize zoom capabilities from camera characteristics
     */
    fun setCharacteristics(characteristics: CameraCharacteristics) {
        try {
            // Get maximum digital zoom
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            
            // Get sensor active array size
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect()
            
            Log.d(TAG, "Zoom capabilities - Max zoom: $maxZoom, Sensor rect: $sensorRect")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize zoom capabilities", e)
            maxZoom = 1.0f
            sensorRect = Rect()
        }
    }
    
    /**
     * Calculate crop region for digital zoom
     * @param zoomLevel Zoom level from 1.0 to maxZoom
     * @return Rect representing the crop region within sensor bounds
     */
    fun calculateZoomRect(zoomLevel: Float): Rect {
        // Clamp zoom level to valid range
        val clampedZoom = zoomLevel.coerceIn(1.0f, maxZoom)
        currentZoom = clampedZoom
        
        if (sensorRect.isEmpty || clampedZoom <= 1.0f) {
            // No zoom or invalid sensor rect - return full sensor area
            Log.d(TAG, "No zoom applied, returning full sensor rect: $sensorRect")
            return sensorRect
        }
        
        // Calculate new dimensions based on zoom factor
        val sensorWidth = sensorRect.width()
        val sensorHeight = sensorRect.height()
        
        val cropWidth = (sensorWidth / clampedZoom).toInt()
        val cropHeight = (sensorHeight / clampedZoom).toInt()
        
        // Center the crop region within the sensor rect
        val centerX = sensorRect.centerX()
        val centerY = sensorRect.centerY()
        
        val left = max(sensorRect.left, centerX - cropWidth / 2)
        val top = max(sensorRect.top, centerY - cropHeight / 2)
        val right = min(sensorRect.right, left + cropWidth)
        val bottom = min(sensorRect.bottom, top + cropHeight)
        
        val zoomRect = Rect(left, top, right, bottom)
        
        Log.d(TAG, "Zoom ${clampedZoom}x -> Crop rect: $zoomRect (${zoomRect.width()}x${zoomRect.height()})")
        
        return zoomRect
    }
    
    /**
     * Set zoom level and return the calculated crop rect
     * @param zoomLevel Zoom level from 1.0 to maxZoom
     * @return Rect representing the crop region
     */
    fun setZoom(zoomLevel: Float): Rect {
        return calculateZoomRect(zoomLevel)
    }
    
    /**
     * Get current zoom level
     */
    fun getZoomLevel(): Float = currentZoom
    
    /**
     * Get maximum supported zoom
     */
    fun getMaxZoomLevel(): Float = maxZoom
    
    /**
     * Check if digital zoom is supported
     */
    fun isZoomSupported(): Boolean = maxZoom > 1.0f
    
    /**
     * Get zoom range as human-readable string
     */
    fun getZoomRangeString(): String {
        return if (isZoomSupported()) {
            "Zoom: 1.0x - ${maxZoom}x"
        } else {
            "Zoom: Not supported (fixed lens)"
        }
    }
    
    /**
     * Calculate zoom level from pinch gesture scale factor
     * @param scaleFactor Scale factor from gesture detector
     * @param previousZoom Previous zoom level to apply scale to
     * @return New zoom level clamped to valid range
     */
    fun calculateZoomFromGesture(scaleFactor: Float, previousZoom: Float): Float {
        val newZoom = previousZoom * scaleFactor
        return newZoom.coerceIn(1.0f, maxZoom)
    }
}