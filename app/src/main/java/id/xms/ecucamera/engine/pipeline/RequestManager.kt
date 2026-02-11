package id.xms.ecucamera.engine.pipeline

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.view.Surface

class RequestManager {
    
    companion object {
        private const val TAG = "ECU_PIPELINE"
    }
    fun createPreviewRequest(
        session: CameraCaptureSession, 
        target: Surface
    ): CaptureRequest {
        
        Log.d(TAG, "ECU_PIPELINE: Building Preview Request")     
        val requestBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(target)
        requestBuilder.apply {
            // Set control mode to AUTO for intelligent scene analysis
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            
            // Set autofocus to CONTINUOUS_PICTURE for smooth focus tracking
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            
            // Additional ECU defaults for optimal preview
            set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        
        Log.d(TAG, "Preview request configured with ECU defaults")
        return requestBuilder.build()
    }
   
    fun updateManualControl(
        requestBuilder: CaptureRequest.Builder,
        iso: Int? = null,
        exposure: Long? = null
    ) {
        Log.d(TAG, "Manual control update requested (ISO: $iso, Exposure: $exposure)")
        Log.d(TAG, "Manual controls not implemented yet - keeping auto mode")
    }
    
    fun createCaptureRequest(
        session: CameraCaptureSession,
        target: Surface
    ): CaptureRequest {
        
        Log.d(TAG, "ECU_PIPELINE: Building Capture Request")
        
        val requestBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        requestBuilder.addTarget(target)
        
        requestBuilder.apply {
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.JPEG_QUALITY, 95.toByte()) // High JPEG quality
        }
        
        return requestBuilder.build()
    }
}