package id.xms.ecucamera.engine.pipeline

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SessionManager {
    
    companion object {
        private const val TAG = "ECU_PIPELINE"
    }
    
    suspend fun createSession(
        device: CameraDevice, 
        targets: List<Surface>,
        backgroundHandler: Handler
    ): CameraCaptureSession = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "🔧 Creating capture session with ${targets.size} surfaces")
        
        suspendCancellableCoroutine { continuation ->
            try {
                // Create output configurations for each surface
                val outputConfigurations = targets.map { surface ->
                    OutputConfiguration(surface)
                }
                
                // Session state callback
                val sessionCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "ECU_PIPELINE: Session Created with ${targets.size} surfaces")
                        continuation.resume(session)
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        val error = Exception("Session configuration failed")
                        Log.e(TAG, "ECU_PIPELINE: Session creation failed", error)
                        continuation.resumeWithException(error)
                    }
                    
                    override fun onClosed(session: CameraCaptureSession) {
                        Log.d(TAG, "ECU_PIPELINE: Session closed")
                    }
                }
                
                // Use SessionConfiguration for Android 11+ approach
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigurations,
                    { command -> backgroundHandler.post(command) }, // Executor
                    sessionCallback
                )
                
                // Create the session
                device.createCaptureSession(sessionConfig)
                
                // Handle cancellation
                continuation.invokeOnCancellation {
                    Log.w(TAG, "Session creation cancelled")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create capture session", e)
                continuation.resumeWithException(e)
            }
        }
    }
}