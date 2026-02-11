package id.xms.ecucamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.xms.ecucamera.engine.core.CameraEngine
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.engine.probe.HardwareProbe
import id.xms.ecucamera.engine.pipeline.PipelineValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ECU_MAIN"
    }
    
    lateinit var cameraEngine: CameraEngine // Made public for Compose access
    private lateinit var hardwareProbe: HardwareProbe
    
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    EngineStatusScreen()
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
                
                // Observe camera state changes
                lifecycleScope.launch {
                    cameraEngine.cameraState.collect { state ->
                        Log.d(TAG, "Camera state changed to: $state")
                        
                        // Auto-start preview when camera is opened
                        if (state is CameraState.Open) {
                            Log.d(TAG, "Camera opened, starting preview...")
                            cameraEngine.startPreview()
                        }
                    }
                }
                
                // Open the back camera
                cameraEngine.openCamera("0")
                
                lifecycleScope.launch {
                    delay(3000)
                    Log.d(TAG, "TEST: Switching to Ultra-Wide (ID 2)")
                    cameraEngine.switchCamera("2")
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

@Composable
fun EngineStatusScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    
    val cameraState by activity.cameraEngine.cameraState.collectAsState()
    var permissionStatus by remember { mutableStateOf("Checking...") }
    
    // Update permission status
    LaunchedEffect(Unit) {
        permissionStatus = if (ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "EcuCamera",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Phase 5: Lens Manager & Zoom Logic",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Engine Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                Text(
                    text = "Check Logcat for Engine Status...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Green
                )
                
                Text(
                    text = "Permission: $permissionStatus",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Yellow
                )
                
                Text(
                    text = "Camera State: $cameraState",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = when (cameraState) {
                        is CameraState.Closed -> Color.Gray
                        is CameraState.Opening -> Color.Yellow
                        is CameraState.Open -> Color.Green
                        is CameraState.Configured -> Color.Cyan
                        is CameraState.Error -> Color.Red
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Hardware Probe: Scanning device capabilities...\n" +
                  "Camera Engine: Attempting to connect to back camera...\n" +
                  "Session Manager: Creating capture sessions...\n" +
                  "Request Manager: Building capture requests...\n" +
                  "State Management: Monitoring camera lifecycle...\n\n" +
                  "All detailed logs are in Logcat with tag 'ECU_*'",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4
        )
    }
}