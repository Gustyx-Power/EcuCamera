package id.xms.ecucamera.engine.core

import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Brain: Core Camera Engine that manages camera lifecycle and state
 * 
 * Philosophy: "Backend First" - Perfect hardware lifecycle before any UI
 */
class CameraEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_ENGINE"
    }
    
    // StateFlow for camera state management
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    // Camera system components
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCameraId: String? = null
    
    // Background thread for camera operations
    private val backgroundThread = HandlerThread("CameraEngineThread").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    // Coroutine scope for async operations
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
            Log.w(TAG, "🟡 Camera $currentCameraId DISCONNECTED")
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
            Log.e(TAG, "🔴 Camera $currentCameraId ERROR: $errorMsg")
            cleanup()
            updateState(CameraState.Error(errorMsg))
        }
    }
    
    /**
     * Capture Session State Callback - The Session Monitor
     */
    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "🟢 Camera Session CONFIGURED for camera $currentCameraId")
            captureSession = session
            updateState(CameraState.Configured)
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "🔴 Camera Session CONFIGURATION FAILED for camera $currentCameraId")
            updateState(CameraState.Error("Session configuration failed"))
        }
        
        override fun onClosed(session: CameraCaptureSession) {
            Log.d(TAG, "🟡 Camera Session CLOSED for camera $currentCameraId")
            captureSession = null
        }
    }
    
    /**
     * Open camera with specified ID
     */
    fun openCamera(cameraId: String) {
        engineScope.launch {
            try {
                Log.d(TAG, "🚀 Attempting to open camera: $cameraId")
                
                // Check if camera is already open
                if (_cameraState.value !is CameraState.Closed) {
                    Log.w(TAG, "⚠️ Camera already in use. Current state: ${_cameraState.value}")
                    return@launch
                }
                
                // Validate camera ID exists
                val availableCameras = cameraManager.cameraIdList
                if (!availableCameras.contains(cameraId)) {
                    val errorMsg = "Camera ID $cameraId not found. Available: ${availableCameras.joinToString()}"
                    Log.e(TAG, "🔴 $errorMsg")
                    updateState(CameraState.Error(errorMsg))
                    return@launch
                }
                
                currentCameraId = cameraId
                updateState(CameraState.Opening)
                
                // Open camera on background thread
                withContext(Dispatchers.Main) {
                    try {
                        cameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler)
                        Log.d(TAG, "📞 Camera open request sent for: $cameraId")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "🔴 Security exception opening camera $cameraId", e)
                        updateState(CameraState.Error("Camera permission denied", e))
                    } catch (e: Exception) {
                        Log.e(TAG, "🔴 Exception opening camera $cameraId", e)
                        updateState(CameraState.Error("Failed to open camera: ${e.message}", e))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Unexpected error in openCamera", e)
                updateState(CameraState.Error("Unexpected error: ${e.message}", e))
            }
        }
    }
    
    /**
     * Close the current camera
     */
    fun closeCamera() {
        engineScope.launch {
            Log.d(TAG, "🛑 Closing camera: $currentCameraId")
            cleanup()
            updateState(CameraState.Closed)
            Log.d(TAG, "✅ Camera closed successfully")
        }
    }
    
    /**
     * Configure capture session (minimal implementation for now)
     */
    fun configureSession() {
        engineScope.launch {
            try {
                val device = cameraDevice
                if (device == null) {
                    Log.e(TAG, "🔴 Cannot configure session: Camera device is null")
                    updateState(CameraState.Error("Camera device not available"))
                    return@launch
                }
                
                if (_cameraState.value !is CameraState.Open) {
                    Log.e(TAG, "🔴 Cannot configure session: Camera not in Open state. Current: ${_cameraState.value}")
                    return@launch
                }
                
                Log.d(TAG, "🔧 Configuring capture session for camera $currentCameraId")
                
                // For now, just create an empty session to test the callback
                // In Phase 4, we'll add actual surfaces here
                withContext(Dispatchers.Main) {
                    device.createCaptureSession(
                        emptyList(), // No surfaces yet - just testing the connection
                        sessionStateCallback,
                        backgroundHandler
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Error configuring session", e)
                updateState(CameraState.Error("Session configuration error: ${e.message}", e))
            }
        }
    }
    
    /**
     * Update the camera state and log the change
     */
    private fun updateState(newState: CameraState) {
        val oldState = _cameraState.value
        _cameraState.value = newState
        Log.d(TAG, "📊 State transition: $oldState → $newState")
    }
    
    /**
     * Clean up camera resources
     */
    private fun cleanup() {
        captureSession?.close()
        captureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        currentCameraId = null
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
        Log.d(TAG, "🧹 Destroying Camera Engine")
        engineScope.cancel()
        cleanup()
        backgroundThread.quitSafely()
        updateState(CameraState.Closed)
    }
}