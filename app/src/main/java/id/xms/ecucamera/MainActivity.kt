package id.xms.ecucamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
import id.xms.ecucamera.ui.screens.CameraScreen
import id.xms.ecucamera.ui.theme.EcuCameraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ECU_MAIN"
    }
    
    lateinit var cameraEngine: CameraEngine
    private lateinit var hardwareProbe: HardwareProbe
    private var currentSurface: Surface? = null
    
    private var histogramDataCsv by mutableStateOf("")
    private var lastHistogramUpdate by mutableStateOf(0L)
    
    private var currentZoom by mutableStateOf(1.0f)
    private var scaleGestureDetector: ScaleGestureDetector? = null
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            checkStoragePermission()
        } else {
            Log.e(TAG, "Camera permission denied")
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted")
            onPermissionsReady()
        } else {
            Log.e(TAG, "Storage permission denied")
            onPermissionsReady()
        }
    }
    
    private val mediaImagesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Media images permission granted")
        } else {
            Log.d(TAG, "Media images permission denied - gallery thumbnail may not work")
        }
        onPermissionsReady()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "EcuCamera starting")
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Configure immersive mode - hide system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController?.apply {
            // Hide both status bar and navigation bar
            hide(WindowInsetsCompat.Type.systemBars())
            // Allow transient bars to show on swipe
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        Log.d(TAG, "Immersive mode enabled")
        
        cameraEngine = CameraEngine(this)
        hardwareProbe = HardwareProbe(this)
        
        // Set initial device rotation
        updateDeviceRotation()
        
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newZoom = cameraEngine.getZoomController().calculateZoomFromGesture(scaleFactor, currentZoom)
                
                if (newZoom != currentZoom) {
                    currentZoom = newZoom
                    cameraEngine.setZoom(newZoom)
                }
                return true
            }
        })
        
        checkAndRequestCameraPermission()
        
        setContent {
            val cameraState by cameraEngine.cameraState.collectAsState()
            val previewAspectRatio by cameraEngine.previewAspectRatio.collectAsState()
            val targetCropRatio by cameraEngine.targetCropRatio.collectAsState()
            val deviceOrientation by cameraEngine.deviceOrientation.collectAsState()
            val lensFacing = cameraEngine.lensFacing
            
            EcuCameraTheme {
                CameraScreen(
                    histogramData = histogramDataCsv,
                    cameraState = cameraState,
                    previewAspectRatio = previewAspectRatio,
                    targetCropRatio = targetCropRatio,
                    deviceOrientation = deviceOrientation,
                    lensFacing = lensFacing,
                    onSurfaceReady = { surface ->
                        Log.d(TAG, "onSurfaceReady: Surface stored")
                        currentSurface = surface
                    },
                    onSurfaceDestroyed = {
                        Log.d(TAG, "onSurfaceDestroyed")
                        currentSurface = null
                        cameraEngine.closeCamera()
                    },
                    onSurfaceChanged = { surface ->
                        Log.d(TAG, "onSurfaceChanged: valid=${surface.isValid}, state=${cameraEngine.cameraState.value}")
                        currentSurface = surface
                        startCameraOnSurface(surface)
                    },
                    onTouchEvent = { event ->
                        scaleGestureDetector?.onTouchEvent(event) ?: false
                    },
                    onZoomChange = { zoom ->
                        currentZoom = zoom
                        cameraEngine.setZoom(zoom)
                    },
                    onFlashModeChange = { mode ->
                        cameraEngine.setFlash(mode)
                    },
                    onShutterClick = {
                        cameraEngine.takePicture()
                    },
                    onSwitchCamera = {
                        val success = cameraEngine.switchCamera()
                        if (success) {
                            // Restart preview with current surface
                            currentSurface?.let { surface ->
                                lifecycleScope.launch {
                                    cameraEngine.startPreview(surface,
                                        onAnalysis = { csvData ->
                                            val now = System.currentTimeMillis()
                                            if (now - lastHistogramUpdate > 33) {
                                                histogramDataCsv = csvData
                                                lastHistogramUpdate = now
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    onAspectRatioChange = { ratio ->
                        cameraEngine.setAspectRatio(ratio.toFloat())
                    },
                    onManualModeChange = { isManual ->
                        cameraEngine.setManualMode(isManual)
                    },
                    onIsoChange = { value ->
                        cameraEngine.updateISO(value)
                    },
                    onShutterChange = { value ->
                        cameraEngine.updateShutter(value)
                    },
                    onFocusChange = { value ->
                        cameraEngine.updateFocus(value)
                    },
                    onTapToFocus = { x, y, viewWidth, viewHeight ->
                        cameraEngine.focusOnPoint(x, y, viewWidth, viewHeight)
                    },
                    onLongPressLock = { x, y, viewWidth, viewHeight ->
                        cameraEngine.triggerAeAfLock(x, y, viewWidth, viewHeight)
                    },
                    onExposureAdjust = { level ->
                        cameraEngine.setExposureCompensation(level)
                    },
                    onCloseApp = { finish() }
                )
            }
        }
    }
    
    private fun checkAndRequestCameraPermission() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
                checkStoragePermission()
            }
            else -> {
                Log.d(TAG, "Requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires READ_MEDIA_IMAGES
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Media images permission already granted")
                    onPermissionsReady()
                }
                else -> {
                    Log.d(TAG, "Requesting media images permission")
                    mediaImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Android 10-12: No storage permission needed")
            onPermissionsReady()
        } else {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Storage permission already granted")
                    onPermissionsReady()
                }
                else -> {
                    Log.d(TAG, "Requesting storage permission")
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    private var isEngineReady = false
    
    /** Runs hardware probe & pipeline validation. Idempotent. */
    private fun onPermissionsReady() {
        if (isEngineReady) return
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Running hardware probe & pipeline validation")
                hardwareProbe.dumpCapabilities()
                PipelineValidator.logPipelineArchitecture()
                
                if (!PipelineValidator.validatePipelineComponents()) {
                    Log.e(TAG, "Pipeline validation failed")
                    return@launch
                }
                
                isEngineReady = true
                Log.d(TAG, "Engine ready")
                
                // If Surface already exists (permissions arrived after surface), start now
                currentSurface?.let { s ->
                    if (s.isValid && cameraEngine.isClosed) startCameraOnSurface(s)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize engine", e)
            }
        }
    }
    
    /** Opens the camera on the given valid surface. Guarded: only runs when engine is ready and camera is closed. */
    private fun startCameraOnSurface(surface: Surface) {
        if (!isEngineReady || !surface.isValid || !cameraEngine.isClosed) {
            Log.d(TAG, "startCameraOnSurface: skipped (ready=$isEngineReady, valid=${surface.isValid}, closed=${cameraEngine.isClosed})")
            return
        }
        
        val cameraId = cameraEngine.getLastCameraId() ?: "0"
        Log.d(TAG, "startCameraOnSurface: Opening camera $cameraId")
        
        lifecycleScope.launch {
            cameraEngine.openCamera(cameraId)
            
            val state = cameraEngine.cameraState.first { it is CameraState.Open || it is CameraState.Error }
            
            if (state is CameraState.Open) {
                Log.d(TAG, "Camera $cameraId opened, starting preview")
                cameraEngine.startPreview(surface,
                    onAnalysis = { csvData ->
                        val now = System.currentTimeMillis()
                        if (now - lastHistogramUpdate > 33) {
                            histogramDataCsv = csvData
                            lastHistogramUpdate = now
                        }
                    }
                )
            } else {
                Log.e(TAG, "Camera $cameraId failed to open: $state")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying MainActivity")
        cameraEngine.destroy()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
    
    /**
     * Updates the device rotation in the camera engine.
     * This ensures captured images have the correct orientation.
     */
    private fun updateDeviceRotation() {
        val rotation = windowManager.defaultDisplay.rotation
        cameraEngine.setDeviceRotation(rotation)
        Log.d(TAG, "Device rotation updated: $rotation")
    }
}
