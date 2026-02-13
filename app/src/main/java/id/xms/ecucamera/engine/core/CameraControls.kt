package id.xms.ecucamera.engine.core

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.util.Log
import android.view.Surface
import id.xms.ecucamera.engine.controller.ExposureController
import id.xms.ecucamera.engine.controller.FlashController
import id.xms.ecucamera.engine.controller.FocusController
import id.xms.ecucamera.engine.controller.ZoomController

class CameraControls(
    private val exposureController: ExposureController,
    private val focusController: FocusController,
    private val zoomController: ZoomController,
    private val flashController: FlashController
) {
    
    companion object {
        private const val TAG = "CameraControls"
    }
    
    private var isManualMode = false
    private var currentIso = 100
    private var currentExpTime = 10_000_000L
    
    private var isManualFocusMode = false
    private var currentFocusDistance = 0.0f
    
    fun setZoom(zoomLevel: Float) {
        zoomController.setZoom(zoomLevel)
    }
    
    fun setFlash(mode: Int) {
        flashController.setFlashMode(mode)
    }
    
    fun cycleFlash() {
        flashController.cycleFlashMode()
    }
    
    fun setManualFocusMode(enabled: Boolean) {
        isManualFocusMode = enabled
        Log.d(TAG, "Manual focus: ${if (enabled) "ON" else "OFF"}")
    }
    
    fun updateFocus(sliderValue: Float) {
        currentFocusDistance = focusController.calculateFocusDistance(sliderValue)
    }
    
    fun setManualMode(enabled: Boolean) {
        isManualMode = enabled
        Log.d(TAG, "Manual exposure: ${if (enabled) "ON" else "OFF"}")
    }
    
    fun updateISO(sliderValue: Float) {
        currentIso = exposureController.calculateISO(sliderValue)
    }
    
    fun updateShutter(sliderValue: Float) {
        currentExpTime = exposureController.calculateExposureTime(sliderValue)
    }
    
    fun applyToPreviewBuilder(builder: CaptureRequest.Builder) {
        if (zoomController.isZoomSupported()) {
            val zoomRect = zoomController.calculateZoomRect(zoomController.getZoomLevel())
            builder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
        }
        
        exposureController.applyToBuilder(builder, isManualMode, currentIso, currentExpTime)
        
        if (isManualFocusMode) {
            focusController.applyManualFocus(builder, currentFocusDistance)
        } else {
            focusController.applyAutoFocus(builder)
        }
        
        flashController.applyToBuilder(builder, isManualMode)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    }
    
    fun applyToCaptureBuilder(builder: CaptureRequest.Builder) {
        if (zoomController.isZoomSupported()) {
            val zoomRect = zoomController.calculateZoomRect(zoomController.getZoomLevel())
            builder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
        }
        
        exposureController.applyToBuilder(builder, isManualMode, currentIso, currentExpTime)
        
        if (isManualFocusMode) {
            focusController.applyManualFocus(builder, currentFocusDistance)
        } else {
            focusController.applyAutoFocus(builder)
        }
        
        flashController.applyToCaptureBuilder(builder, isManualMode)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    }
    
    fun updateRepeatingRequest(
        device: CameraDevice,
        session: CameraCaptureSession,
        viewFinder: Surface,
        readerSurface: Surface,
        backgroundHandler: Handler
    ) {
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(viewFinder)
            builder.addTarget(readerSurface)
            
            applyToPreviewBuilder(builder)
            
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }
    
    fun isInManualMode(): Boolean = isManualMode
    fun isInManualFocusMode(): Boolean = isManualFocusMode
}
