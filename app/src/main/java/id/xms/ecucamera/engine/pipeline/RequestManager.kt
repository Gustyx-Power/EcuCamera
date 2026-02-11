package id.xms.ecucamera.engine.pipeline

import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraMetadata
import android.os.Build
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
        
        Log.d(TAG, "Building Preview Request")     
        val requestBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(target)
        requestBuilder.apply {
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        
        Log.d(TAG, "Preview request configured with ECU defaults")
        return requestBuilder.build()
    }
    
    fun updateZoom(
        requestBuilder: CaptureRequest.Builder,
        zoomRatio: Float,
        activeRect: Rect
    ) {
        Log.d(TAG, "Updating zoom: ratio=$zoomRatio, activeRect=$activeRect")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                requestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
                Log.d(TAG, "Using CONTROL_ZOOM_RATIO for zoom $zoomRatio")
            } catch (e: Exception) {
                Log.w(TAG, "CONTROL_ZOOM_RATIO not supported, falling back to crop region")
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, activeRect)
            }
        } else {
            requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, activeRect)
            Log.d(TAG, "Using SCALER_CROP_REGION for zoom")
        }
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
        
        Log.d(TAG, "Building Capture Request")
        
        val requestBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        requestBuilder.addTarget(target)
        
        requestBuilder.apply {
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.JPEG_QUALITY, 95.toByte())
        }
        
        return requestBuilder.build()
    }
}