package id.xms.ecucamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
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
                            cameraEngine.startPreview(surface)
                        }
                    }
                }
                
                cameraEngine.openCamera("0")
                
                lifecycleScope.launch {
                    delay(3000)
                    Log.d(TAG, "TEST: Switching to Ultra-Wide (ID 2)")
                    cameraEngine.switchCamera("2", surface)
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