package id.xms.ecucamera.engine.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import id.xms.ecucamera.bridge.NativeBridge
import id.xms.ecucamera.engine.controller.ExposureController
import id.xms.ecucamera.engine.pipeline.SessionManager
import id.xms.ecucamera.engine.pipeline.RequestManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

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
    
    // Manual exposure control variables
    private var isManualMode = false
    private var currentIso = 100
    private var currentExpTime = 10_000_000L // 1/100s in nanoseconds
    
    private var testImageReader: ImageReader? = null
    private var imageReader: ImageReader? = null
    private var previewJob: Job? = null
    
    // Track current surfaces for manual exposure updates
    private var currentViewFinderSurface: Surface? = null
    
    private val backgroundThread = HandlerThread("CameraEngineThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Camera Device State Callback - The Heart Monitor
     */
    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera $currentCameraId Opened Successfully")
            cameraDevice = camera
            updateState(CameraState.Open)
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera $currentCameraId DISCONNECTED")
            cleanup()
            updateState(CameraState.Error("Camera disconnected"))
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            val errorMsg = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera is already in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                ERROR_CAMERA_DISABLED -> "Camera disabled by policy"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown camera error: $error"
            }
            Log.e(TAG, "Camera $currentCameraId ERROR: $errorMsg")
            cleanup()
            updateState(CameraState.Error(errorMsg))
        }
    }
    
    /**
     * Capture Session State Callback - The Session Monitor
     */
    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "Camera Session CONFIGURED for camera $currentCameraId")
            captureSession = session
            updateState(CameraState.Configured)
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Camera Session CONFIGURATION FAILED for camera $currentCameraId")
            updateState(CameraState.Error("Session configuration failed"))
        }
        
        override fun onClosed(session: CameraCaptureSession) {
            Log.d(TAG, "Camera Session CLOSED for camera $currentCameraId")
            captureSession = null
        }
    }
    
    /**
     * Open camera with specified ID
     */
    fun openCamera(cameraId: String) {
        engineScope.launch {
            try {
                Log.d(TAG, "Attempting to open camera: $cameraId")
                
                // Check if camera is already open
                if (_cameraState.value !is CameraState.Closed) {
                    Log.w(TAG, "Camera already in use. Current state: ${_cameraState.value}")
                    return@launch
                }
                
                // Validate camera ID exists
                val availableCameras = cameraManager.cameraIdList
                if (!availableCameras.contains(cameraId)) {
                    val errorMsg = "Camera ID $cameraId not found. Available: ${availableCameras.joinToString()}"
                    Log.e(TAG, "$errorMsg")
                    updateState(CameraState.Error(errorMsg))
                    return@launch
                }
                
                currentCameraId = cameraId
                updateState(CameraState.Opening)
                
                // Initialize exposure controller with camera characteristics
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    exposureController.setRanges(characteristics)
                    Log.d(TAG, "Exposure Controller initialized: ${exposureController.getISORangeString()}, ${exposureController.getExposureRangeString()}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize exposure controller", e)
                }
                
                // Open camera on background thread
                withContext(Dispatchers.Main) {
                    try {
                        cameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler)
                        Log.d(TAG, "Camera open request sent for: $cameraId")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception opening camera $cameraId", e)
                        updateState(CameraState.Error("Camera permission denied", e))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception opening camera $cameraId", e)
                        updateState(CameraState.Error("Failed to open camera: ${e.message}", e))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in openCamera", e)
                updateState(CameraState.Error("Unexpected error: ${e.message}", e))
            }
        }
    }
    
    /**
     * Close the current camera
     */
    fun closeCamera() {
        engineScope.launch {
            Log.d(TAG, "Closing camera: $currentCameraId")
            cleanup()
            updateState(CameraState.Closed)
            Log.d(TAG, "Camera closed successfully")
        }
    }
    
    fun switchCamera(newId: String, viewFinderSurface: Surface? = null, onAnalysis: ((String) -> Unit)? = null) {
        engineScope.launch {
            val oldId = currentCameraId
            
            if (newId == oldId) {
                Log.d(TAG, "Camera switch ignored - already using ID $newId")
                return@launch
            }
            
            Log.d(TAG, "ECU_LENS: Switching from ID $oldId to $newId")
            
            closeCamera()
            
            while (_cameraState.value !is CameraState.Closed) {
                delay(50)
            }
            
            openCamera(newId)
            
            while (_cameraState.value !is CameraState.Open) {
                delay(50)
            }
            
            viewFinderSurface?.let { surface ->
                startPreview(surface, onAnalysis)
            }
        }
    }
    
    suspend fun startPreview(viewFinderSurface: Surface, onAnalysis: ((String) -> Unit)? = null) {
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
                    Log.e(TAG, "Cannot start preview: Camera not in Open state. Current: ${_cameraState.value}")
                    return@launch
                }
                
                Log.d(TAG, "Starting preview for camera $currentCameraId")
                
                // Store current surface for manual exposure updates
                currentViewFinderSurface = viewFinderSurface
                
                this@CameraEngine.imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
                
                this@CameraEngine.imageReader!!.setOnImageAvailableListener({ reader ->
                    try {
                        val safeReader = this@CameraEngine.imageReader ?: return@setOnImageAvailableListener
                        val image = safeReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        
                        try {
                            Log.d("ECU_DEBUG", "Frame received! Timestamp: ${image.timestamp}")
                            
                            val plane = image.planes[0]
                            val stride = plane.rowStride
                            val pixelStride = plane.pixelStride
                            
                            // Use DirectByteBuffer directly - no copying needed
                            val buffer = plane.buffer
                            val length = buffer.remaining()
                            
                            Log.d("ECU_DEBUG", "Buffer info: length=$length, stride=$stride")
                            
                            try {
                                val result = NativeBridge.analyzeFrame(buffer, length, image.width, image.height, stride)
                                Log.d("ECU_RUST", "Result: $result")
                                
                                // Call the analysis callback if provided
                                onAnalysis?.invoke(result)
                            } catch (e: Exception) {
                                Log.e("ECU_ERROR", "Rust call failed: ${e.message}", e)
                            }
                            
                        } catch (e: Exception) {
                            Log.e("ECU_ERROR", "Frame processing failed", e)
                        } finally {
                            image.close()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("ECU_ERROR", "Image listener error", e)
                    }
                }, backgroundHandler)
                
                val targets = listOf(viewFinderSurface, this@CameraEngine.imageReader!!.surface)
                
                device.createCaptureSession(
                    targets,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            
                            try {
                                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                builder.addTarget(viewFinderSurface)
                                builder.addTarget(this@CameraEngine.imageReader!!.surface)
                                
                                // Apply exposure settings
                                if (isManualMode) {
                                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExpTime)
                                    Log.d(TAG, "Manual exposure applied: ISO=$currentIso, ExpTime=${currentExpTime}ns")
                                } else {
                                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                }
                                
                                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                
                                session.setRepeatingRequest(
                                    builder.build(),
                                    null,
                                    backgroundHandler
                                )
                                
                                updateState(CameraState.Configured)
                                Log.d(TAG, "ECU_ENGINE: Preview Running (Dual Output - Safe Mode)")
                                
                            } catch (e: IllegalStateException) {
                                Log.w(TAG, "Session closed during preview start")
                            } catch (e: CameraAccessException) {
                                Log.w(TAG, "Camera access error during preview start")
                            }
                        }
                        
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Camera Session CONFIGURATION FAILED")
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
    
    /**
     * Toggle between auto and manual exposure mode
     */
    fun setManualMode(enabled: Boolean) {
        engineScope.launch {
            isManualMode = enabled
            Log.d(TAG, "Manual exposure mode: ${if (enabled) "ENABLED" else "DISABLED"}")
            updateRepeatingRequest()
        }
    }
    
    /**
     * Update ISO value from slider (0.0 - 1.0)
     */
    fun updateISO(sliderValue: Float) {
        engineScope.launch {
            currentIso = exposureController.calculateISO(sliderValue)
            Log.d(TAG, "ISO updated to: $currentIso")
            if (isManualMode) {
                updateRepeatingRequest()
            }
        }
    }
    
    /**
     * Update shutter speed from slider (0.0 - 1.0)
     */
    fun updateShutter(sliderValue: Float) {
        engineScope.launch {
            currentExpTime = exposureController.calculateExposureTime(sliderValue)
            val seconds = currentExpTime / 1_000_000_000.0
            Log.d(TAG, "Exposure time updated to: ${currentExpTime}ns (${seconds}s)")
            if (isManualMode) {
                updateRepeatingRequest()
            }
        }
    }
    
    /**
     * Update the repeating capture request with current settings
     */
    private fun updateRepeatingRequest() {
        try {
            val device = cameraDevice ?: return
            val session = captureSession ?: return
            val reader = imageReader ?: return
            val viewFinder = currentViewFinderSurface ?: return
            
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(viewFinder)
            builder.addTarget(reader.surface)
            
            if (isManualMode) {
                // Set manual exposure mode
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExpTime)
                Log.d(TAG, "Manual mode: ISO=$currentIso, ExpTime=${currentExpTime}ns")
            } else {
                // Set auto exposure mode
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                Log.d(TAG, "Auto exposure mode enabled")
            }
            
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }
    
    /**
     * Get exposure controller for UI access
     */
    fun getExposureController(): ExposureController = exposureController
    
    /**
     * Check if manual exposure is supported
     */
    fun isManualExposureSupported(): Boolean = exposureController.isManualExposureSupported()
    
    /**
     * Get current manual mode state
     */
    fun isInManualMode(): Boolean = isManualMode

    /**
     * Update the camera state and log the change
     */
    private fun updateState(newState: CameraState) {
        val oldState = _cameraState.value
        _cameraState.value = newState
        Log.d(TAG, "State transition: $oldState → $newState")
    }
    
    /**
     * Clean up camera resources
     */
    private fun cleanup() {
        previewJob?.cancel()
        previewJob = null
        
        captureSession?.close()
        captureSession = null
        
        testImageReader?.close()
        testImageReader = null
        
        imageReader?.close()
        imageReader = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        currentCameraId = null
        currentViewFinderSurface = null
    }
    
    /**
     * Get current camera ID
     */
    fun getCurrentCameraId(): String? = currentCameraId
    
    /**
     * Check if camera is ready for operations
     */
    fun isReady(): Boolean = _cameraState.value is CameraState.Open || _cameraState.value is CameraState.Configured
    
    /**
     * Cleanup resources when engine is destroyed
     */
    fun destroy() {
        Log.d(TAG, "Destroying Camera Engine")
        engineScope.cancel()
        cleanup()
        backgroundThread.quitSafely()
        updateState(CameraState.Closed)
    }
}