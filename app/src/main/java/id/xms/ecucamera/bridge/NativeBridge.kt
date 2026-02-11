package id.xms.ecucamera.bridge

import android.util.Log
import java.nio.ByteBuffer

/**
 * Native bridge for ECU Camera engine communication.
 * Provides interface between Kotlin and native Rust/C++ engine.
 */
object NativeBridge {
    private const val TAG = "NativeBridge"
    
    init {
        try {
            System.loadLibrary("ecucamera_engine")
            System.loadLibrary("ecu-bridge")
            Log.i(TAG, "Native libraries loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native libraries", e)
            throw RuntimeException("Failed to load native libraries", e)
        }
    }
    
    external fun stringFromRust(): String
    external fun getEngineStatus(): String
    external fun initializeEngine(): String
    external fun getCppBridgeInfo(): String
    
    fun isNativeBridgeReady(): Boolean {
        return try {
            val testResult = stringFromRust()
            testResult.contains("Rust V8 Connected")
        } catch (e: Exception) {
            Log.e(TAG, "Native bridge test failed", e)
            false
        }
    }
    
    /**
     * Analyze frame data using Rust image processor with zero-copy direct buffer
     * @param buffer Direct ByteBuffer containing YUV frame data (Y-plane)
     * @param length Buffer length
     * @param width Frame width
     * @param height Frame height
     * @param stride Row stride (bytes per row including padding)
     * @return CSV histogram data
     */
    external fun analyzeFrame(buffer: ByteBuffer, length: Int, width: Int, height: Int, stride: Int): String
    
    /**
     * Analyze frame for focus peaking using Rust edge detection
     * @param buffer Direct ByteBuffer containing YUV frame data (Y-plane)
     * @param length Buffer length
     * @param width Frame width
     * @param height Frame height
     * @param stride Row stride (bytes per row including padding)
     * @return CSV string of in-focus block indices
     */
    external fun analyzeFocusPeaking(buffer: ByteBuffer, length: Int, width: Int, height: Int, stride: Int): String
}
