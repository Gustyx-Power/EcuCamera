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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.controller.FlashController
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
import id.xms.ecucamera.ui.components.HistogramGraph
import id.xms.ecucamera.ui.components.PeakingOverlay
import id.xms.ecucamera.ui.screens.viewfinder.ViewfinderScreen
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
    
    private var isManualMode by mutableStateOf(false)
    private var isoSliderValue by mutableStateOf(0.0f)
    private var shutterSliderValue by mutableStateOf(0.5f)
    
    private var isManualFocusMode by mutableStateOf(false)
    private var focusSliderValue by mutableStateOf(0.0f)
    private var focusBlocks by mutableStateOf(listOf<Int>())
    
    private var currentZoom by mutableStateOf(1.0f)
    private var scaleGestureDetector: ScaleGestureDetector? = null
    
    private var currentFlashMode by mutableStateOf(FlashController.FLASH_OFF)
    
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
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ViewfinderScreen(
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
                        }
                    )
                    
                    HistogramGraph(
                        dataCsv = histogramDataCsv,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 60.dp, end = 16.dp)
                    )
                    
                    if (isManualFocusMode) {
                        PeakingOverlay(
                            focusBlocks = focusBlocks,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            cameraEngine.cycleFlash()
                            currentFlashMode = cameraEngine.getFlashController().getCurrentFlashMode()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (currentFlashMode == FlashController.FLASH_OFF) {
                                Icons.Filled.FlashOff
                            } else {
                                Icons.Filled.FlashOn
                            },
                            contentDescription = if (currentFlashMode == FlashController.FLASH_OFF) {
                                "Flash Off"
                            } else {
                                "Torch On"
                            },
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    if (currentZoom > 1.0f) {
                        Text(
                            text = "${String.format("%.1f", currentZoom)}x",
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isManualMode) "Manual Exp" else "Auto Exp",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isManualMode,
                                onCheckedChange = { enabled ->
                                    isManualMode = enabled
                                    cameraEngine.setManualMode(enabled)
                                }
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isManualFocusMode) "Manual Focus" else "Auto Focus",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isManualFocusMode,
                                onCheckedChange = { enabled ->
                                    isManualFocusMode = enabled
                                    cameraEngine.setManualFocusMode(enabled)
                                }
                            )
                        }
                        
                        if (isManualMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "ISO",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Slider(
                                value = isoSliderValue,
                                onValueChange = { value ->
                                    isoSliderValue = value
                                    cameraEngine.updateISO(value)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Shutter Speed",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Slider(
                                value = shutterSliderValue,
                                onValueChange = { value ->
                                    shutterSliderValue = value
                                    cameraEngine.updateShutter(value)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        if (isManualFocusMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Focus Distance",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Slider(
                                value = focusSliderValue,
                                onValueChange = { value ->
                                    focusSliderValue = value
                                    cameraEngine.updateFocus(value)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    Button(
                        onClick = {
                            cameraEngine.takePicture()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .size(80.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {}
                }
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
                                },
                                onFocusPeaking = { csvData ->
                                    try {
                                        if (csvData.isNotEmpty()) {
                                            focusBlocks = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                        } else {
                                            focusBlocks = listOf()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to parse focus peaking data", e)
                                        focusBlocks = listOf()
                                    }
                                }
                            )
                        }
                    }
                }
                
                cameraEngine.openCamera("0")
                
                lifecycleScope.launch {
                    delay(3000)
                    Log.d(TAG, "Switching to ultra-wide camera")
                    cameraEngine.switchCamera("2", surface,
                        onAnalysis = { csvData ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastHistogramUpdate > 33) {
                                histogramDataCsv = csvData
                                lastHistogramUpdate = currentTime
                            }
                        },
                        onFocusPeaking = { csvData ->
                            try {
                                if (csvData.isNotEmpty()) {
                                    focusBlocks = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                } else {
                                    focusBlocks = listOf()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse focus peaking data", e)
                                focusBlocks = listOf()
                            }
                        }
                    )
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
