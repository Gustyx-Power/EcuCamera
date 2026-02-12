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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.controller.FlashController
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
import id.xms.ecucamera.ui.screens.CameraScreen
import id.xms.ecucamera.ui.theme.EcuCameraTheme
import kotlinx.coroutines.delay
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
            startCameraEngine()
        } else {
            Log.e(TAG, "Storage permission denied")
            startCameraEngine()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "EcuCamera starting")
        
        cameraEngine = CameraEngine(this)
        hardwareProbe = HardwareProbe(this)
        
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
            
            EcuCameraTheme {
                CameraScreen(
                    histogramData = histogramDataCsv,
                    cameraState = cameraState,
                    onSurfaceReady = { surface ->
                        currentSurface = surface
                        startCameraEngine()
                    },
                    onSurfaceDestroyed = {
                        currentSurface = null
                        cameraEngine.closeCamera()
                    },
                    onTouchEvent = { event ->
                        scaleGestureDetector?.onTouchEvent(event) ?: false
                    },
                    onZoomChange = { zoom ->
                        currentZoom = zoom
                        cameraEngine.setZoom(zoom)
                    },
                    onFlashToggle = {
                        cameraEngine.cycleFlash()
                    },
                    onShutterClick = {
                        cameraEngine.takePicture()
                    },
                    onSwitchCamera = {
                        
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Android 10+: No storage permission needed")
            startCameraEngine()
            return
        }
        
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Storage permission already granted")
                startCameraEngine()
            }
            else -> {
                Log.d(TAG, "Requesting storage permission")
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun startCameraEngine() {
        val surface = currentSurface
        if (surface == null) {
            Log.w(TAG, "Surface not ready")
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting hardware probe")
                hardwareProbe.dumpCapabilities()
                
                Log.d(TAG, "Validating pipeline")
                PipelineValidator.logPipelineArchitecture()
                val pipelineValid = PipelineValidator.validatePipelineComponents()
                
                if (!pipelineValid) {
                    Log.e(TAG, "Pipeline validation failed")
                    return@launch
                }
                
                Log.d(TAG, "Starting camera engine")
                
                lifecycleScope.launch {
                    cameraEngine.cameraState.collect { state ->
                        Log.d(TAG, "Camera state: $state")
                        
                        if (state is CameraState.Open) {
                            Log.d(TAG, "Camera opened, starting preview")
                            cameraEngine.startPreview(surface, 
                                onAnalysis = { csvData ->
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastHistogramUpdate > 33) {
                                        histogramDataCsv = csvData
                                        lastHistogramUpdate = currentTime
                                    }
                                }
                            )
                        }
                    }
                }
                
                cameraEngine.openCamera("0")
                
                lifecycleScope.launch {
                    delay(3000)
                    
                    val ultraWideCameraId = "2"
                    if (cameraEngine.isCameraAvailable(ultraWideCameraId)) {
                        Log.d(TAG, "Switching to ultra-wide camera")
                        cameraEngine.switchCamera(ultraWideCameraId, surface,
                            onAnalysis = { csvData ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastHistogramUpdate > 33) {
                                    histogramDataCsv = csvData
                                    lastHistogramUpdate = currentTime
                                }
                            }
                        )
                    } else {
                        Log.d(TAG, "Ultra-wide camera (ID: $ultraWideCameraId) not available on this device. Available cameras: ${cameraEngine.getAvailableCameraIds().joinToString()}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera engine", e)
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
}