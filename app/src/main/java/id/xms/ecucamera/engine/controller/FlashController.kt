package id.xms.ecucamera.engine.controller

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * FlashController - Simplified 2-mode flash control
 * 
 * Mode 0: OFF - No light
 * Mode 1: ON (Torch) - Constant light for CV processing
 * 
 * Torch mode provides continuous illumination required by the Rust engine
 * for real-time histogram and focus peaking calculations during preview.
 */
class FlashController {
    
    companion object {
        private const val TAG = "ECU_FLASH"
        
        const val FLASH_OFF = 0
        const val FLASH_ON = 1
    }
    
    private var currentFlashMode: Int = FLASH_OFF
    private var isFlashSupported = false
    
    fun setCapabilities(characteristics: CameraCharacteristics) {
        try {
            isFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            
            if (!isFlashSupported) {
                currentFlashMode = FLASH_OFF
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize flash capabilities", e)
            isFlashSupported = false
            currentFlashMode = FLASH_OFF
        }
    }
    
    fun setFlashMode(mode: Int) {
        if (!isFlashSupported && mode != FLASH_OFF) {
            return
        }
        
        currentFlashMode = when (mode) {
            FLASH_OFF, FLASH_ON -> mode
            else -> FLASH_OFF
        }
    }
    
    fun applyToBuilder(builder: CaptureRequest.Builder, isManualExposureMode: Boolean = false) {
        if (!isFlashSupported) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            return
        }
        
        try {
            when (currentFlashMode) {
                FLASH_OFF -> {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                
                FLASH_ON -> {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                
                else -> {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply flash settings", e)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
    }
    
    fun applyToCaptureBuilder(builder: CaptureRequest.Builder, isManualExposureMode: Boolean = false) {
        applyToBuilder(builder, isManualExposureMode)
    }
    
    fun getCurrentFlashMode(): Int = currentFlashMode
    fun isFlashSupported(): Boolean = isFlashSupported
    
    fun getNextFlashMode(): Int {
        return if (!isFlashSupported) {
            FLASH_OFF
        } else {
            when (currentFlashMode) {
                FLASH_OFF -> FLASH_ON
                FLASH_ON -> FLASH_OFF
                else -> FLASH_OFF
            }
        }
    }
    
    fun cycleFlashMode() {
        setFlashMode(getNextFlashMode())
    }
    
    fun getFlashModeString(mode: Int = currentFlashMode): String {
        return when (mode) {
            FLASH_OFF -> "Off"
            FLASH_ON -> "On"
            else -> "Unknown"
        }
    }
    
    fun getFlashCapabilitiesString(): String {
        return if (isFlashSupported) {
            "Flash: Off, On (Torch)"
        } else {
            "Flash: Not supported"
        }
    }
}
