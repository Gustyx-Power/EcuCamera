package id.xms.ecucamera.engine.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import id.xms.ecucamera.bridge.NativeBridge
import id.xms.ecucamera.engine.controller.ExposureController
import id.xms.ecucamera.engine.controller.FocusController
import id.xms.ecucamera.engine.controller.ZoomController
import id.xms.ecucamera.engine.controller.FlashController
import id.xms.ecucamera.engine.pipeline.SessionManager
import id.xms.ecucamera.engine.pipeline.RequestManager
import id.xms.ecucamera.ui.model.CameraMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

class CameraEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_ENGINE"
        private const val HARDWARE_ASPECT_RATIO = 4f / 3f
    }
    
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private val _previewAspectRatio = MutableStateFlow(HARDWARE_ASPECT_RATIO)
    val previewAspectRatio: StateFlow<Float> = _previewAspectRatio.asStateFlow()
    
    private val _targetCropRatio = MutableStateFlow(HARDWARE_ASPECT_RATIO)
    val targetCropRatio: StateFlow<Float> = _targetCropRatio.asStateFlow()
    
    private val _deviceOrientation = MutableStateFlow(0)
    val deviceOrientation: StateFlow<Int> = _deviceOrientation.asStateFlow()
    
    private var currentAspectRatio: Float = HARDWARE_ASPECT_RATIO
    private var screenAspectRatio: Float = 0f
    private var deviceRotation: Int = 0
    
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCameraId: String? = null
    private var lastCameraId: String? = null
    
    var lensFacing: Int = CameraCharacteristics.LENS_FACING_BACK
        private set
    
    private val sessionManager = SessionManager()
    private val requestManager = RequestManager()
    private val lensManager = LensManager()
    private val exposureController = ExposureController()
    private val focusController = FocusController()
    private val zoomController = ZoomController()
    private val flashController = FlashController()
    
    private val cameraControls = CameraControls(
        exposureController,
        focusController,
        zoomController,
        flashController
    )
    
    private val imageCaptureManager = ImageCaptureManager(context)
    private val videoCaptureManager = VideoCaptureManager(context)
    
    // Current camera mode (PHOTO or VIDEO)
    private var currentMode: CameraMode = CameraMode.PHOTO
    
    // Current zoom level
    private var currentZoom: Float = 1.0f
    
    // Current exposure compensation (EV adjustment)
    private var currentExposureCompensation: Int = 0
    
    /**
     * Set a callback to be invoked when an image is successfully saved.
     * @param callback Function that receives the saved image URI (or null on failure)
     */
    fun setOnImageSavedCallback(callback: (android.net.Uri?) -> Unit) {
        imageCaptureManager.setOnImageSavedCallback(callback)
    }
    
    private var testImageReader: ImageReader? = null
    private var imageReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var previewJob: Job? = null
    
    private var currentViewFinderSurface: Surface? = null
    private var currentRecorderSurface: Surface? = null
    
    private val backgroundThread = HandlerThread("CameraEngineThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var orientationEventListener: OrientationEventListener? = null
    private var currentDeviceOrientationDegrees: Int = 0
    private var sensorOrientation: Int = 90
    
    private fun initializeOrientationListener() {
        if (orientationEventListener != null) {
            orientationEventListener?.disable()
        }
        
        try {
            val cameraId = currentCameraId
            if (cameraId != null) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                Log.d(TAG, "Camera sensor orientation: $sensorOrientation°")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sensor orientation, using default 90°", e)
            sensorOrientation = 90
        }
        
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                val snappedOrientation = when (orientation) {
                    in 45..134 -> 90
                    in 135..224 -> 180
                    in 225..314 -> 270
                    else -> 0
                }
                
                if (currentDeviceOrientationDegrees != snappedOrientation) {
                    val oldOrientation = currentDeviceOrientationDegrees
                    currentDeviceOrientationDegrees = snappedOrientation
                    _deviceOrientation.value = snappedOrientation
                    Log.d(TAG, "Device orientation changed: $oldOrientation° -> $snappedOrientation° (raw: $orientation°)")
                }
            }
        }
        
        orientationEventListener?.enable()
        Log.d(TAG, "Orientation listener enabled")
    }
    
    fun setAspectRatio(aspectRatio: Float) {
        if (aspectRatio == currentAspectRatio) return
        
        Log.d(TAG, "setAspectRatio (virtual crop): ${String.format("%.3f", currentAspectRatio)} -> ${String.format("%.3f", aspectRatio)}")
        
        currentAspectRatio = aspectRatio
        
        val resolvedRatio = if (aspectRatio == 0f) {
            if (screenAspectRatio > 0f) screenAspectRatio else HARDWARE_ASPECT_RATIO
        } else {
            aspectRatio
        }
        
        _targetCropRatio.value = resolvedRatio
        
        Log.d(TAG, "setAspectRatio: targetCropRatio=${String.format("%.3f", resolvedRatio)}, stream untouched ✓")
    }
    
    fun setDeviceRotation(rotation: Int) {
        deviceRotation = rotation
        Log.d(TAG, "Device rotation set to: $rotation")
    }
    
    private fun getJpegOrientation(): Int {
        if (currentCameraId == null) return 0
        
        try {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
            
            val deviceDegrees = when (deviceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            
            val jpegOrientation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation - deviceDegrees + 360) % 360
            } else {
                (sensorOrientation + deviceDegrees) % 360
            }
            
            Log.d(TAG, "JPEG Orientation: sensor=$sensorOrientation, device=$deviceDegrees, final=$jpegOrientation")
            return jpegOrientation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate JPEG orientation", e)
            return 0
        }
    }
    
    private fun getOptimalPreviewSize(
        sizes: Array<android.util.Size>,
        targetWidth: Int,
        targetHeight: Int,
        targetAspectRatio: Float = HARDWARE_ASPECT_RATIO
    ): android.util.Size {
        val ASPECT_TOLERANCE = 0.1f
        val isPortrait = targetHeight > targetWidth
        
        Log.d(TAG, "getOptimalPreviewSize: screen=${targetWidth}x${targetHeight}, " +
                "isPortrait=$isPortrait, targetRatio=${String.format("%.3f", targetAspectRatio)}")
        
        val matchingAspectRatioSizes = sizes.filter { size ->
            val ratio = size.width.toFloat() / size.height.toFloat()
            abs(ratio - targetAspectRatio) < ASPECT_TOLERANCE
        }
        
        if (matchingAspectRatioSizes.isNotEmpty()) {
            val optimalSize = if (isPortrait) {
                matchingAspectRatioSizes
                    .filter { it.width <= targetHeight * 2 && it.height <= targetWidth * 2 }
                    .maxByOrNull { it.width * it.height }
                    ?: matchingAspectRatioSizes.maxByOrNull { it.width * it.height }!!
            } else {
                matchingAspectRatioSizes
                    .filter { it.width <= targetWidth * 2 && it.height <= targetHeight * 2 }
                    .maxByOrNull { it.width * it.height }
                    ?: matchingAspectRatioSizes.maxByOrNull { it.width * it.height }!!
            }
            
            Log.d(TAG, "Selected optimal preview size: ${optimalSize.width}x${optimalSize.height} " +
                    "(aspect ratio: ${String.format("%.3f", optimalSize.width.toFloat() / optimalSize.height)})")
            return optimalSize
        }
        
        val fallbackSize = sizes.maxByOrNull { it.width * it.height }!!
        Log.w(TAG, "No matching aspect ratio found, using fallback: ${fallbackSize.width}x${fallbackSize.height}")
        return fallbackSize
    }
    
    private fun getOptimalCaptureSize(sizes: Array<android.util.Size>): android.util.Size {
        val ASPECT_TOLERANCE = 0.1f
        
        val matchingAspectRatioSizes = sizes.filter { size ->
            val ratio = size.width.toFloat() / size.height.toFloat()
            abs(ratio - HARDWARE_ASPECT_RATIO) < ASPECT_TOLERANCE
        }
        
        if (matchingAspectRatioSizes.isNotEmpty()) {
            val optimalSize = matchingAspectRatioSizes.maxByOrNull { it.width * it.height }!!
            Log.d(TAG, "Selected optimal capture size (4:3 locked): ${optimalSize.width}x${optimalSize.height}")
            return optimalSize
        }
        
        val fallbackSize = sizes.maxByOrNull { it.width * it.height }!!
        Log.w(TAG, "No 4:3 capture size found, fallback: ${fallbackSize.width}x${fallbackSize.height}")
        return fallbackSize
    }

    
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
                lensManager.setAvailableCameras(cameraManager)
                
                currentCameraId = cameraId
                updateState(CameraState.Opening)
                
                // Update lens facing
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
                    Log.d(TAG, "Camera $cameraId lens facing: ${if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) "FRONT" else "BACK"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get lens facing, defaulting to BACK", e)
                    lensFacing = CameraCharacteristics.LENS_FACING_BACK
                }
                
                initializeOrientationListener()
                
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
    
    /**
     * Switch between PHOTO and VIDEO modes
     * This requires recreating the camera session with different surfaces
     */
    suspend fun switchMode(mode: CameraMode) {
        if (mode == currentMode) {
            Log.d(TAG, "Already in $mode mode")
            return
        }
        
        Log.d(TAG, "Switching mode: $currentMode -> $mode")
        
        val oldMode = currentMode
        currentMode = mode
        
        // Reset zoom to 1.0x when switching modes
        Log.d(TAG, "Resetting zoom to 1.0x for mode switch")
        zoomController.setZoom(1.0f)
        currentZoom = 1.0f
        
        // If we're switching from VIDEO mode while recording, stop recording
        if (oldMode == CameraMode.VIDEO && videoCaptureManager.isRecording) {
            Log.w(TAG, "Stopping active recording before mode switch")
            videoCaptureManager.stopRecording()
        }
        
        // Close current camera session
        val viewFinder = currentViewFinderSurface
        if (viewFinder != null && isReady()) {
            Log.d(TAG, "Recreating camera session for $mode mode")
            
            // Restart preview with new mode
            startPreview(viewFinder, null, null)
        } else {
            Log.d(TAG, "Mode switched to $mode (will apply on next camera start)")
        }
    }
    
    /**
     * Start video recording (only works in VIDEO mode)
     */
    fun startRecording() {
        if (currentMode != CameraMode.VIDEO) {
            Log.e(TAG, "Cannot start recording: not in VIDEO mode (current: $currentMode)")
            return
        }
        
        if (videoCaptureManager.isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        Log.d(TAG, "Starting video recording")
        videoCaptureManager.startRecording()
    }
    
    /**
     * Stop video recording
     */
    fun stopRecording(): File? {
        if (!videoCaptureManager.isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }
        
        Log.d(TAG, "Stopping video recording and restarting preview")
        
        try {
            // 1. Stop sending frames to the recorder
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
            
            // 2. Invalidate recorder surface before stopping
            currentRecorderSurface = null
            
            // 3. Finalize the video file
            val videoFile = videoCaptureManager.stopRecording()
            
            // 4. CRITICAL FIX: Restart preview to rebuild session with fresh MediaRecorder
            // This prevents the preview freeze by creating a new valid surface
            val viewFinder = currentViewFinderSurface
            if (viewFinder != null && currentMode == CameraMode.VIDEO) {
                Log.d(TAG, "Restarting preview session after video stop")
                engineScope.launch {
                    // Small delay to ensure MediaRecorder cleanup is complete
                    kotlinx.coroutines.delay(100)
                    startPreview(viewFinder, null, null)
                }
            } else {
                Log.w(TAG, "Cannot restart preview: viewFinder=$viewFinder, mode=$currentMode")
            }
            
            return videoFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            return null
        }
    }
    
    /**
     * Check if currently recording video
     */
    fun isRecording(): Boolean = videoCaptureManager.isRecording
    
    /**
     * Get current camera mode
     */
    fun getCurrentMode(): CameraMode = currentMode
    
    /**
     * Switch between front and back cameras.
     * Automatically determines the target camera ID based on current lens facing.
     * Handles flash safety: disables flash if front camera doesn't support it.
     */
    suspend fun switchCamera(): Boolean {
        return try {
            val oldId = currentCameraId
            if (oldId == null) {
                Log.e(TAG, "Cannot switch camera: no current camera")
                return false
            }
            
            // Determine new lens facing (toggle)
            val newLensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            
            // Find camera ID for new facing
            val newId = findCameraIdForLensFacing(newLensFacing)
            if (newId == null) {
                Log.w(TAG, "No camera found for lens facing: ${if (newLensFacing == CameraCharacteristics.LENS_FACING_FRONT) "FRONT" else "BACK"}")
                return false
            }
            
            if (newId == oldId) {
                Log.d(TAG, "Camera switch ignored - already using $newId")
                return false
            }
            
            Log.d(TAG, "Switching camera: $oldId (${if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) "FRONT" else "BACK"}) -> $newId (${if (newLensFacing == CameraCharacteristics.LENS_FACING_FRONT) "FRONT" else "BACK"})")
            
            // Close current camera
            closeCamera()
            while (_cameraState.value !is CameraState.Closed) {
                delay(50)
            }
            
            // Open new camera
            openCamera(newId)
            while (_cameraState.value !is CameraState.Open) {
                delay(50)
            }
            
            // Flash safety check for front camera
            if (newLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                if (!flashController.isFlashSupported()) {
                    Log.d(TAG, "Front camera has no flash unit - disabling flash")
                    flashController.setFlashMode(0) // OFF
                }
            }
            
            Log.d(TAG, "Camera switch completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch camera", e)
            false
        }
    }
    
    /**
     * Find camera ID for a specific lens facing.
     */
    private fun findCameraIdForLensFacing(targetLensFacing: Int): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == targetLensFacing
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find camera for lens facing", e)
            null
        }
    }
    
    /**
     * Legacy method for explicit camera ID switching (kept for compatibility).
     */
    fun switchCamera(newId: String, viewFinderSurface: Surface? = null, onAnalysis: ((String) -> Unit)? = null, onFocusPeaking: ((String) -> Unit)? = null) {
        engineScope.launch {
            val oldId = currentCameraId
            
            if (newId == oldId) {
                Log.d(TAG, "Camera switch ignored - already using $newId")
                return@launch
            }
            
            val availableCameras = cameraManager.cameraIdList
            if (!availableCameras.contains(newId)) {
                Log.w(TAG, "Camera $newId not available on this device. Available: ${availableCameras.joinToString()}. Staying on current camera $oldId.")
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
                
                if (_cameraState.value !is CameraState.Open && _cameraState.value !is CameraState.Configured) {
                    Log.e(TAG, "Cannot start preview: Camera not ready. Current: ${_cameraState.value}")
                    return@launch
                }
                
                Log.d(TAG, "Starting preview for camera $currentCameraId — LOCKED to 4:3")
                
                currentViewFinderSurface = viewFinderSurface
                
                val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                
                if (map == null) {
                    Log.e(TAG, "Cannot get stream configuration map")
                    updateState(CameraState.Error("Stream configuration not available"))
                    return@launch
                }
                
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                if (screenAspectRatio == 0f) {
                    screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
                    Log.d(TAG, "Screen aspect ratio: ${String.format("%.3f", screenAspectRatio)} (${screenWidth}x${screenHeight})")
                }
                
                val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
                val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
                
                val previewSize = getOptimalPreviewSize(yuvSizes, screenWidth, screenHeight, HARDWARE_ASPECT_RATIO)
                val captureSize = getOptimalCaptureSize(jpegSizes)
                
                _previewAspectRatio.value = HARDWARE_ASPECT_RATIO
                Log.d(TAG, "Preview locked to 4:3: ${previewSize.width}x${previewSize.height}")
                
                this@CameraEngine.imageReader = ImageReader.newInstance(
                    previewSize.width,
                    previewSize.height,
                    ImageFormat.YUV_420_888,
                    2
                )
                
                this@CameraEngine.jpegReader = ImageReader.newInstance(
                    captureSize.width,
                    captureSize.height,
                    ImageFormat.JPEG,
                    1
                )
                
                Log.d(TAG, "ImageReaders created - Preview: ${previewSize.width}x${previewSize.height}, " +
                        "Capture: ${captureSize.width}x${captureSize.height} (both 4:3)")
                
                try {
                    if (viewFinderSurface.isValid) {
                        Log.d(TAG, "Surface configured for preview size: ${previewSize.width}x${previewSize.height}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not configure surface fixed size: ${e.message}")
                }
                
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
                                
                                if (cameraControls.isInManualFocusMode()) {
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
                
                imageCaptureManager.setupJpegListener(
                    this@CameraEngine.jpegReader!!,
                    backgroundHandler,
                    targetCropRatio = { _targetCropRatio.value },
                    deviceOrientationDegrees = { currentDeviceOrientationDegrees },
                    sensorOrientation = { sensorOrientation }
                )
                
                // Prepare surfaces based on camera mode
                val targets = mutableListOf<Surface>()
                targets.add(viewFinderSurface)
                targets.add(this@CameraEngine.imageReader!!.surface)
                
                // Add mode-specific surfaces
                var recorderSurface: Surface? = null
                currentRecorderSurface = null // Reset before setup
                if (currentMode == CameraMode.VIDEO) {
                    try {
                        // Setup MediaRecorder and get its surface
                        recorderSurface = videoCaptureManager.setupMediaRecorder(
                            characteristics,
                            deviceRotation,
                            lensFacing
                        )
                        targets.add(recorderSurface)
                        currentRecorderSurface = recorderSurface // Persist for zoom/controls
                        Log.d(TAG, "Added MediaRecorder surface for VIDEO mode")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to setup MediaRecorder, falling back to PHOTO mode", e)
                        currentMode = CameraMode.PHOTO
                    }
                } else {
                    // PHOTO mode: add JPEG reader
                    targets.add(this@CameraEngine.jpegReader!!.surface)
                    Log.d(TAG, "Added JPEG surface for PHOTO mode")
                }
                
                device.createCaptureSession(
                    targets,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            
                            try {
                                // Use appropriate template based on mode
                                val template = if (currentMode == CameraMode.VIDEO) {
                                    CameraDevice.TEMPLATE_RECORD
                                } else {
                                    CameraDevice.TEMPLATE_PREVIEW
                                }
                                
                                val builder = device.createCaptureRequest(template)
                                builder.addTarget(viewFinderSurface)
                                builder.addTarget(this@CameraEngine.imageReader!!.surface)
                                
                                // Add recorder surface if in VIDEO mode
                                if (currentMode == CameraMode.VIDEO && recorderSurface != null) {
                                    builder.addTarget(recorderSurface)
                                    // Enable video stabilization to prevent jitter during zoom
                                    builder.set(
                                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                                    )
                                    Log.d(TAG, "Using TEMPLATE_RECORD for VIDEO mode (stabilization ON)")
                                } else {
                                    Log.d(TAG, "Using TEMPLATE_PREVIEW for PHOTO mode")
                                }
                                
                                cameraControls.applyToPreviewBuilder(builder)
                                
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                                
                                updateState(CameraState.Configured)
                                Log.d(TAG, "Preview running in $currentMode mode — 4:3 locked, virtual crop active ✓")
                                
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
                
                Log.d(TAG, "Taking picture — will software-crop to ${String.format("%.3f", _targetCropRatio.value)}")
                
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                builder.addTarget(jpegReader.surface)
                
                // CRITICAL: Apply all camera controls (zoom, focus, flash, etc.)
                cameraControls.applyToCaptureBuilder(builder)
                
                // CRITICAL FIX: Sync Exposure Compensation from preview to capture
                // This ensures the EV adjustment visible in preview is applied to the final image
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentExposureCompensation)
                Log.d(TAG, "Applied exposure compensation to capture: $currentExposureCompensation")
                
                // CRITICAL FIX: Sync Zoom crop region to ensure capture matches preview framing
                if (zoomController.isZoomSupported()) {
                    val cropRegion = zoomController.calculateZoomRect(currentZoom)
                    builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                    Log.d(TAG, "Applied zoom crop region to capture: ${currentZoom}x")
                }
                
                val jpegOrientation = getJpegOrientation()
                builder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                builder.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                
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
                val clampedZoom = if (zoomLevel < 1.0f) {
                    val targetCameraId = lensManager.getLensForZoom(zoomLevel)
                    val availableCameras = cameraManager.cameraIdList
                    
                    if (!availableCameras.contains(targetCameraId)) {
                        Log.w(TAG, "Zoom $zoomLevel would require camera $targetCameraId which is not available. Clamping to 1.0x")
                        1.0f
                    } else {
                        zoomLevel
                    }
                } else {
                    zoomLevel
                }
                
                // Update zoom in controller and get crop region
                val cropRegion = zoomController.setZoom(clampedZoom)
                currentZoom = clampedZoom
                
                // Apply zoom immediately to the active session
                val device = cameraDevice
                val session = captureSession
                val reader = imageReader
                val viewFinder = currentViewFinderSurface
                val isCurrentlyRecording = videoCaptureManager.isRecording && currentMode == CameraMode.VIDEO
                val recSurface = currentRecorderSurface
                
                if (device != null && session != null && reader != null && viewFinder != null) {
                    // Determine which template to use based on recording state
                    val template = if (isCurrentlyRecording) {
                        CameraDevice.TEMPLATE_RECORD
                    } else {
                        CameraDevice.TEMPLATE_PREVIEW
                    }
                    
                    val builder = device.createCaptureRequest(template)
                    builder.addTarget(viewFinder)
                    builder.addTarget(reader.surface)
                    
                    // CRITICAL: Add recorder surface when recording so video doesn't freeze
                    if (isCurrentlyRecording && recSurface != null) {
                        builder.addTarget(recSurface)
                        // Maintain video stabilization during zoom
                        builder.set(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
                        Log.d(TAG, "Zoom: recorder surface attached ✓")
                    }
                    
                    // Apply zoom crop region
                    builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                    
                    // Apply other camera controls
                    cameraControls.applyToPreviewBuilder(builder)
                    
                    // Update the repeating request immediately
                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                    
                    Log.d(TAG, "Zoom updated to ${clampedZoom}x (recording: ${videoCaptureManager.isRecording})")
                } else {
                    Log.w(TAG, "Cannot apply zoom: camera not ready")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set zoom", e)
            }
        }
    }
    
    fun setFlash(mode: Int) {
        engineScope.launch {
            cameraControls.setFlash(mode)
            updateRepeatingRequest()
        }
    }
    
    fun cycleFlash() {
        engineScope.launch {
            cameraControls.cycleFlash()
            updateRepeatingRequest()
        }
    }
    
    fun setManualFocusMode(enabled: Boolean) {
        engineScope.launch {
            cameraControls.setManualFocusMode(enabled)
            updateRepeatingRequest()
        }
    }
    
    fun updateFocus(sliderValue: Float) {
        engineScope.launch {
            cameraControls.updateFocus(sliderValue)
            if (cameraControls.isInManualFocusMode()) {
                updateRepeatingRequest()
            }
        }
    }
    
    /**
     * Trigger tap-to-focus at a specific point in the preview
     * 
     * @param x Touch X coordinate in view space
     * @param y Touch Y coordinate in view space
     * @param viewWidth Width of the preview view
     * @param viewHeight Height of the preview view
     */
    fun focusOnPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        Log.d("ECU_ENGINE", "focusOnPoint called: ($x, $y)")
        
        engineScope.launch {
            try {
                val cameraId = currentCameraId ?: run {
                    Log.e("ECU_ENGINE", "focusOnPoint: No camera ID available")
                    return@launch
                }
                val device = cameraDevice ?: run {
                    Log.e("ECU_ENGINE", "focusOnPoint: No camera device available")
                    return@launch
                }
                val session = captureSession ?: run {
                    Log.e("ECU_ENGINE", "focusOnPoint: No capture session available")
                    return@launch
                }
                
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: run {
                        Log.e("ECU_ENGINE", "focusOnPoint: No active array size available")
                        return@launch
                    }
                
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                
                val viewFinder = currentViewFinderSurface ?: run {
                    Log.e("ECU_ENGINE", "focusOnPoint: No viewfinder surface available")
                    return@launch
                }
                val reader = imageReader ?: run {
                    Log.e("ECU_ENGINE", "focusOnPoint: No image reader available")
                    return@launch
                }
                
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(viewFinder)
                builder.addTarget(reader.surface)
                
                focusController.focusOnPoint(
                    x, y,
                    viewWidth, viewHeight,
                    sensorOrientation,
                    activeArraySize,
                    builder
                )
                
                cameraControls.applyToPreviewBuilder(builder)
                session.capture(builder.build(), null, backgroundHandler)
                
                Log.d("ECU_ENGINE", "Tap-to-focus request sent ✓")
                
                delay(4000)
                
                val resetBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                resetBuilder.addTarget(viewFinder)
                resetBuilder.addTarget(reader.surface)
                
                focusController.cancelFocus(resetBuilder)
                cameraControls.applyToPreviewBuilder(resetBuilder)
                session.setRepeatingRequest(resetBuilder.build(), null, backgroundHandler)
                
                Log.d("ECU_ENGINE", "Returned to continuous AF ✓")
                
            } catch (e: Exception) {
                Log.e("ECU_ENGINE", "Failed to focus on point", e)
            }
        }
    }
    
    fun triggerAeAfLock(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        Log.d("ECU_ENGINE", "triggerAeAfLock called: ($x, $y)")
        
        engineScope.launch {
            try {
                val cameraId = currentCameraId ?: return@launch
                val device = cameraDevice ?: return@launch
                val session = captureSession ?: return@launch
                
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return@launch
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                
                val viewFinder = currentViewFinderSurface ?: return@launch
                val reader = imageReader ?: return@launch
                
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(viewFinder)
                builder.addTarget(reader.surface)
                
                focusController.triggerAeAfLock(
                    x, y,
                    viewWidth, viewHeight,
                    sensorOrientation,
                    activeArraySize,
                    builder
                )
                
                cameraControls.applyToPreviewBuilder(builder)
                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                
                Log.d("ECU_ENGINE", "AE/AF Lock applied ✓")
                
            } catch (e: Exception) {
                Log.e("ECU_ENGINE", "Failed to trigger AE/AF lock", e)
            }
        }
    }
    
    fun setExposureCompensation(sliderValue: Float) {
        engineScope.launch {
            try {
                val compensation = exposureController.calculateExposureCompensation(sliderValue)
                // Store the compensation value for use in takePicture()
                currentExposureCompensation = compensation
                // Also update CameraControls for consistency
                cameraControls.setExposureCompensation(compensation)
                updateRepeatingRequestWithExposure(compensation)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set exposure compensation", e)
            }
        }
    }
    
    private fun updateRepeatingRequestWithExposure(compensation: Int) {
        try {
            val device = cameraDevice ?: return
            val session = captureSession ?: return
            val reader = imageReader ?: return
            val viewFinder = currentViewFinderSurface ?: return
            val isCurrentlyRecording = videoCaptureManager.isRecording && currentMode == CameraMode.VIDEO
            val recSurface = currentRecorderSurface
            
            val template = if (isCurrentlyRecording) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }
            
            val builder = device.createCaptureRequest(template)
            builder.addTarget(viewFinder)
            builder.addTarget(reader.surface)
            
            // Include recorder surface when recording
            if (isCurrentlyRecording && recSurface != null) {
                builder.addTarget(recSurface)
                builder.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
            
            cameraControls.applyToPreviewBuilder(builder)
            exposureController.applyExposureCompensation(builder, compensation)
            
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request with exposure", e)
        }
    }
    
    fun setManualMode(enabled: Boolean) {
        engineScope.launch {
            cameraControls.setManualMode(enabled)
            updateRepeatingRequest()
        }
    }
    
    fun updateISO(sliderValue: Float) {
        engineScope.launch {
            cameraControls.updateISO(sliderValue)
            if (cameraControls.isInManualMode()) {
                updateRepeatingRequest()
            }
        }
    }
    
    fun updateShutter(sliderValue: Float) {
        engineScope.launch {
            cameraControls.updateShutter(sliderValue)
            if (cameraControls.isInManualMode()) {
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
            val isCurrentlyRecording = videoCaptureManager.isRecording && currentMode == CameraMode.VIDEO
            val recSurface = currentRecorderSurface
            
            cameraControls.updateRepeatingRequest(
                device, session, viewFinder, reader.surface, backgroundHandler,
                isRecording = isCurrentlyRecording,
                recorderSurface = recSurface
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }
    
    fun getFocusController(): FocusController = focusController
    fun isManualFocusSupported(): Boolean = focusController.isManualFocusSupported()
    fun isInManualFocusMode(): Boolean = cameraControls.isInManualFocusMode()
    
    fun getExposureController(): ExposureController = exposureController
    fun isManualExposureSupported(): Boolean = exposureController.isManualExposureSupported()
    fun isInManualMode(): Boolean = cameraControls.isInManualMode()
    
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
        
        // Cleanup video resources
        currentRecorderSurface = null
        videoCaptureManager.cleanup()
        
        orientationEventListener?.disable()
        orientationEventListener = null
        currentDeviceOrientationDegrees = 0
        
        if (currentCameraId != null) lastCameraId = currentCameraId
        currentCameraId = null
        currentViewFinderSurface = null
    }
    
    fun getCurrentCameraId(): String? = currentCameraId
    fun getLastCameraId(): String? = lastCameraId
    val isClosed: Boolean get() = _cameraState.value is CameraState.Closed
    fun isReady(): Boolean = _cameraState.value is CameraState.Open || _cameraState.value is CameraState.Configured
    
    fun getAvailableCameraIds(): List<String> {
        return try {
            cameraManager.cameraIdList.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available cameras", e)
            emptyList()
        }
    }
    
    fun isCameraAvailable(cameraId: String): Boolean {
        return getAvailableCameraIds().contains(cameraId)
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying Camera Engine")
        orientationEventListener?.disable()
        orientationEventListener = null
        engineScope.cancel()
        cleanup()
        backgroundThread.quitSafely()
        updateState(CameraState.Closed)
    }
}
