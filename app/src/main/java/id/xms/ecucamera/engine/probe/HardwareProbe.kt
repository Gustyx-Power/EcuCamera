package id.xms.ecucamera.engine.probe

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Spy: Hardware probe that scans device capabilities for logging
 * 
 * Purpose: Dump RAW capabilities of the Poco F5 to understand what we're working with
 */
class HardwareProbe(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_PROBE"
    }
    
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    /**
     * Dump all camera capabilities to logcat
     * This is our intelligence gathering mission
     */
    suspend fun dumpCapabilities() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Starting Hardware Probe - Scanning Device Capabilities")
            Log.d(TAG, "============================================================")
            
            val cameraIds = cameraManager.cameraIdList
            Log.d(TAG, "📱 Found ${cameraIds.size} camera(s): ${cameraIds.joinToString()}")
            
            cameraIds.forEachIndexed { index, cameraId ->
                dumpCameraCapabilities(cameraId, index)
                if (index < cameraIds.size - 1) {
                    Log.d(TAG, "----------------------------------------")
                }
            }
            
            Log.d(TAG, "============================================================")
            Log.d(TAG, "✅ Hardware Probe Complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Hardware Probe Failed", e)
        }
    }
    
    /**
     * Dump capabilities for a specific camera
     */
    private fun dumpCameraCapabilities(cameraId: String, index: Int) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            
            // Lens Facing
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val facingStr = when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }
            Log.d(TAG, "Found Camera $cameraId ($facingStr)")
            
            // Hardware Level
            val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val levelStr = when (hardwareLevel) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                else -> "UNKNOWN ($hardwareLevel)"
            }
            Log.d(TAG, "   🔧 Hardware Level: $levelStr")
            
            // Max Zoom
            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            Log.d(TAG, "Max Zoom: ${String.format("%.1f", maxZoom)}x")
            
            // Stream Configuration Map
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (streamConfigMap != null) {
                Log.d(TAG, "   📐 Available Stream Configurations:")
                
                // Output formats
                val outputFormats = streamConfigMap.outputFormats
                Log.d(TAG, "      🎯 Output Formats: ${outputFormats.size} formats")
                outputFormats.forEach { format ->
                    val formatName = getFormatName(format)
                    val sizes = streamConfigMap.getOutputSizes(format)
                    Log.d(TAG, "         $formatName: ${sizes.size} sizes")
                    
                    // Log first few sizes to avoid spam
                    sizes.take(3).forEach { size ->
                        Log.d(TAG, "           ${size.width}x${size.height}")
                    }
                    if (sizes.size > 3) {
                        Log.d(TAG, "           ... and ${sizes.size - 3} more")
                    }
                }
            }
            
            // Sensor info
            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (sensorSize != null) {
                Log.d(TAG, "   📏 Sensor Size: ${String.format("%.2f", sensorSize.width)}mm x ${String.format("%.2f", sensorSize.height)}mm")
            }
            
            val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            if (pixelArraySize != null) {
                Log.d(TAG, "   🔲 Pixel Array: ${pixelArraySize.width}x${pixelArraySize.height}")
            }
            
            // Available capabilities
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            if (capabilities != null) {
                Log.d(TAG, "   ⚡ Capabilities:")
                capabilities.forEach { capability ->
                    val capabilityName = getCapabilityName(capability)
                    Log.d(TAG, "      $capabilityName")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Failed to dump capabilities for camera $cameraId", e)
        }
    }
    
    /**
     * Convert format constant to readable name
     */
    private fun getFormatName(format: Int): String = when (format) {
        android.graphics.ImageFormat.JPEG -> "JPEG"
        android.graphics.ImageFormat.YUV_420_888 -> "YUV_420_888"
        android.graphics.ImageFormat.NV21 -> "NV21"
        android.graphics.ImageFormat.NV16 -> "NV16"
        android.graphics.ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
        android.graphics.ImageFormat.RAW10 -> "RAW10"
        android.graphics.ImageFormat.RAW12 -> "RAW12"
        android.graphics.ImageFormat.PRIVATE -> "PRIVATE"
        else -> "FORMAT_$format"
    }
    
    /**
     * Convert capability constant to readable name
     */
    private fun getCapabilityName(capability: Int): String = when (capability) {
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "HIGH_SPEED_VIDEO"
        else -> "CAPABILITY_$capability"
    }
}