package id.xms.ecucamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
import id.xms.ecucamera.ui.components.HistogramView
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
    
    // Histogram data state
    private var histogramData by mutableStateOf(listOf<Int>())
    
    // Manual exposure control state
    private var isManualMode by mutableStateOf(false)
    private var isoSliderValue by mutableStateOf(0.0f)
    private var shutterSliderValue by mutableStateOf(0.5f)
    
    // Manual focus control state
    private var isManualFocusMode by mutableStateOf(false)
    private var focusSliderValue by mutableStateOf(0.0f)
    private var focusBlocks by mutableStateOf(listOf<Int>())
    
    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Camera permission GRANTED")
            startCameraEngine()
        } else {
            Log.e(TAG, "Camera permission DENIED")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "EcuCamera Phase 5: Lens Manager & Zoom Logic - Starting")
        
        // Initialize engine components
        cameraEngine = CameraEngine(this)
        hardwareProbe = HardwareProbe(this)
        
        // Request camera permission immediately
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
                        }
                    )
                    
                    // Overlay histogram view
                    HistogramView(
                        data = histogramData,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Focus peaking overlay (visible only in manual focus mode)
                    if (isManualFocusMode) {
                        PeakingOverlay(
                            focusBlocks = focusBlocks,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Manual exposure controls overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        // Auto/Manual Exposure toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                        
                        // Auto/Manual Focus toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                        
                        // Manual exposure controls (visible only in manual mode)
                        if (isManualMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // ISO Slider
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
                            
                            // Shutter Speed Slider
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
                        
                        // Manual focus controls (visible only in manual focus mode)
                        if (isManualFocusMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Focus Slider
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
                }
            }
        }
    }
    
    private fun checkAndRequestCameraPermission() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
                startCameraEngine()
            }
            else -> {
                Log.d(TAG, "Requesting camera permission...")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCameraEngine() {
        val surface = currentSurface
        if (surface == null) {
            Log.w(TAG, "Surface not ready, skipping camera start")
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting hardware probe...")
                hardwareProbe.dumpCapabilities()
                
                Log.d(TAG, "Validating pipeline components...")
                PipelineValidator.logPipelineArchitecture()
                val pipelineValid = PipelineValidator.validatePipelineComponents()
                
                if (!pipelineValid) {
                    Log.e(TAG, "Pipeline validation failed - aborting camera start")
                    return@launch
                }
                
                Log.d(TAG, "Starting camera engine...")
                
                lifecycleScope.launch {
                    cameraEngine.cameraState.collect { state ->
                        Log.d(TAG, "Camera state changed to: $state")
                        
                        if (state is CameraState.Open) {
                            Log.d(TAG, "Camera opened, starting preview...")
                            cameraEngine.startPreview(surface, 
                                onAnalysis = { csvData ->
                                    // Parse CSV histogram data and update UI
                                    try {
                                        val histogramList = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                        if (histogramList.size == 256) {
                                            histogramData = histogramList
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to parse histogram data: $csvData", e)
                                    }
                                },
                                onFocusPeaking = { csvData ->
                                    // Parse CSV focus peaking data and update UI
                                    try {
                                        if (csvData.isNotEmpty()) {
                                            val blockList = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                            focusBlocks = blockList
                                        } else {
                                            focusBlocks = listOf()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to parse focus peaking data: $csvData", e)
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
                    Log.d(TAG, "TEST: Switching to Ultra-Wide (ID 2)")
                    cameraEngine.switchCamera("2", surface,
                        onAnalysis = { csvData ->
                            // Parse CSV histogram data and update UI
                            try {
                                val histogramList = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                if (histogramList.size == 256) {
                                    histogramData = histogramList
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse histogram data: $csvData", e)
                            }
                        },
                        onFocusPeaking = { csvData ->
                            // Parse CSV focus peaking data and update UI
                            try {
                                if (csvData.isNotEmpty()) {
                                    val blockList = csvData.split(",").mapNotNull { it.toIntOrNull() }
                                    focusBlocks = blockList
                                } else {
                                    focusBlocks = listOf()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse focus peaking data: $csvData", e)
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
}