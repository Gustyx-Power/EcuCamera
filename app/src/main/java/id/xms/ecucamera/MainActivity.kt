package id.xms.ecucamera

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.xms.ecucamera.bridge.NativeBridge

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Test the native bridge immediately
        testNativeBridge()
        
        setContent {
            EcuCameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EcuCameraMainScreen()
                }
            }
        }
    }
    
    private fun testNativeBridge() {
        try {
            Log.i(TAG, "Testing native bridge connection...")
            
            if (NativeBridge.isNativeBridgeReady()) {
                val rustMessage = NativeBridge.stringFromRust()
                val engineStatus = NativeBridge.getEngineStatus()
                val initResult = NativeBridge.initializeEngine()
                
                Log.i(TAG, "✅ Native Bridge Test Results:")
                Log.i(TAG, "   Rust Message: $rustMessage")
                Log.i(TAG, "   Engine Status: $engineStatus")
                Log.i(TAG, "   Init Result: $initResult")
            } else {
                Log.e(TAG, "❌ Native bridge is not ready!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Native bridge test failed", e)
        }
    }
}

@Composable
fun EcuCameraMainScreen() {
    var bridgeStatus by remember { mutableStateOf("Testing...") }
    var rustMessage by remember { mutableStateOf("") }
    var engineStatus by remember { mutableStateOf("") }
    var initResult by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        try {
            if (NativeBridge.isNativeBridgeReady()) {
                bridgeStatus = "✅ Bridge Connected"
                rustMessage = NativeBridge.stringFromRust()
                engineStatus = NativeBridge.getEngineStatus()
                initResult = NativeBridge.initializeEngine()
            } else {
                bridgeStatus = "❌ Bridge Failed"
            }
        } catch (e: Exception) {
            bridgeStatus = "❌ Error: ${e.message}"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "EcuCamera",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Phase 2: The Nervous System",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Divider()
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Native Bridge Status",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = bridgeStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )
                
                if (rustMessage.isNotEmpty()) {
                    Text(
                        text = "Rust Engine: $rustMessage",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                if (engineStatus.isNotEmpty()) {
                    Text(
                        text = "Status: $engineStatus",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                if (initResult.isNotEmpty()) {
                    Text(
                        text = "Init: $initResult",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        
        Button(
            onClick = {
                // Refresh bridge status
                try {
                    if (NativeBridge.isNativeBridgeReady()) {
                        bridgeStatus = "✅ Bridge Connected (Refreshed)"
                        rustMessage = NativeBridge.stringFromRust()
                        engineStatus = NativeBridge.getEngineStatus()
                    }
                } catch (e: Exception) {
                    bridgeStatus = "❌ Refresh Error: ${e.message}"
                }
            }
        ) {
            Text("Refresh Bridge Status")
        }
    }
}

@Composable
fun EcuCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun EcuCameraMainScreenPreview() {
    EcuCameraTheme {
        EcuCameraMainScreen()
    }
}