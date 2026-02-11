package id.xms.ecucamera.engine.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import id.xms.ecucamera.bridge.NativeBridge
import id.xms.ecucamera.engine.controller.CaptureController
import id.xms.ecucamera.engine.controller.ExposureController
import id.xms.ecucamera.engine.controller.FocusController
import id.xms.ecucamera.engine.controller.ZoomController
import id.xms.ecucamera.engine.controller.FlashController
import id.xms.ecucamera.engine.pipeline.SessionManager
import id.xms.ecucamera.engine.pipeline.RequestManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_ENGINE"
    }
    
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCameraId: String? = null
    
    private val sessionManager = SessionManager()
    private val requestManager = RequestManager()
    private val lensManager = LensManager()
    private val exposureController = ExposureController()
    private val focusController = FocusController()
    private val zoomController = ZoomController()
    private val flashController = FlashController()
    private val captureController = CaptureController(context)
    
    private var isManualMode = false
    private var currentIso = 100
    private var currentExpTime = 10_000_000L
    
    private var isManualFocusMode = false
    private var currentFocusDistance = 0.0f
    
    private var testImageReader: ImageReader? = null
    private var imageReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var previewJob: Job? = null
    
    private var currentViewFinderSurface: Surface? = null
    
    private val backgroundThread = HandlerThread("CameraEngineThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera $currentCameraId opened")
            cameraDevice = camera
            updateState(CameraState.Open)
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera $currentCameraId disconnected")
            cleanup()
            updateState(CameraState.Error("Camera disconnected"))
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            val errorMsg = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "Camera $currentCameraId error: $errorMsg")
            cleanup()
            updateState(CameraState.Error(errorMsg))
        }
    }
    
    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "Session configured for camera $currentCameraId")
            captureSession = session
            updateState(CameraState.Configured)
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Session configuration failed for camera $currentCameraId")
            updateState(CameraState.Error("Session configuration failed"))
        }
        
        override fun onClosed(session: CameraCaptureSession) {
            Log.d(TAG, "Session closed for camera $currentCameraId")
            captureSession = null
        }
    }
    
    fun openCamera(cameraId: String) {
        engineScope.launch {
            try {
                Log.d(TAG, "Opening camera: $cameraId")
                
                if (_cameraState.value !is CameraState.Closed) {
                    Log.w(TAG, "Camera already in use: ${_cameraState.value}")
                    return@launch
                }
                
                val availableCameras = cameraManager.cameraIdList
                if (!availableCameras.contains(cameraId)) {
                    val errorMsg = "Camera $cameraId not found. Available: ${availableCameras.joinToString()}"
                    Log.e(TAG, errorMsg)
                    updateState(CameraState.Error(errorMsg))
                    return@launch
                }
                
                currentCameraId = cameraId
                updateState(CameraState.Opening)
                
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    exposureController.setRanges(characteristics)
                    focusController.setCapabilities(characteristics)
                    zoomController.setCharacteristics(characteristics)
                    flashController.setCapabilities(characteristics)
                    Log.d(TAG, "Controllers initialized: ${exposureController.getISORangeString()}, ${focusController.getFocusRangeString()}, ${zoomController.getZoomRangeString()}, ${flashController.getFlashCapabilitiesString()}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize controllers", e)
                }
                
                withContext(Dispatchers.Main) {
                    try {
                        cameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception opening camera", e)
                        updateState(CameraState.Error("Camera permission denied", e))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception opening camera", e)
                        updateState(CameraState.Error("Failed to open camera: ${e.message}", e))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in openCamera", e)
                updateState(CameraState.Error("Unexpected error: ${e.message}", e))
            }
        }
    }
    
    fun closeCamera() {
        engineScope.launch {
            Log.d(TAG, "Closing camera: $currentCameraId")
            cleanup()
            updateState(CameraState.Closed)
        }
    }
    
    fun switchCamera(newId: String, viewFinderSurface: Surface? = null, onAnalysis: ((String) -> Unit)? = null, onFocusPeaking: ((String) -> Unit)? = null) {
        engineScope.launch {
            val oldId = currentCameraId
            
            if (newId == oldId) {
                Log.d(TAG, "Camera switch ignored - already using $newId")
                return@launch
            }
            
            Log.d(TAG, "Switching from $oldId to $newId")
            
            closeCamera()
            
            while (_cameraState.value !is CameraState.Closed) {
                delay(50)
            }
            
            openCamera(newId)
            
            while (_cameraState.value !is CameraState.Open) {
                delay(50)
            }
            
            viewFinderSurface?.let { surface ->
                startPreview(surface, onAnalysis, onFocusPeaking)
            }
        }
    }
    
    suspend fun startPreview(viewFinderSurface: Surface, onAnalysis: ((String) -> Unit)? = null, onFocusPeaking: ((String) -> Unit)? = null) {
        previewJob?.cancel()
        previewJob = engineScope.launch {
            try {
                val device = cameraDevice
                if (device == null) {
                    Log.e(TAG, "Cannot start preview: Camera device is null")
                    updateState(CameraState.Error("Camera device not available"))
                    return@launch
                }
                
                if (_cameraState.value !is CameraState.Open) {
                    Log.e(TAG, "Cannot start preview: Camera not open. Current: ${_cameraState.value}")
                    return@launch
                }
                
                Log.d(TAG, "Starting preview for camera $currentCameraId")
                
                currentViewFinderSurface = viewFinderSurface
                
                this@CameraEngine.imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
                this@CameraEngine.jpegReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
                
                this@CameraEngine.imageReader!!.setOnImageAvailableListener({ reader ->
                    try {
                        val safeReader = this@CameraEngine.imageReader ?: return@setOnImageAvailableListener
                        val image = safeReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        
                        try {
                            val plane = image.planes[0]
                            val stride = plane.rowStride
                            val buffer = plane.buffer
                            val length = buffer.remaining()
                            
                            try {
                                val result = NativeBridge.analyzeFrame(buffer, length, image.width, image.height, stride)
                                onAnalysis?.invoke(result)
                                
                                if (isManualFocusMode) {
                                    try {
                                        val focusResult = NativeBridge.analyzeFocusPeaking(buffer, length, image.width, image.height, stride)
                                        onFocusPeaking?.invoke(focusResult)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Focus peaking failed: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Rust call failed: ${e.message}")
                            }
                            
                        } finally {
                            image.close()
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Image listener error", e)
                    }
                }, backgroundHandler)
                
                this@CameraEngine.jpegReader!!.setOnImageAvailableListener({ reader ->
                    try {
                        val safeReader = this@CameraEngine.jpegReader ?: return@setOnImageAvailableListener
                        val image = safeReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        
                        try {
                            Log.d(TAG, "JPEG captured: ${image.width}x${image.height}")
                            
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            
                            val uri = captureController.saveImage(buffer, 0)
                            if (uri != null) {
                                Log.d(TAG, "Photo saved to: $uri")
                            } else {
                                Log.e(TAG, "Failed to save photo")
                            }
                            
                        } finally {
                            image.close()
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "JPEG listener error", e)
                    }
                }, backgroundHandler)
                
                val targets = listOf(viewFinderSurface, this@CameraEngine.imageReader!!.surface, this@CameraEngine.jpegReader!!.surface)
                
                device.createCaptureSession(
                    targets,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            
                            try {
                                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                builder.addTarget(viewFinderSurface)
                                builder.addTarget(this@CameraEngine.imageReader!!.surface)
                                
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
                                
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                                
                                updateState(CameraState.Configured)
                                Log.d(TAG, "Preview running")
                                
                            } catch (e: IllegalStateException) {
                                Log.w(TAG, "Session closed during preview start")
                            } catch (e: CameraAccessException) {
                                Log.w(TAG, "Camera access error during preview start")
                            }
                        }
                        
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Session configuration failed")
                            updateState(CameraState.Error("Session configuration failed"))
                        }
                    },
                    backgroundHandler
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting preview", e)
                updateState(CameraState.Error("Preview start error: ${e.message}", e))
            }
        }
    }
    
    fun takePicture() {
        engineScope.launch {
            try {
                val device = cameraDevice
                val session = captureSession
                val jpegReader = jpegReader
                
                if (device == null || session == null || jpegReader == null) {
                    Log.e(TAG, "Cannot take picture: Camera not ready")
                    return@launch
                }
                
                Log.d(TAG, "Taking picture")
                
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                builder.addTarget(jpegReader.surface)
                
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
                
                builder.set(CaptureRequest.JPEG_ORIENTATION, 0)
                builder.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                
                session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d(TAG, "Capture completed")
                    }
                    
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        Log.e(TAG, "Capture failed: ${failure.reason}")
                    }
                }, backgroundHandler)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take picture", e)
            }
        }
    }

    fun setZoom(zoomLevel: Float) {
        engineScope.launch {
            try {
                zoomController.setZoom(zoomLevel)
                updateRepeatingRequest()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set zoom", e)
            }
        }
    }
    
    fun setFlash(mode: Int) {
        engineScope.launch {
            flashController.setFlashMode(mode)
            updateRepeatingRequest()
        }
    }
    
    fun cycleFlash() {
        engineScope.launch {
            flashController.cycleFlashMode()
            updateRepeatingRequest()
        }
    }

    fun setManualFocusMode(enabled: Boolean) {
        engineScope.launch {
            isManualFocusMode = enabled
            Log.d(TAG, "Manual focus: ${if (enabled) "ON" else "OFF"}")
            updateRepeatingRequest()
        }
    }
    
    fun updateFocus(sliderValue: Float) {
        engineScope.launch {
            currentFocusDistance = focusController.calculateFocusDistance(sliderValue)
            if (isManualFocusMode) {
                updateRepeatingRequest()
            }
        }
    }
    
    fun getFocusController(): FocusController = focusController
    fun isManualFocusSupported(): Boolean = focusController.isManualFocusSupported()
    fun isInManualFocusMode(): Boolean = isManualFocusMode

    fun setManualMode(enabled: Boolean) {
        engineScope.launch {
            isManualMode = enabled
            Log.d(TAG, "Manual exposure: ${if (enabled) "ON" else "OFF"}")
            updateRepeatingRequest()
        }
    }
    
    fun updateISO(sliderValue: Float) {
        engineScope.launch {
            currentIso = exposureController.calculateISO(sliderValue)
            if (isManualMode) {
                updateRepeatingRequest()
            }
        }
    }
    
    fun updateShutter(sliderValue: Float) {
        engineScope.launch {
            currentExpTime = exposureController.calculateExposureTime(sliderValue)
            if (isManualMode) {
                updateRepeatingRequest()
            }
        }
    }
    
    private fun updateRepeatingRequest() {
        try {
            val device = cameraDevice ?: return
            val session = captureSession ?: return
            val reader = imageReader ?: return
            val viewFinder = currentViewFinderSurface ?: return
            
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(viewFinder)
            builder.addTarget(reader.surface)
            
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
            
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }
    
    fun getExposureController(): ExposureController = exposureController
    fun isManualExposureSupported(): Boolean = exposureController.isManualExposureSupported()
    fun isInManualMode(): Boolean = isManualMode
    
    fun getZoomController(): ZoomController = zoomController
    fun getFlashController(): FlashController = flashController
    
    fun isZoomSupported(): Boolean = zoomController.isZoomSupported()
    fun isFlashSupported(): Boolean = flashController.isFlashSupported()

    private fun updateState(newState: CameraState) {
        _cameraState.value = newState
    }
    
    private fun cleanup() {
        previewJob?.cancel()
        previewJob = null
        
        captureSession?.close()
        captureSession = null
        
        testImageReader?.close()
        testImageReader = null
        
        imageReader?.close()
        imageReader = null
        
        jpegReader?.close()
        jpegReader = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        currentCameraId = null
        currentViewFinderSurface = null
    }
    
    fun getCurrentCameraId(): String? = currentCameraId
    fun isReady(): Boolean = _cameraState.value is CameraState.Open || _cameraState.value is CameraState.Configured
    
    fun destroy() {
        Log.d(TAG, "Destroying Camera Engine")
        engineScope.cancel()
        cleanup()
        backgroundThread.quitSafely()
        updateState(CameraState.Closed)
    }
}
